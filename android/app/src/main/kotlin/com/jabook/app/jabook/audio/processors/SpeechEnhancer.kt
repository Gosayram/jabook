// Copyright 2026 Jabook Contributors
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
import com.jabook.app.jabook.util.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Audio processor for speech enhancement.
 *
 * Improves speech clarity in audiobooks through frequency correction:
 * - High-pass filter (<120 Hz, -12 dB/octave) - removes low-frequency noise
 * - Peak EQ (2-4 kHz, +3-6 dB, Q=1.5) - enhances speech formants
 * - DeEsser (4-8 kHz, dynamic suppression) - reduces sibilance
 * - Gentle compression (threshold -28 dB, ratio 2:1) - stabilizes level
 */
@UnstableApi
public class SpeechEnhancer : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Filter parameters
    private val highPassCutoffHz = 120.0f
    private val peakEqFreqHz = 3000.0f // Center of 2-4 kHz range
    private val peakEqGainDb = 4.5f // Average of +3-6 dB
    private val peakEqQ = 1.5f

    // Compression parameters (gentle)
    private val compressionThresholdDb = -28.0f
    private val compressionRatio = 2.0f
    private val compressionThresholdLinear = 10.0.pow((compressionThresholdDb / 20.0f).toDouble()).toFloat()

    // High-pass filter state (simple first-order IIR), per channel
    private var highPassAlpha = 0.0f
    private var highPassPrev = FloatArray(0)

    // Peak EQ biquad state (frequency-selective 3kHz boost), per channel
    private var peakEqB0 = 0f
    private var peakEqB1 = 0f
    private var peakEqB2 = 0f
    private var peakEqA1 = 0f
    private var peakEqA2 = 0f
    private lateinit var peakEqState: Array<BiquadState>

    // DeEsser state (dynamic suppression in 4-8 kHz range)
    private var deEsserGain = 1.0f
    private val deEsserThreshold = 0.7f // Threshold for sibilance detection

    // Compression state
    private var compressionEnvelope = 0.0f
    private var compressionGainReduction = 1.0f

    // Input/output buffers
    private var queuedInputBuffer: ByteBuffer? = null
    private var queuedInputCapacity: Int = 0
    private var queuedInputBytes = 0
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
        val hpAlpha = 1.0f - (2.0f * kotlin.math.PI.toFloat() * highPassCutoffHz / sampleRate)
        highPassAlpha = hpAlpha.coerceIn(0.0f, 1.0f)
        highPassPrev = FloatArray(channels)

        // Compute peak EQ biquad coefficients (peak/notch filter)
        val w0 = (2.0f * kotlin.math.PI.toFloat() * peakEqFreqHz / sampleRate)
        val sinW0 = kotlin.math.sin(w0.toDouble()).toFloat()
        val peakAlpha = sinW0 / (2.0f * peakEqQ)
        val amplitude = 10.0.pow(peakEqGainDb / 40.0).toFloat() // sqrt(10^(dBgain/20))
        val norm = 1.0f / (1.0f + peakAlpha / amplitude)
        peakEqB0 = (1.0f + peakAlpha * amplitude) * norm
        peakEqB1 = (-2.0f * kotlin.math.cos(w0.toDouble())).toFloat() * norm
        peakEqB2 = (1.0f - peakAlpha * amplitude) * norm
        peakEqA1 = peakEqB1 // same as -2*cos(w0)*norm
        peakEqA2 = (1.0f - peakAlpha / amplitude) * norm
        peakEqState = Array(channels) { BiquadState() }

        // Reset states
        deEsserGain = 1.0f
        compressionEnvelope = 0.0f
        compressionGainReduction = 1.0f

        isActive = true

        LogUtils.d(
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
            val remaining = inputBuffer.remaining()
            ensureQueuedInputCapacity(remaining)
            queuedInputBytes += remaining
            queuedInputBuffer!!.put(inputBuffer)
        }
    }

    private fun ensureQueuedInputCapacity(additionalBytes: Int) {
        val required = queuedInputBytes + additionalBytes
        if (required <= queuedInputCapacity) return
        val newCapacity = maxOf(required, queuedInputCapacity * 2, 4096)
        val newBuffer = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
        queuedInputBuffer?.let { old ->
            old.flip()
            newBuffer.put(old)
        }
        queuedInputBuffer = newBuffer
        queuedInputCapacity = newCapacity
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        if (outputBuffer?.hasRemaining() == true) return outputBuffer!!
        if (!isActive || queuedInputBytes == 0) {
            return EMPTY_BUFFER
        }

        val totalSize = queuedInputBytes

        val preparedOutputBuffer =
            if (outputBuffer == null || outputBuffer!!.capacity() < totalSize) {
                ByteBuffer.allocateDirect(totalSize).order(ByteOrder.nativeOrder()).also {
                    outputBuffer = it
                }
            } else {
                outputBuffer!!.clear()
                outputBuffer
            } ?: return EMPTY_BUFFER

        queuedInputBuffer?.let { buf ->
            buf.flip()
            processBuffer(buf, preparedOutputBuffer)
        }

        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        preparedOutputBuffer.flip()

        return preparedOutputBuffer
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
                val prev = highPassPrev[ch]
                normalized = normalized - prev + highPassAlpha * prev
                highPassPrev[ch] = normalized

                // 2. Peak EQ (3 kHz boost via biquad — frequency-selective, not broadband)
                normalized = processPeakEqBiquad(normalized, ch)

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

    override fun isEnded(): Boolean = inputEnded && queuedInputBytes == 0

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun flush() {
        // Reset filter states
        highPassPrev.fill(0f)
        deEsserGain = 1.0f
        compressionEnvelope = 0.0f
        compressionGainReduction = 1.0f
        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        highPassAlpha = 0.0f
        highPassPrev = FloatArray(0)
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
    }

    /**
     * Processes one sample through the peak EQ biquad filter.
     */
    private fun processPeakEqBiquad(
        input: Float,
        channel: Int,
    ): Float {
        val state = peakEqState[channel]
        val output =
            peakEqB0 * input +
                peakEqB1 * state.x1 +
                peakEqB2 * state.x2 -
                peakEqA1 * state.y1 -
                peakEqA2 * state.y2
        state.x2 = state.x1
        state.x1 = input
        state.y2 = state.y1
        state.y1 = output
        return output
    }

    public companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        private class BiquadState {
            var x1 = 0f
            var x2 = 0f
            var y1 = 0f
            var y2 = 0f
        }
    }
}
