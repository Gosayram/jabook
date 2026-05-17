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

/**
 * Audio processor placeholder for future reverb implementation.
 *
 * Currently passes audio through unchanged. The strength parameter is reserved
 * for future use when the actual reverb algorithm is implemented.
 *
 * @property strength Reverb strength (0.0 to 1.0) - currently unused
 */
@UnstableApi
public class ReverbAudioProcessor(
    private val strength: Float = 0.5f,
) : AudioProcessor {
    private var inputAudioFormat: AudioProcessor.AudioFormat? = null
    private var active = false

    private val inputBuffers = mutableListOf<ByteBuffer>()
    private var outputBuffer: ByteBuffer? = null
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        active = strength > 0f

        LogUtils.d(TAG) { "Configured: active=$active, strength=$strength" }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) return
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
        if (!active || inputBuffers.isEmpty()) {
            return EMPTY_BUFFER
        }

        val totalSize = inputBuffers.sumOf { it.remaining() }
        if (totalSize == 0) {
            return EMPTY_BUFFER
        }

        outputBuffer = ByteBuffer.allocateDirect(totalSize)
        outputBuffer!!.order(ByteOrder.nativeOrder())

        for (buf in inputBuffers) {
            outputBuffer!!.put(buf)
        }

        inputBuffers.clear()
        outputBuffer!!.flip()
        return outputBuffer!!
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffers.isEmpty()

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun flush() {
        inputBuffers.clear()
        outputBuffer = null
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = null
        active = false
    }

    private companion object {
        private const val TAG = "ReverbProcessor"
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
