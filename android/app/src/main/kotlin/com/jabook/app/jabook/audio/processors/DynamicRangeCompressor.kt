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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Audio processor for dynamic range compression.
 *
 * Makes quiet parts louder and loud parts softer for comfortable listening
 * of audiobooks.
 *
 * Compression parameters by level:
 * - Gentle: threshold -32 dB, ratio 2:1, attack 10 ms, release 80 ms
 * - Medium: threshold -24 dB, ratio 3:1, attack 5 ms, release 60 ms
 * - Strong: threshold -18 dB, ratio 4:1, attack 3 ms, release 40 ms
 */
@UnstableApi
public class DynamicRangeCompressor(
    private val drcLevel: DRCLevel,
) : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Compression parameters
    private var thresholdDb = -32.0f
    private var ratio = 2.0f
    private var attackMs = 10.0f
    private var releaseMs = 80.0f

    // Envelope follower state
    private var envelopeLevel = 0.0f
    private var gainReduction = 0.0f

    // Attack and release coefficients (calculated from time constants)
    private var attackCoeff = 0.0f
    private var releaseCoeff = 0.0f

    // Threshold in linear scale
    private var thresholdLinear = 0.0f

    // Makeup gain to compensate for level reduction
    private var makeupGain = 1.0f

    // Scratch frame buffer (sized in configure, reused per frame)
    private var channelSamples = ShortArray(0)

    // Input/output buffers
    private var queuedInputBuffer: ByteBuffer? = null
    private var queuedInputCapacity: Int = 0
    private var queuedInputBytes = 0
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    init {
        // Set compression parameters based on level
        when (drcLevel) {
            DRCLevel.Off -> {
                isActive = false
            }
            DRCLevel.Gentle -> {
                thresholdDb = -32.0f
                ratio = 2.0f
                attackMs = 10.0f
                releaseMs = 80.0f
            }
            DRCLevel.Medium -> {
                thresholdDb = -24.0f
                ratio = 3.0f
                attackMs = 5.0f
                releaseMs = 60.0f
            }
            DRCLevel.Strong -> {
                thresholdDb = -18.0f
                ratio = 4.0f
                attackMs = 3.0f
                releaseMs = 40.0f
            }
        }

        LogUtils.d(
            "DynamicRangeCompressor",
            "Initialized with DRC level: $drcLevel " +
                "(threshold=${thresholdDb}dB, ratio=$ratio:1, " +
                "attack=${attackMs}ms, release=${releaseMs}ms)",
        )
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        // Only activate if DRC is enabled
        isActive = drcLevel != DRCLevel.Off

        if (isActive) {
            val sampleRate = inputAudioFormat.sampleRate

            channelSamples = ShortArray(inputAudioFormat.channelCount)

            // Convert threshold from dB to linear
            thresholdLinear = 10.0.pow((thresholdDb / 20.0f).toDouble()).toFloat()

            // Calculate attack and release coefficients
            // Using exponential smoothing: coeff = 1 - exp(-1 / (time * sampleRate))
            attackCoeff = 1.0f - kotlin.math.exp(-1.0f / (attackMs * sampleRate / 1000.0f))
            releaseCoeff = 1.0f - kotlin.math.exp(-1.0f / (releaseMs * sampleRate / 1000.0f))

            // Calculate makeup gain (compensate for average level reduction)
            // Approximate: makeup = sqrt(ratio) for gentle compensation
            makeupGain = kotlin.math.sqrt(ratio.toDouble()).toFloat()

            // Reset envelope follower
            envelopeLevel = 0.0f
            gainReduction = 0.0f
        }

        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false

        LogUtils.d(
            "DynamicRangeCompressor",
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "isActive=$isActive, " +
                "threshold=$thresholdLinear, " +
                "attackCoeff=$attackCoeff, " +
                "releaseCoeff=$releaseCoeff",
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
     * Processes audio buffer with dynamic range compression.
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

        applyCompression(input, output, samples, channels)
    }

    /**
     * Applies dynamic range compression with envelope follower.
     * Optimized: single pass through samples, pre-compute constants.
     */
    private fun applyCompression(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
    ) {
        // Pre-compute constants
        val invMaxValue = 1.0f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()
        val invChannels = 1.0f / channels

        for (i in 0 until samples) {
            // Calculate RMS across all channels for this sample (single pass)
            var sumSquares = 0.0f
            val sampleStartPos = input.position()

            // Read all channel samples first
            for (ch in 0 until channels) {
                val sample = input.short
                channelSamples[ch] = sample
                val normalized = sample * invMaxValue
                sumSquares += normalized * normalized
            }

            val rms = kotlin.math.sqrt(sumSquares * invChannels)

            // Update envelope follower
            if (rms > envelopeLevel) {
                // Attack: fast response to increasing level
                envelopeLevel += (rms - envelopeLevel) * attackCoeff
            } else {
                // Release: slow response to decreasing level
                envelopeLevel += (rms - envelopeLevel) * releaseCoeff
            }

            // Calculate gain reduction based on threshold and ratio
            if (envelopeLevel > thresholdLinear) {
                val excess = envelopeLevel - thresholdLinear
                val compressedExcess = excess / ratio
                val targetLevel = thresholdLinear + compressedExcess
                gainReduction = targetLevel / envelopeLevel
            } else {
                gainReduction = 1.0f // No compression below threshold
            }

            // Apply compression with reduction-proportional makeup gain.
            // ponytail: linear interpolation of makeupGain^reduction01 between unity and
            // full makeup — avoids a per-sample pow on the audio thread; use real pow
            // if curve fidelity ever matters.
            val reduction01 = ((1f - gainReduction) * ratio / (ratio - 1f)).coerceIn(0f, 1f)
            val scaledMakeup = 1f + reduction01 * (makeupGain - 1f)
            val finalGain = gainReduction * scaledMakeup
            for (ch in 0 until channels) {
                val normalized = channelSamples[ch] * invMaxValue
                val compressed = normalized * finalGain
                val clamped = compressed.coerceIn(-1.0f, 1.0f)
                val outputSample = (clamped * maxValue).toInt().toShort()
                output.putShort(outputSample)
            }
        }
    }

    override fun isEnded(): Boolean = inputEnded && queuedInputBytes == 0

    override fun flush(streamMetadata: StreamMetadata) {
        envelopeLevel = 0.0f
        gainReduction = 0.0f
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
