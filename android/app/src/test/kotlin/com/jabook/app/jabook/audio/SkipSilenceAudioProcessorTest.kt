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
import com.jabook.app.jabook.audio.processors.SkipSilenceAudioProcessor
import com.jabook.app.jabook.audio.processors.SkipSilenceMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class SkipSilenceAudioProcessorTest {
    private lateinit var processor: SkipSilenceAudioProcessor

    @Before
    fun setUp() {
        processor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
            )
        processor.configure(
            AudioProcessor.AudioFormat(
                1000,
                1,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
        )
    }

    @Test
    fun `long silence skips frames beyond minimum duration`() {
        processor.queueInput(
            pcm16Buffer(
                1000,
                0,
                0,
                0,
                0,
                1000,
            ),
        )

        val output = processor.getOutput().order(ByteOrder.nativeOrder())
        val samples = ShortArray(output.remaining() / 2) { output.short }

        assertTrue(samples.size < 6)
        assertTrue(samples.isNotEmpty())
        assertEquals(1000.toShort(), samples.first())
        assertEquals(1000.toShort(), samples.last())
        assertTrue(samples.count { it == 0.toShort() } <= 3)
    }

    @Test
    fun `short silence is preserved`() {
        processor.queueInput(
            pcm16Buffer(
                1000,
                0,
                0,
                1000,
            ),
        )

        val output = processor.getOutput().order(ByteOrder.nativeOrder())
        val samples = ShortArray(output.remaining() / 2) { output.short }

        assertArrayEquals(shortArrayOf(1000, 0, 0, 1000), samples)
    }

    @Test
    fun `speed up mode keeps more silence frames than skip mode`() {
        val skipProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
            )
        val speedUpProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SPEED_UP,
            )
        val format =
            AudioProcessor.AudioFormat(
                1000,
                1,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        skipProcessor.configure(format)
        speedUpProcessor.configure(format)

        val input = pcm16Buffer(1000, 0, 0, 0, 0, 0, 0, 0, 0, 1000)
        skipProcessor.queueInput(input.duplicate())
        speedUpProcessor.queueInput(input.duplicate())

        val skipOut = skipProcessor.getOutput().order(ByteOrder.nativeOrder())
        val speedOut = speedUpProcessor.getOutput().order(ByteOrder.nativeOrder())
        val skipSamples = ShortArray(skipOut.remaining() / 2) { skipOut.short }
        val speedSamples = ShortArray(speedOut.remaining() / 2) { speedOut.short }

        assertTrue(speedSamples.size > skipSamples.size)
        assertTrue(speedSamples.size < 10)
    }

    @Test
    fun `unsupported encoding keeps processor inactive`() {
        val unsupported =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 250,
            )
        unsupported.configure(
            AudioProcessor.AudioFormat(
                44_100,
                1,
                AudioFormat.ENCODING_PCM_FLOAT,
            ),
        )
        assertFalse(unsupported.isActive())
    }

    @Test
    fun `disabled processor stays inactive`() {
        val disabled =
            SkipSilenceAudioProcessor(
                enabled = false,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 250,
            )
        disabled.configure(
            AudioProcessor.AudioFormat(
                44_100,
                1,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
        )
        assertFalse(disabled.isActive())
    }

    @Test
    fun `active processor reports active state`() {
        assertTrue(processor.isActive())
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
