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
 * Audio processor for loudness normalization using RMS-based approach.
 *
 * This processor normalizes audio to a target RMS level to ensure
 * consistent volume across different audio files.
 *
 * Supports:
 * - ReplayGain metadata (if present in file) - preferred method
 * - RMS-based normalization (fallback if no metadata)
 *
 * Note: Full EBU R128 LUFS implementation is complex and requires
 * psychoacoustic filtering. This implementation uses RMS as a simpler
 * but effective alternative for speech content.
 */
@OptIn(UnstableApi::class)
class LoudnessNormalizer(
    private val settings: AudioProcessingSettings,
) : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Target RMS level (normalized, -1.0 to 1.0 range)
    // For speech, target RMS around 0.3-0.4 provides good loudness
    private val targetRms = 0.35f

    // RMS measurement window: 400ms
    private val windowSizeMs = 400
    private var windowSizeSamples = 0

    // Use ArrayDeque for O(1) add/remove operations (better than mutableListOf)
    private val rmsBuffer = ArrayDeque<Float>()

    // Gain adjustment (in linear scale)
    private var gainMultiplier = 1.0f

    // ReplayGain from metadata (if available)
    private var replayGainDb: Float? = null

    // Input/output buffers
    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        // Calculate window size in samples
        val sampleRate = inputAudioFormat.sampleRate
        windowSizeSamples = (sampleRate * windowSizeMs / 1000).toInt()

        // Initialize RMS buffer
        rmsBuffer.clear()
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false

        // Only activate if normalization is enabled
        isActive = settings.normalizeVolume

        android.util.Log.d(
            "LoudnessNormalizer",
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "encoding=${inputAudioFormat.encoding}, " +
                "isActive=$isActive",
        )

        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) {
            // If not active, just pass through
            return
        }

        // Store input buffer for processing
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

        // Process all input buffers
        val totalSize = inputBuffers.sumOf { it.remaining() }
        if (totalSize == 0) {
            return EMPTY_BUFFER
        }

        // Performance profiling (only in debug builds)
        val startTime =
            if (android.util.Log.isLoggable("LoudnessNormalizer", android.util.Log.DEBUG)) {
                System.nanoTime()
            } else {
                0L
            }

        // Allocate output buffer
        outputBuffer = ByteBuffer.allocateDirect(totalSize)
        outputBuffer!!.order(ByteOrder.nativeOrder())

        // Process each input buffer
        for (inputBuffer in inputBuffers) {
            processBuffer(inputBuffer, outputBuffer!!)
        }

        inputBuffers.clear()
        outputBuffer!!.flip()

        // Log processing time if profiling enabled
        if (startTime > 0) {
            val processingTimeMs = (System.nanoTime() - startTime) / 1_000_000.0
            val samplesProcessed = totalSize / 2 // 16-bit samples
            val throughput = samplesProcessed / processingTimeMs // samples per ms
            if (processingTimeMs > 10.0) { // Only log if processing takes > 10ms
                android.util.Log.d(
                    "LoudnessNormalizer",
                    "Processed $samplesProcessed samples in ${processingTimeMs}ms " +
                        "($throughput samples/ms)",
                )
            }
        }

        return outputBuffer!!
    }

    /**
     * Processes audio buffer with RMS-based normalization.
     */
    private fun processBuffer(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val format = inputAudioFormat ?: return

        // Only process 16-bit PCM (most common format)
        if (format.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) {
            // For other formats, pass through (can be extended later)
            val remaining = input.remaining()
            val position = output.position()
            output.put(input)
            return
        }

        val channels = format.channelCount
        val samples = input.remaining() / (2 * channels) // 16-bit = 2 bytes per sample

        // Calculate current RMS if not using ReplayGain
        if (replayGainDb == null) {
            calculateAndApplyRmsGain(input, output, samples, channels)
        } else {
            // Use ReplayGain if available
            applyReplayGain(input, output, samples, channels)
        }
    }

    /**
     * Calculates RMS and applies gain adjustment.
     */
    private fun calculateAndApplyRmsGain(
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

        // Calculate gain adjustment to reach target RMS
        if (avgRms > 0.001f) { // Avoid division by zero
            val targetGain = targetRms / avgRms
            // Smooth gain changes to avoid artifacts
            gainMultiplier = gainMultiplier * 0.9f + targetGain * 0.1f
            // Limit gain to reasonable range (0.1x to 10x)
            gainMultiplier = gainMultiplier.coerceIn(0.1f, 10.0f)
        }

        // Reset input position and apply gain
        input.position(originalPosition)
        applyGain(input, output, samples, channels, gainMultiplier)
    }

    /**
     * Applies ReplayGain adjustment.
     */
    private fun applyReplayGain(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
    ) {
        applyGain(input, output, samples, channels, gainMultiplier)
    }

    /**
     * Applies gain multiplier to audio samples.
     * Optimized: pre-compute constants and use direct buffer access.
     */
    private fun applyGain(
        input: ByteBuffer,
        output: ByteBuffer,
        samples: Int,
        channels: Int,
        gain: Float,
    ) {
        // Pre-compute constants to avoid repeated calculations
        val invMaxValue = 1.0f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()

        for (i in 0 until samples) {
            for (ch in 0 until channels) {
                val sample = input.short
                val normalized = sample * invMaxValue // Faster than division
                val amplified = (normalized * gain).coerceIn(-1.0f, 1.0f)
                val outputSample = (amplified * maxValue).toInt().toShort()
                output.putShort(outputSample)
            }
        }
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    override fun flush() {
        rmsBuffer.clear()
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false
        // Reset gain only if not using ReplayGain
        if (replayGainDb == null) {
            gainMultiplier = 1.0f
        }
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
        replayGainDb = null
        gainMultiplier = 1.0f
    }

    /**
     * Sets ReplayGain value from metadata (if available).
     *
     * @param replayGainDb ReplayGain value in dB
     */
    fun setReplayGain(replayGainDb: Float) {
        this.replayGainDb = replayGainDb
        // Convert dB to linear gain
        gainMultiplier = kotlin.math.pow(10.0, replayGainDb / 20.0).toFloat()
        // Limit gain to reasonable range
        gainMultiplier = gainMultiplier.coerceIn(0.1f, 10.0f)
        android.util.Log.d("LoudnessNormalizer", "ReplayGain set: ${replayGainDb}dB -> ${gainMultiplier}x")
    }

    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
