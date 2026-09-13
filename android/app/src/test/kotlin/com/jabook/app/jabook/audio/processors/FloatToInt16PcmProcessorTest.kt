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

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Unit tests for [FloatToInt16PcmProcessor] — the chain-head converter that lets the
 * strictly-16-bit DSP chain accept float raw PCM (renderer error 5001 fix).
 */
class FloatToInt16PcmProcessorTest {
    private fun floatBuffer(vararg samples: Float): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) buffer.putFloat(sample)
        buffer.flip()
        return buffer
    }

    private fun configureFloat(processor: FloatToInt16PcmProcessor): AudioProcessor.AudioFormat =
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))

    /** Reads the processor's current output buffer (only valid until the next queueInput). */
    private fun drainCurrent(processor: FloatToInt16PcmProcessor): ShortArray {
        val out = processor.output ?: ByteBuffer.allocate(0)
        out.flip()
        out.order(ByteOrder.LITTLE_ENDIAN)
        val result = ShortArray(out.remaining() / 2)
        for (i in result.indices) result[i] = out.short
        return result
    }

    private fun queueAndDrain(
        processor: FloatToInt16PcmProcessor,
        input: ByteBuffer,
    ): ShortArray {
        processor.queueInput(input)
        return drainCurrent(processor)
    }

    @Test
    fun `float input configures to int16 output preserving sample rate and channels`() {
        val processor = FloatToInt16PcmProcessor()

        val outputFormat = configureFloat(processor)

        assertEquals(44_100, outputFormat.sampleRate)
        assertEquals(2, outputFormat.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, outputFormat.encoding)
        assertTrue(processor.isActive)
    }

    @Test
    fun `16-bit input keeps processor inactive`() {
        val processor = FloatToInt16PcmProcessor()

        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))

        assertFalse(processor.isActive)
    }

    @Test
    fun `non-float non-16bit input is rejected`() {
        val processor = FloatToInt16PcmProcessor()

        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT))
        }
    }

    @Test
    fun `float in converts to 16-bit out with sample count preserved`() {
        val processor = FloatToInt16PcmProcessor()
        configureFloat(processor)

        processor.queueInput(floatBuffer(0.0f, 0.5f, -1.0f, 1.0f, -0.25f))
        processor.queueEndOfStream()

        val out = drainCurrent(processor)
        assertEquals(5, out.size)
        assertEquals(0, out[0].toInt())
        assertEquals((0.5f * 32767f).roundToInt(), out[1].toInt())
        assertEquals(-32767, out[2].toInt())
        assertEquals(32767, out[3].toInt())
        assertEquals((-0.25f * 32767f).roundToInt(), out[4].toInt())
    }

    @Test
    fun `odd-length buffers with frame split across queue calls are handled`() {
        val processor = FloatToInt16PcmProcessor()
        configureFloat(processor)

        // 6 bytes = 1.5 frames: frame #1 is split across the two queueInput calls.
        // Second call: 2 carry bytes + 6 new = 8 bytes = 2 frames, so all 12 input
        // bytes are consumed with no dangling remainder.
        val first = ByteArray(6)
        ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, 1.0f)
        val second = ByteArray(6)
        val secondBuf = ByteBuffer.wrap(second).order(ByteOrder.LITTLE_ENDIAN)
        secondBuf.putFloat(0, 0.0f)
        secondBuf.put(4, 0x10)
        secondBuf.put(5, 0x20) // low bytes of the next float sample (split part)

        val firstOut = queueAndDrain(processor, ByteBuffer.wrap(first))
        val secondOut = queueAndDrain(processor, ByteBuffer.wrap(second))
        processor.queueEndOfStream()

        assertEquals(3, firstOut.size + secondOut.size)
        assertEquals(32767, firstOut[0].toInt())
        assertEquals(0, secondOut[0].toInt())
        assertEquals(0, secondOut[1].toInt())
    }

    @Test
    fun `out-of-range samples are clamped`() {
        val processor = FloatToInt16PcmProcessor()
        configureFloat(processor)

        processor.queueInput(floatBuffer(2.0f, -2.0f))
        processor.queueEndOfStream()

        val out = drainCurrent(processor)
        assertEquals(32767, out[0].toInt())
        assertEquals(-32767, out[1].toInt())
    }
}
