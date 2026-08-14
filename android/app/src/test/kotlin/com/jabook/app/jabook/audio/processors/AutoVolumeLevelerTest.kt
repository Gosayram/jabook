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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

class AutoVolumeLevelerTest {
    private lateinit var leveler: AutoVolumeLeveler

    @Before
    fun setUp() {
        leveler = AutoVolumeLeveler()
        leveler.configure(
            AudioProcessor.AudioFormat(
                SAMPLE_RATE,
                1,
                C.ENCODING_PCM_16BIT,
            ),
        )
    }

    @Test
    fun `getOutput preserves pcm byte size`() {
        leveler.queueInput(constPcmBuffer(1_000, 4))

        val output = leveler.getOutput()

        assertEquals(8, output.remaining())
    }

    @Test
    fun `slew limits gain rise to zero point five dB per second`() {
        // Quiet DC signal (RMS 0.05) demands the capped target gain of 3.0 (+9.54 dB).
        val amplitude = (QUIET_RMS * Short.MAX_VALUE).toInt()

        var gain = 1.0f
        repeat(BUFFERS_PER_SECOND) {
            gain = processAndMeasureGain(amplitude)
        }

        val gainDb = 20f * log10(gain)
        assertTrue(
            "gain moved $gainDb dB after 1s, expected ~0.5 dB at 0.5 dB/s slew",
            gainDb in 0.3f..0.7f,
        )
    }

    @Test
    fun `rms window covers four hundred ms of audio time across small buffers`() {
        val loudAmplitude = (LOUD_RMS * Short.MAX_VALUE).toInt()
        val quietAmplitude = (QUIET_RMS * Short.MAX_VALUE).toInt()

        // 1s of loud audio drives the target gain below unity.
        repeat(BUFFERS_PER_SECOND) { processAndMeasureGain(loudAmplitude) }
        // 3s of quiet audio in small buffers: once the trailing 400ms window
        // contains only quiet audio the target gain must be 3.0 again.
        repeat(3 * BUFFERS_PER_SECOND) { processAndMeasureGain(quietAmplitude) }

        val gain = processAndMeasureGain(quietAmplitude)
        assertTrue(
            "gain=$gain should exceed unity once the window rotated to quiet audio",
            gain > 1.02f,
        )
    }

    @Test
    fun `output buffer instance is reused across calls`() {
        leveler.queueInput(constPcmBuffer(1_000, SAMPLES_PER_BUFFER))
        val first = leveler.getOutput()

        leveler.queueInput(constPcmBuffer(1_000, SAMPLES_PER_BUFFER))
        val second = leveler.getOutput()

        assertSame(first, second)
        assertEquals(first.capacity(), second.remaining())
    }

    private fun processAndMeasureGain(amplitude: Int): Float {
        leveler.queueInput(constPcmBuffer(amplitude, SAMPLES_PER_BUFFER))
        val output = leveler.getOutput().order(ByteOrder.nativeOrder())
        val measured = output.short.toInt()
        return measured.toFloat() / amplitude
    }

    private fun constPcmBuffer(
        amplitude: Int,
        sampleCount: Int,
    ): ByteBuffer =
        ByteBuffer
            .allocateDirect(sampleCount * 2)
            .order(ByteOrder.nativeOrder())
            .apply {
                repeat(sampleCount) { putShort(amplitude.toShort()) }
                flip()
            }

    private companion object {
        private const val SAMPLE_RATE = 44_100
        private const val SAMPLES_PER_BUFFER = 882 // 20ms mono
        private const val BUFFERS_PER_SECOND = 50
        private const val LOUD_RMS = 0.6f
        private const val QUIET_RMS = 0.05f
    }
}
