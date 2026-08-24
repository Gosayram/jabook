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
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.util.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.pow

/**
 * Audio processor for automatic volume leveling.
 *
 * Maintains consistent volume level by:
 * - Measuring LUFS in real-time (400ms sliding window)
 * - Adaptive gain adjustment:
 *   - If level < [AUDIOBOOK_TARGET_LUFS] LUFS (quiet): add gain to reach target
 *   - If level > loud threshold: apply soft limiter
 * - Smooth gain changes (slew rate: 0.5 dB/s) to avoid artifacts
 */
@UnstableApi
public class AutoVolumeLeveler : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Target LUFS: audiobook-optimized target (-16 LUFS, vs -14 for music)
    private val targetLufs = AUDIOBOOK_TARGET_LUFS

    // LUFS measurement window: 400ms of audio time
    private val windowSizeMs = 400
    private var windowSizeSamples = 0

    // Frame-weighted RMS sliding window: 400ms of audio regardless of buffer size.
    private data class RmsWindowEntry(
        val rms: Float,
        val frames: Int,
    )

    private val rmsBuffer = ArrayDeque<RmsWindowEntry>()
    private var rmsWeightedSum = 0.0f
    private var rmsWindowFrames = 0

    // Gain adjustment (in linear scale)
    private var currentGain = 1.0f
    private var targetGain = 1.0f

    // Slew rate: 0.5 dB/s for smooth changes
    private val slewRateDbPerSecond = 0.5f

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        // Calculate window size in samples
        val sampleRate = inputAudioFormat.sampleRate
        windowSizeSamples = (sampleRate * windowSizeMs / 1000).coerceAtLeast(1)

        // Initialize RMS sliding window
        rmsBuffer.clear()
        rmsWeightedSum = 0.0f
        rmsWindowFrames = 0

        // Reset gain
        currentGain = 1.0f
        targetGain = 1.0f

        isActive = true

        LogUtils.d(
            "AutoVolumeLeveler",
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "windowSize=$windowSizeSamples samples",
        )

        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    // Input/output buffers
    private var queuedInputBuffer: ByteBuffer? = null
    private var queuedInputCapacity: Int = 0
    private var queuedInputBytes = 0
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

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

        val rms =
            if (samples > 0) {
                kotlin.math.sqrt(sumSquares / (samples * channels)).toFloat()
            } else {
                0.0f
            }

        // Update RMS sliding window using frame-weighted running average.
        // This keeps the intended 400ms horizon independent of input buffer size.
        val entryFrames = samples.coerceAtLeast(1)
        rmsBuffer.addLast(RmsWindowEntry(rms = rms, frames = entryFrames))
        rmsWeightedSum += rms * entryFrames
        rmsWindowFrames += entryFrames

        while (rmsWindowFrames > windowSizeSamples && rmsBuffer.isNotEmpty()) {
            val oldest = rmsBuffer.removeFirst()
            rmsWeightedSum -= oldest.rms * oldest.frames
            rmsWindowFrames -= oldest.frames
        }

        // Average RMS over the window in O(1)
        val avgRms =
            if (rmsWindowFrames > 0) {
                rmsWeightedSum / rmsWindowFrames
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

        // Smooth gain changes (slew rate limiting in dB domain, scaled by buffer duration)
        val gainDiff = targetGain - currentGain
        if (kotlin.math.abs(gainDiff) > 0.001f) {
            val sampleRate = inputAudioFormat!!.sampleRate.toFloat()
            val maxStepDb = slewRateDbPerSecond * (samples / sampleRate)
            val currentDb = 20f * log10(currentGain)
            val targetDb = 20f * log10(targetGain)
            val newDb = currentDb + (targetDb - currentDb).coerceIn(-maxStepDb, maxStepDb)
            currentGain = 10f.pow(newDb / 20f)
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

    override fun isEnded(): Boolean = inputEnded && queuedInputBytes == 0

    override fun flush(streamMetadata: StreamMetadata) {
        rmsBuffer.clear()
        rmsWeightedSum = 0.0f
        rmsWindowFrames = 0
        queuedInputBuffer?.clear()
        queuedInputBytes = 0
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

    public companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Audiobook-optimized loudness target. -16 LUFS is better suited for long-form speech than EBU R128 -23. */
        public const val AUDIOBOOK_TARGET_LUFS: Double = -16.0
    }
}
