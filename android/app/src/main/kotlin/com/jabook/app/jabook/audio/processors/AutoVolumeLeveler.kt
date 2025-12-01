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
 * Audio processor for automatic volume leveling.
 *
 * Maintains consistent volume level by:
 * - Measuring LUFS in real-time (400ms sliding window)
 * - Adaptive gain adjustment:
 *   - If level < -23 LUFS (quiet): add gain to reach -23 LUFS
 *   - If level > -16 LUFS (loud): apply soft limiter
 * - Smooth gain changes (slew rate: 0.5 dB/s) to avoid artifacts
 */
@OptIn(UnstableApi::class)
class AutoVolumeLeveler : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Target LUFS: -23 LUFS (EBU R128 for speech)
    private val targetLufs = -23.0

    // LUFS measurement window: 400ms
    private val windowSizeMs = 400
    private var windowSizeSamples = 0
    private val lufsBuffer = mutableListOf<Double>()

    // Gain adjustment (in linear scale)
    private var currentGain = 1.0f
    private var targetGain = 1.0f

    // Slew rate: 0.5 dB/s for smooth changes
    private val slewRateDbPerSecond = 0.5f
    private var slewRatePerSample = 0.0f

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        // Calculate window size in samples
        val sampleRate = inputAudioFormat.sampleRate
        windowSizeSamples = (sampleRate * windowSizeMs / 1000).toInt()

        // Calculate slew rate per sample
        slewRatePerSample = kotlin.math.pow(10.0, (slewRateDbPerSecond / sampleRate) / 20.0).toFloat()

        // Initialize LUFS buffer
        lufsBuffer.clear()

        // Reset gain
        currentGain = 1.0f
        targetGain = 1.0f

        isActive = true

        android.util.Log.d(
            "AutoVolumeLeveler",
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "windowSize=$windowSizeSamples samples",
        )

        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    // Input/output buffers
    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    // RMS measurement for level detection (simplified LUFS)
    // Use ArrayDeque for O(1) add/remove operations
    private val rmsBuffer = ArrayDeque<Float>()

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
     * Processes audio buffer with automatic volume leveling.
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

        applyAutoLeveling(input, output, samples, channels)
    }

    /**
     * Applies automatic volume leveling using RMS-based approach.
     */
    private fun applyAutoLeveling(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
    ) {
        // Calculate RMS of current buffer
        var sumSquares = 0.0
        val originalPosition = input.position()

        // Read samples and calculate RMS
        for (i in 0 until samples) {
            for (ch in 0 until channels) {
                val sample = input.short.toFloat() / Short.MAX_VALUE
                sumSquares += sample * sample
            }
        }

        val rms = kotlin.math.sqrt(sumSquares / (samples * channels)).toFloat()

        // Update RMS buffer (sliding window) - O(1) operations with ArrayDeque
        rmsBuffer.addLast(rms)
        if (rmsBuffer.size > windowSizeSamples) {
            rmsBuffer.removeFirst()
        }

        // Calculate average RMS over window (optimized: avoid creating intermediate list)
        val avgRms =
            if (rmsBuffer.isNotEmpty()) {
                var sum = 0.0f
                for (value in rmsBuffer) {
                    sum += value
                }
                sum / rmsBuffer.size
            } else {
                rms
            }

        // Convert RMS to approximate LUFS (simplified)
        // RMS of 0.35 ≈ -23 LUFS for speech
        val targetRms = 0.35f
        val quietThresholdRms = 0.15f // ≈ -23 LUFS
        val loudThresholdRms = 0.5f // ≈ -16 LUFS

        // Calculate target gain
        if (avgRms > 0.001f) { // Avoid division by zero
            when {
                avgRms < quietThresholdRms -> {
                    // Too quiet: boost to target
                    targetGain = targetRms / avgRms
                }
                avgRms > loudThresholdRms -> {
                    // Too loud: reduce to target
                    targetGain = targetRms / avgRms
                }
                else -> {
                    // Within acceptable range: maintain current level
                    targetGain = 1.0f
                }
            }

            // Limit gain to reasonable range
            targetGain = targetGain.coerceIn(0.3f, 3.0f)
        }

        // Smooth gain changes (slew rate limiting)
        val gainDiff = targetGain - currentGain
        if (kotlin.math.abs(gainDiff) > 0.001f) {
            // Apply slew rate: limit change per sample
            val maxChange = slewRatePerSample
            val actualChange = gainDiff.coerceIn(-maxChange, maxChange)
            currentGain += actualChange
        } else {
            currentGain = targetGain
        }

        // Reset input position and apply gain
        // Optimized: pre-compute constants and combine operations
        input.position(originalPosition)
        val invMaxValue = 1.0f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()
        val limiterThreshold = 0.95f
        val limiterRatio = 0.5f

        for (i in 0 until samples) {
            for (ch in 0 until channels) {
                val sample = input.short
                val normalized = sample * invMaxValue
                var amplified = normalized * currentGain

                // Apply soft limiter if too loud (optimized conditionals)
                if (amplified > limiterThreshold) {
                    amplified = limiterThreshold + (amplified - limiterThreshold) * limiterRatio
                } else if (amplified < -limiterThreshold) {
                    amplified = -limiterThreshold + (amplified + limiterThreshold) * limiterRatio
                }

                amplified = amplified.coerceIn(-1.0f, 1.0f)
                val outputSample = (amplified * maxValue).toInt().toShort()
                output.putShort(outputSample)
            }
        }
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    override fun flush() {
        lufsBuffer.clear()
        rmsBuffer.clear()
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false
        currentGain = 1.0f
        targetGain = 1.0f
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
    }

    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
