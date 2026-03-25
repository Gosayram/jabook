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

package com.jabook.app.jabook.audio

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class LoudnessNormalizerTest {
    private lateinit var normalizer: LoudnessNormalizer

    @Before
    fun setUp() {
        normalizer = LoudnessNormalizer(settings = AudioProcessingSettings.defaults())
        normalizer.configure(
            AudioProcessor.AudioFormat(
                44_100,
                1,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
        )
    }

    @Test
    fun `getOutput preserves pcm byte size`() {
        normalizer.queueInput(pcm16Buffer(1000, -1000, 2000, -2000))

        val output = normalizer.getOutput()

        assertEquals(8, output.remaining())
    }

    @Test
    fun `flush clears queued buffers`() {
        normalizer.queueInput(pcm16Buffer(1200, -700))
        normalizer.flush()

        val output = normalizer.getOutput()

        assertEquals(0, output.remaining())
    }

    @Test
    fun `setReplayGain clamps low gain to point one`() {
        normalizer.setReplayGain(-80f)
        normalizer.queueInput(pcm16Buffer(10_000))

        val output = normalizer.getOutput()
        val sample = output.order(ByteOrder.nativeOrder()).short.toInt()

        assertTrue(abs(sample) in 950..1050)
    }

    @Test
    fun `setReplayGain clamps high gain to ten and clips`() {
        normalizer.setReplayGain(40f)
        normalizer.queueInput(pcm16Buffer(5_000))

        val output = normalizer.getOutput()
        val sample = output.order(ByteOrder.nativeOrder()).short

        assertEquals(Short.MAX_VALUE, sample)
    }

    private fun pcm16Buffer(vararg samples: Int): ByteBuffer =
        ByteBuffer
            .allocateDirect(samples.size * 2)
            .order(ByteOrder.nativeOrder())
            .apply {
                samples.forEach { putShort(it.toShort()) }
                flip()
            }
}
