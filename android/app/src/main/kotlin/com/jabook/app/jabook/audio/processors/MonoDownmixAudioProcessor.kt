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

/**
 * Mono downmix audio processor.
 *
 * Converts stereo (or multi-channel) PCM audio to mono by averaging all
 * channels: `out = (L + R + ...) / N`. Output retains the same channel count
 * but each channel carries the averaged sample value, so downstream
 * processors and sinks see no format change.
 *
 * Only active for 16-bit PCM input with at least 2 channels.
 */
@UnstableApi
public class MonoDownmixAudioProcessor : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var isActive = false

    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        isActive =
            inputAudioFormat.encoding == android.media.AudioFormat.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount >= 2
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || !inputBuffer.hasRemaining()) return
        val buffer = ByteBuffer.allocateDirect(inputBuffer.remaining())
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(inputBuffer)
        buffer.flip()
        inputBuffers.add(buffer)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        if (!isActive || inputBuffers.isEmpty()) {
            return EMPTY_BUFFER
        }

        val channelCount = inputAudioFormat?.channelCount ?: 2
        val totalInputBytes = inputBuffers.sumOf { it.remaining() }
        if (totalInputBytes == 0) {
            return EMPTY_BUFFER
        }

        outputBuffer = ByteBuffer.allocateDirect(totalInputBytes)
        outputBuffer!!.order(ByteOrder.nativeOrder())

        for (buf in inputBuffers) {
            buf.order(ByteOrder.nativeOrder())
            val totalShorts = buf.remaining() / 2
            val sampleFrames = totalShorts / channelCount
            repeat(sampleFrames) {
                var sum = 0
                repeat(channelCount) { sum += buf.short.toInt() }
                val avg = (sum / channelCount).toShort()
                repeat(channelCount) { outputBuffer!!.putShort(avg) }
            }
        }

        inputBuffers.clear()
        outputBuffer!!.flip()
        return outputBuffer!!
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        isActive = false
    }

    private companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
