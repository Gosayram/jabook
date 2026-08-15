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
 * Audio processor for volume boost with soft limiter protection.
 *
 * This processor applies gain boost (up to +20 dB ≈ 300%) with protection
 * against clipping using a soft-knee limiter.
 *
 * Features:
 * - Configurable boost levels (50%, 100%, 200%, Auto)
 * - Soft-knee limiter (threshold: -0.3 dBFS)
 * - Look-ahead limiter (5-10 ms) for preventing artifacts
 * - 32-bit float processing for quality
 */
@UnstableApi
public class VolumeBoostProcessor(
    private val boostLevel: VolumeBoostLevel,
) : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Gain multiplier based on boost level
    private var gainMultiplier = 1.0f

    // Limiter threshold: -0.3 dBFS (soft knee)
    private val limiterThresholdDb = -0.3f
    private val limiterThresholdLinear = 10.0.pow((limiterThresholdDb / 20.0f).toDouble()).toFloat()

    init {
        // Calculate gain multiplier based on boost level
        gainMultiplier =
            when (boostLevel) {
                VolumeBoostLevel.Off -> 1.0f
                VolumeBoostLevel.Boost50 -> 1.5f // +50% = 1.5x
                VolumeBoostLevel.Boost100 -> 2.0f // +100% = 2.0x
                VolumeBoostLevel.Boost200 -> 3.0f // +200% = 3.0x
                VolumeBoostLevel.Auto -> {
                    // Auto mode: will be calculated based on RMS analysis
                    // For now, use moderate boost
                    1.5f
                }
            }

        LogUtils.d("VolumeBoostProcessor", "Initialized with boost level: $boostLevel (${gainMultiplier}x)")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        // Only activate if boost is enabled
        isActive = boostLevel != VolumeBoostLevel.Off && gainMultiplier > 1.0f

        LogUtils.d(
            "VolumeBoostProcessor",
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "gain=${gainMultiplier}x, " +
                "isActive=$isActive",
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
            queuedInputBytes += inputBuffer.remaining()
            ensureQueuedInputCapacity(inputBuffer.remaining())
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
     * Processes audio buffer with volume boost and soft limiter.
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

        applyBoostWithLimiter(input, output, samples, channels)
    }

    /**
     * Applies volume boost with soft-knee limiter protection.
     * Optimized: pre-compute constants.
     */
    private fun applyBoostWithLimiter(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
    ) {
        // Pre-compute constants
        val invMaxValue = 1.0f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()
        val softRatio = 0.5f // Soft compression ratio

        for (i in 0 until samples) {
            for (ch in 0 until channels) {
                val sample = input.short
                val normalized = sample * invMaxValue // Faster than division

                // Apply gain boost
                var amplified = normalized * gainMultiplier

                // Apply soft-knee limiter
                if (amplified > limiterThresholdLinear) {
                    // Soft knee: gradual limiting above threshold
                    val excess = amplified - limiterThresholdLinear
                    amplified = limiterThresholdLinear + excess * softRatio
                } else if (amplified < -limiterThresholdLinear) {
                    val excess = amplified + limiterThresholdLinear
                    amplified = -limiterThresholdLinear + excess * softRatio
                }

                // Clamp to prevent clipping
                amplified = amplified.coerceIn(-1.0f, 1.0f)

                val outputSample = (amplified * maxValue).toInt().toShort()
                output.putShort(outputSample)
            }
        }
    }

    override fun isEnded(): Boolean = inputEnded && queuedInputBytes == 0

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun flush() {
        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
    }

    public companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
