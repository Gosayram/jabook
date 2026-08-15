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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Noise gate audio processor.
 *
 * Attenuates background noise between speech segments using an adaptive
 * threshold. Less aggressive than SkipSilence — it reduces the noise floor
 * rather than removing pauses entirely.
 *
 * Auto-threshold: measures RMS of the first 5 seconds and sets threshold =
 * measuredRMS × thresholdOffset. This adapts to the recording level without
 * manual tuning.
 *
 * @property level Gate strength level (Off, Light, Medium, Strong).
 */
@UnstableApi
public class NoiseGateAudioProcessor(
    private val level: NoiseGateLevel,
) : AudioProcessor {
    // Level parameters
    private var thresholdOffset = 2.0
    private var gateTimeMs = 100.0
    private var gateAttenuationDb = -15.0

    // Auto-threshold state
    private var autoThresholdLinear = 0.0
    private var totalFramesForAutoThreshold = 0L
    private var autoThresholdComplete = false
    private var autoThresholdFrameLimit = 0L
    private var sumSquaresAccum = 0.0
    private var framesInCurrentBlock = 0

    // Envelope follower
    private var envelope = 0.0
    private var attackCoeff = 0.0
    private var releaseCoeff = 0.0

    // Gate state
    private var gateOpen = true
    private var belowThresholdCounter = 0L
    private var gateCloseFrames = 0L
    private var gateOpenFrames = 0L
    private var currentGain = 1.0f

    // Attenuation in linear
    private var gateAttenuationLinear = 1.0f

    // Scratch frame buffer (sized in configure, reused per frame)
    private var channelSamples = ShortArray(0)

    // Format / active
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    // Buffering
    private var queuedInputBuffer: ByteBuffer? = null
    private var queuedInputCapacity: Int = 0
    private var queuedInputBytes = 0
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    init {
        when (level) {
            NoiseGateLevel.Off -> {
                isActive = false
            }
            NoiseGateLevel.Light -> {
                thresholdOffset = 1.5
                gateTimeMs = 150.0
                gateAttenuationDb = -10.0
            }
            NoiseGateLevel.Medium -> {
                thresholdOffset = 2.0
                gateTimeMs = 100.0
                gateAttenuationDb = -15.0
            }
            NoiseGateLevel.Strong -> {
                thresholdOffset = 2.5
                gateTimeMs = 80.0
                gateAttenuationDb = -20.0
            }
        }
        gateAttenuationLinear = 10.0.pow(gateAttenuationDb / 20.0).toFloat()
        LogUtils.d(
            TAG,
            "Initialized: level=$level, " +
                "thresholdOffset=$thresholdOffset, " +
                "gateTime=${gateTimeMs}ms, " +
                "attenuation=${gateAttenuationDb}dB",
        )
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat

        isActive = level != NoiseGateLevel.Off &&
            inputAudioFormat.encoding == android.media.AudioFormat.ENCODING_PCM_16BIT

        if (isActive) {
            val sampleRate = inputAudioFormat.sampleRate

            channelSamples = ShortArray(inputAudioFormat.channelCount)

            gateCloseFrames = (gateTimeMs * sampleRate / 1000.0).toLong()
            gateOpenFrames = (50.0 * sampleRate / 1000.0).toLong()

            // Envelope follower: attack 1ms, release 200ms
            val attackSeconds = 0.001
            val releaseSeconds = 0.2
            attackCoeff = exp(-1.0 / (sampleRate * attackSeconds))
            releaseCoeff = exp(-1.0 / (sampleRate * releaseSeconds))

            // 5 seconds of sample frames for auto-threshold
            autoThresholdFrameLimit = (sampleRate * 5L)
            autoThresholdComplete = false
            autoThresholdLinear = 0.0
            totalFramesForAutoThreshold = 0L
            sumSquaresAccum = 0.0
            framesInCurrentBlock = 0
            envelope = 0.0
            gateOpen = true
            belowThresholdCounter = 0L
            currentGain = 1.0f
        }

        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false

        LogUtils.d(
            TAG,
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "isActive=$isActive, " +
                "gateCloseFrames=$gateCloseFrames",
        )

        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) return
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

    override fun isEnded(): Boolean = inputEnded && queuedInputBytes == 0

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun flush() {
        autoThresholdComplete = false
        autoThresholdLinear = 0.0
        totalFramesForAutoThreshold = 0L
        sumSquaresAccum = 0.0
        framesInCurrentBlock = 0
        envelope = 0.0
        gateOpen = true
        belowThresholdCounter = 0L
        currentGain = 1.0f
        queuedInputBuffer?.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        isActive = false
    }

    private fun processBuffer(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val format = inputAudioFormat ?: return
        val channels = format.channelCount
        val frames = input.remaining() / (2 * channels)
        val invMaxValue = 1.0f / Short.MAX_VALUE
        val maxValue = Short.MAX_VALUE.toFloat()

        for (i in 0 until frames) {
            var frameSumSq = 0.0

            // Read all channel samples and accumulate RMS
            for (ch in 0 until channels) {
                val s = input.short
                channelSamples[ch] = s
                val normalized = s * invMaxValue
                frameSumSq += (normalized * normalized).toDouble()
            }

            val rms = sqrt(frameSumSq / channels)

            // Auto-threshold: accumulate RMS over first 5 seconds
            if (!autoThresholdComplete) {
                sumSquaresAccum += frameSumSq / channels
                framesInCurrentBlock++
                totalFramesForAutoThreshold++

                if (totalFramesForAutoThreshold >= autoThresholdFrameLimit) {
                    autoThresholdLinear =
                        sqrt(sumSquaresAccum / framesInCurrentBlock) * thresholdOffset
                    autoThresholdComplete = true
                    LogUtils.d(TAG, "Auto-threshold complete: $autoThresholdLinear")
                }
            }

            val threshold =
                if (autoThresholdComplete) {
                    autoThresholdLinear
                } else {
                    // Before auto-threshold, use a conservative fixed gate
                    0.01
                }

            // Envelope follower (peak detector)
            if (rms > envelope) {
                envelope = envelope * attackCoeff + rms * (1.0 - attackCoeff)
            } else {
                envelope = envelope * releaseCoeff + rms * (1.0 - releaseCoeff)
            }

            // Gate decision
            if (envelope < threshold) {
                belowThresholdCounter++
                if (belowThresholdCounter >= gateCloseFrames) {
                    gateOpen = false
                }
            } else {
                belowThresholdCounter = 0
                gateOpen = true
            }

            val targetGain = if (gateOpen) 1.0f else gateAttenuationLinear

            // Smooth gain transition per sample
            currentGain += (targetGain - currentGain) * SMOOTHING_COEFF

            // Apply gain to each channel
            for (ch in 0 until channels) {
                val normalized = channelSamples[ch] * invMaxValue
                val gated = normalized * currentGain
                val clamped = gated.coerceIn(-1.0f, 1.0f)
                val outputSample = (clamped * maxValue).toInt().toShort()
                output.putShort(outputSample)
            }
        }
    }

    private companion object {
        private const val TAG = "NoiseGateProcessor"
        private const val SMOOTHING_COEFF = 0.1f
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
