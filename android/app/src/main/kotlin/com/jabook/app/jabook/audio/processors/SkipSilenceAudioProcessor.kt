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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Lightweight silence skipper for 16-bit PCM streams.
 *
 * Keeps short pauses intact and only drops silence blocks longer than configured threshold.
 */
@UnstableApi
public class SkipSilenceAudioProcessor(
    private val enabled: Boolean,
    silenceThresholdNormalized: Float,
    minSilenceDurationMs: Int,
    private val mode: SkipSilenceMode = SkipSilenceMode.SKIP,
) : AudioProcessor {
    private val thresholdNormalized = silenceThresholdNormalized.coerceIn(0.0001f, 0.1f)
    private val minimumSilenceMs = minSilenceDurationMs.coerceIn(1, 2000)

    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var outputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false
    private var bytesPerFrame = 0
    private var minSilenceFrames = 0

    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var queuedInputBytes = 0
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false
    private var consecutiveSilentFrames = 0

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        bytesPerFrame = (inputAudioFormat.channelCount * PCM_16_BIT_BYTES).coerceAtLeast(PCM_16_BIT_BYTES)
        minSilenceFrames = (inputAudioFormat.sampleRate * minimumSilenceMs / 1000).coerceAtLeast(1)
        consecutiveSilentFrames = 0
        inputBuffers.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
        isActive =
            enabled &&
            inputAudioFormat.encoding == android.media.AudioFormat.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount > 0
        return outputAudioFormat!!
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }
        val copy =
            ByteBuffer
                .allocateDirect(inputBuffer.remaining())
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(inputBuffer)
                    flip()
                }
        inputBuffers.add(copy)
        queuedInputBytes += copy.remaining()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        if (inputBuffers.isEmpty()) {
            return EMPTY_BUFFER
        }

        if (!isActive || queuedInputBytes == 0) {
            val passthrough =
                ByteBuffer.allocateDirect(queuedInputBytes).order(ByteOrder.nativeOrder())
            for (input in inputBuffers) {
                passthrough.put(input)
            }
            inputBuffers.clear()
            queuedInputBytes = 0
            passthrough.flip()
            return passthrough
        }

        val out =
            if (outputBuffer == null || outputBuffer!!.capacity() < queuedInputBytes) {
                ByteBuffer.allocateDirect(queuedInputBytes).order(ByteOrder.nativeOrder()).also {
                    outputBuffer = it
                }
            } else {
                outputBuffer!!.clear()
                outputBuffer
            } ?: return EMPTY_BUFFER

        for (input in inputBuffers) {
            processBuffer(input, out)
        }
        inputBuffers.clear()
        queuedInputBytes = 0
        out.flip()
        return out
    }

    private fun processBuffer(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val channels = inputAudioFormat?.channelCount ?: 1
        val thresholdPcm = (thresholdNormalized * Short.MAX_VALUE).toInt().coerceAtLeast(1)
        val frameCount = input.remaining() / bytesPerFrame
        for (frameIndex in 0 until frameCount) {
            val frameStart = input.position()
            var silentFrame = true
            repeat(channels) {
                val sample = input.short.toInt()
                if (abs(sample) > thresholdPcm) {
                    silentFrame = false
                }
            }
            val frameEnd = input.position()
            if (silentFrame) {
                consecutiveSilentFrames++
                if (consecutiveSilentFrames <= minSilenceFrames) {
                    copyFrame(input, output, frameStart, frameEnd)
                } else if (mode == SkipSilenceMode.SPEED_UP && shouldKeepFrameInSpeedUpMode()) {
                    copyFrame(input, output, frameStart, frameEnd)
                }
            } else {
                consecutiveSilentFrames = 0
                copyFrame(input, output, frameStart, frameEnd)
            }
        }
    }

    private fun shouldKeepFrameInSpeedUpMode(): Boolean {
        val silentFramesPastMinimum = (consecutiveSilentFrames - minSilenceFrames).coerceAtLeast(0)
        return silentFramesPastMinimum % SPEED_UP_KEEP_EVERY_NTH_FRAME == 0
    }

    private fun copyFrame(
        input: ByteBuffer,
        output: ByteBuffer,
        start: Int,
        end: Int,
    ) {
        val currentPosition = input.position()
        input.position(start)
        val duplicate = input.slice()
        duplicate.limit(end - start)
        output.put(duplicate)
        input.position(currentPosition)
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        inputBuffers.clear()
        queuedInputBytes = 0
        outputBuffer = null
        inputEnded = false
        consecutiveSilentFrames = 0
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        outputAudioFormat = null
        isActive = false
        bytesPerFrame = 0
        minSilenceFrames = 0
    }

    private companion object {
        private const val PCM_16_BIT_BYTES = 2
        private const val SPEED_UP_KEEP_EVERY_NTH_FRAME = 2
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
