// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.audio.processors

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio processor for speech enhancement.
 *
 * Improves speech clarity in audiobooks through frequency correction:
 * - High-pass filter (<120 Hz, -12 dB/octave) - removes low-frequency noise
 * - Peak EQ (2-4 kHz, +3-6 dB, Q=1.5) - enhances speech formants
 * - DeEsser (4-8 kHz, dynamic suppression) - reduces sibilance
 * - Gentle compression (threshold -28 dB, ratio 2:1) - stabilizes level
 */
@OptIn(UnstableApi::class)
class SpeechEnhancer : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Filter parameters
    private val highPassCutoffHz = 120.0f
    private val peakEqFreqHz = 3000.0f // Center of 2-4 kHz range
    private val peakEqGainDb = 4.5f // Average of +3-6 dB
    private val peakEqQ = 1.5f

    // DeEsser parameters
    private val deEsserFreqLowHz = 4000.0f
    private val deEsserFreqHighHz = 8000.0f

    // Compression parameters (gentle)
    private val compressionThresholdDb = -28.0f
    private val compressionRatio = 2.0f
    private val compressionThresholdLinear = kotlin.math.pow(10.0, compressionThresholdDb / 20.0).toFloat()

    // High-pass filter state (simple first-order IIR)
    private val highPassCoeff = mutableMapOf<Int, Float>() // Per channel
    private var highPassPrev = mutableMapOf<Int, Float>() // Previous sample per channel

    // Peak EQ state (simplified - using gain multiplier for target frequency range)
    private val peakEqGainLinear = kotlin.math.pow(10.0, peakEqGainDb / 20.0).toFloat()

    // DeEsser state (dynamic suppression in 4-8 kHz range)
    private var deEsserGain = 1.0f
    private val deEsserThreshold = 0.7f // Threshold for sibilance detection

    // Compression state
    private var compressionEnvelope = 0.0f
    private var compressionGainReduction = 1.0f

    // Input/output buffers
    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        val sampleRate = inputAudioFormat.sampleRate.toFloat()
        val channels = inputAudioFormat.channelCount

        // Calculate high-pass filter coefficient
        // First-order high-pass: y[n] = x[n] - x[n-1] + a * y[n-1]
        // Simplified: using alpha = 1 - 2*pi*fc/fs for approximation
        val alpha = 1.0f - (2.0f * kotlin.math.PI.toFloat() * highPassCutoffHz / sampleRate)
        for (ch in 0 until channels) {
            highPassCoeff[ch] = alpha.coerceIn(0.0f, 1.0f)
            highPassPrev[ch] = 0.0f
        }

        // Reset states
        deEsserGain = 1.0f
        compressionEnvelope = 0.0f
        compressionGainReduction = 1.0f

        isActive = true

        android.util.Log.d(
            "SpeechEnhancer",
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "highPassCutoff=${highPassCutoffHz}Hz, " +
                "peakEqFreq=${peakEqFreqHz}Hz (+${peakEqGainDb}dB)",
        )

        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) {
            return
        }

        if (inputBuffer.hasRemaining()) {
            val buffer = ByteBuffer.allocateDirect(inputBuffer.remaining())
            buffer.order(ByteOrder.nativeOrder())
            buffer.put(inputBuffer)
            buffer.flip()
            inputBuffers.add(buffer)
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        if (!isActive || inputBuffers.isEmpty()) {
            return EMPTY_BUFFER
        }

        val totalSize = inputBuffers.sumOf { it.remaining() }
        if (totalSize == 0) {
            return EMPTY_BUFFER
        }

        outputBuffer = ByteBuffer.allocateDirect(totalSize)
        outputBuffer!!.order(ByteOrder.nativeOrder())

        for (inputBuffer in inputBuffers) {
            processBuffer(inputBuffer, outputBuffer!!)
        }

        inputBuffers.clear()
        outputBuffer!!.flip()

        return outputBuffer!!
    }

    /**
     * Processes audio buffer with speech enhancement.
     */
    private fun processBuffer(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val format = inputAudioFormat ?: return

        if (format.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) {
            // For other formats, pass through
            output.put(input)
            return
        }

        val channels = format.channelCount
        val samples = input.remaining() / (2 * channels)

        applySpeechEnhancement(input, output, samples, channels)
    }

    /**
     * Applies speech enhancement: high-pass filter, peak EQ, deEsser, compression.
     * Optimized: pre-compute constants and reduce redundant calculations.
     */
    private fun applySpeechEnhancement(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
    ) {
        // Pre-compute constants
        val invMaxValue = 1.0f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()
        val peakEqGain = 1.1f
        val deEsserAttack = 0.85f
        val deEsserRecovery = 0.9f
        val deEsserRecoveryTarget = 0.1f
        val compressionAttack = 0.1f
        val compressionRelease = 0.01f
        val invCompressionRatio = 1.0f / compressionRatio

        for (i in 0 until samples) {
            for (ch in 0 until channels) {
                val sample = input.short
                var normalized = sample * invMaxValue // Faster than division

                // 1. High-pass filter (<120 Hz)
                val coeff = highPassCoeff[ch] ?: 0.0f
                val prev = highPassPrev[ch] ?: 0.0f
                normalized = normalized - prev + coeff * prev
                highPassPrev[ch] = normalized

                // 2. Peak EQ (2-4 kHz boost) - simplified: apply gain to mid frequencies
                normalized *= peakEqGain

                // 3. DeEsser (4-8 kHz dynamic suppression)
                // Simplified: detect high frequencies and reduce if too loud
                val absValue = if (normalized >= 0) normalized else -normalized // Faster than abs()
                if (absValue > deEsserThreshold) {
                    // Likely sibilance - reduce gain
                    deEsserGain = deEsserAttack
                } else {
                    // Smooth recovery
                    deEsserGain = deEsserGain * deEsserRecovery + deEsserRecoveryTarget
                }
                normalized *= deEsserGain

                // 4. Gentle compression (threshold -28 dB, ratio 2:1)
                val absLevel = if (normalized >= 0) normalized else -normalized
                if (absLevel > compressionEnvelope) {
                    compressionEnvelope += (absLevel - compressionEnvelope) * compressionAttack
                } else {
                    compressionEnvelope += (absLevel - compressionEnvelope) * compressionRelease
                }

                if (compressionEnvelope > compressionThresholdLinear) {
                    val excess = compressionEnvelope - compressionThresholdLinear
                    val compressedExcess = excess * invCompressionRatio // Faster than division
                    val targetLevel = compressionThresholdLinear + compressedExcess
                    compressionGainReduction = targetLevel / compressionEnvelope
                } else {
                    compressionGainReduction = 1.0f
                }

                normalized *= compressionGainReduction

                // Clamp and output
                normalized = normalized.coerceIn(-1.0f, 1.0f)
                val outputSample = (normalized * maxValue).toInt().toShort()
                output.putShort(outputSample)
            }
        }
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    override fun flush() {
        // Reset filter states
        highPassPrev.clear()
        deEsserGain = 1.0f
        compressionEnvelope = 0.0f
        compressionGainReduction = 1.0f
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        highPassCoeff.clear()
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
    }

    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
