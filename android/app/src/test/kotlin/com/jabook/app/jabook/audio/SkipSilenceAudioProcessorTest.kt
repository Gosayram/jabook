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
        // Use 200 silence frames so that minSilence(3) + retain(65) << 200.
        // Without retain window this would drop almost all silence; with it
        // the output is still significantly shorter than the input.
        val samples = mutableListOf<Int>()
        samples.add(1000) // speech
        repeat(200) { samples.add(0) } // long silence
        samples.add(1000) // speech

        processor.queueInput(pcm16Buffer(*samples.toIntArray()))
        val output = processor.getOutput().order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2) { output.short }

        // Input is 202 frames; output must be significantly less
        assertTrue(result.size < 202)
        assertTrue(result.isNotEmpty())
        assertEquals(1000.toShort(), result.first())
        assertEquals(1000.toShort(), result.last())
        // At least some silence was dropped
        assertTrue(result.count { it == 0.toShort() } < 200)
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

        // 200 silence frames: minSilence=3, retain=65 → 132 frames are truly dropped.
        // SPEED_UP keeps every 2nd frame of those 132, so it outputs more frames than SKIP.
        val samples = mutableListOf<Int>()
        samples.add(1000)
        repeat(200) { samples.add(0) }
        samples.add(1000)
        val input = pcm16Buffer(*samples.toIntArray())

        skipProcessor.queueInput(input.duplicate())
        speedUpProcessor.queueInput(input.duplicate())

        val skipOut = skipProcessor.getOutput().order(ByteOrder.nativeOrder())
        val speedOut = speedUpProcessor.getOutput().order(ByteOrder.nativeOrder())
        val skipSamples = ShortArray(skipOut.remaining() / 2) { skipOut.short }
        val speedSamples = ShortArray(speedOut.remaining() / 2) { speedOut.short }

        assertTrue(speedSamples.size > skipSamples.size)
        assertTrue(speedSamples.size < 202) // definitely less than input
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

    // ---- Retain window tests ----

    @Test
    fun `retain window preserves trailing silence before speech`() {
        // minSilenceDurationMs=3, retainWindowMs=65 (default).
        // With 1000 Hz sample rate: retainWindow = 65 frames.
        // Create: speech(1) + silence(100) + speech(1).
        // minSilence=3, so frames 1-3 are kept. Frames 4-35 are dropped.
        // Frames 36-100 (65 frames) are the retain window.
        // Expected: [speech] [frames 1-3 silence] [65 retained silence frames] [speech]
        val retainProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        retainProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        val samples = mutableListOf<Int>()
        samples.add(1000) // speech
        repeat(100) { samples.add(0) } // 100 frames of silence
        samples.add(1000) // speech

        retainProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        val output = retainProcessor.getOutput().order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2) { output.short }

        // First sample is speech
        assertEquals(1000.toShort(), result.first())
        // Last sample is speech
        assertEquals(1000.toShort(), result.last())
        // Total: 1 (speech) + 3 (minSilence) + 65 (retain) + 1 (speech) = 70
        assertEquals(70, result.size)
    }

    @Test
    fun `retain window is limited to actual silence length`() {
        // minSilenceDurationMs=3, retainWindowMs=65.
        // Only 10 silence frames total: 3 minSilence + 7 retained (< 65).
        // Expected: 1 + 3 + 7 + 1 = 12 samples
        val retainProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        retainProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        val samples = mutableListOf<Int>()
        samples.add(1000) // speech
        repeat(10) { samples.add(0) } // 10 frames of silence
        samples.add(1000) // speech

        retainProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        val output = retainProcessor.getOutput().order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2) { output.short }

        assertEquals(12, result.size)
    }

    @Test
    fun `retain window clamped to valid range`() {
        // Retain window of 30ms should be clamped to 50ms (minimum)
        val clampedProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 30, // below min, should be clamped to 50
            )
        clampedProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        // 100 silence frames: 3 minSilence + 50 retain = 53 silence kept
        val samples = mutableListOf<Int>()
        samples.add(1000)
        repeat(100) { samples.add(0) }
        samples.add(1000)

        clampedProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        val output = clampedProcessor.getOutput().order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2) { output.short }

        // 1 speech + 3 minSilence + 50 retain + 1 speech = 55
        assertEquals(55, result.size)
    }

    @Test
    fun `multiple silence blocks each get retain window`() {
        // Two separate silence blocks, each should get its own retain window
        val retainProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        retainProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        val samples = mutableListOf<Int>()
        samples.add(1000) // speech
        repeat(100) { samples.add(0) } // long silence
        samples.add(1000) // speech
        repeat(100) { samples.add(0) } // another long silence
        samples.add(1000) // speech

        retainProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        val output = retainProcessor.getOutput().order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2) { output.short }

        // Block 1: 1 + 3 + 65 + 1 = 70
        // Block 2: 3 + 65 + 1 = 69
        // Total: 70 + 69 = 139
        assertEquals(139, result.size)
    }

    // ---- Skipped-time metric tests ----

    @Test
    fun `skipped duration metric tracks total skipped time`() {
        // minSilenceDurationMs=3, sampleRate=1000.
        // Input: 1 speech + 100 silence + 1 speech.
        // With retain=65: frames kept = 3 (min) + 65 (retain) = 68 silence frames kept
        // Frames skipped = 100 - 68 = 32 frames.
        // Duration = 32 frames * 1000ms / 1000Hz = 32ms
        val metricProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        metricProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        val samples = mutableListOf<Int>()
        samples.add(1000)
        repeat(100) { samples.add(0) }
        samples.add(1000)

        metricProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        metricProcessor.getOutput() // consume output to trigger processing

        assertEquals(32L, metricProcessor.getSkippedDurationMs())
    }

    @Test
    fun `reset skipped metric clears counter`() {
        val metricProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        metricProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        val samples = mutableListOf<Int>()
        samples.add(1000)
        repeat(100) { samples.add(0) }
        samples.add(1000)

        metricProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        metricProcessor.getOutput()

        assertTrue(metricProcessor.getSkippedDurationMs() > 0)
        metricProcessor.resetSkippedMetric()
        assertEquals(0L, metricProcessor.getSkippedDurationMs())
    }

    @Test
    fun `skipped duration returns zero before configure`() {
        val freshProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 250,
            )
        assertEquals(0L, freshProcessor.getSkippedDurationMs())
    }

    @Test
    fun `flush resets skipped metric`() {
        val flushProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        flushProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        val samples = mutableListOf<Int>()
        samples.add(1000)
        repeat(100) { samples.add(0) }
        samples.add(1000)

        flushProcessor.queueInput(pcm16Buffer(*samples.toIntArray()))
        flushProcessor.getOutput()

        assertTrue(flushProcessor.getSkippedDurationMs() > 0)

        @Suppress("DEPRECATION")
        flushProcessor.flush()

        assertEquals(0L, flushProcessor.getSkippedDurationMs())
    }

    // ---- Zero-allocation contract (behavioral verification) ----

    @Test
    fun `processor output is correct after multiple queue-getOutput cycles`() {
        // Verifies the zero-alloc temp buffer approach works across multiple
        // queue/getOutput calls (the tempFrameBuf and retainRing are reused)
        val zaProcessor =
            SkipSilenceAudioProcessor(
                enabled = true,
                silenceThresholdNormalized = 0.02f,
                minSilenceDurationMs = 3,
                mode = SkipSilenceMode.SKIP,
                retainWindowMs = 65,
            )
        zaProcessor.configure(
            AudioProcessor.AudioFormat(1000, 1, AudioFormat.ENCODING_PCM_16BIT),
        )

        // Cycle 1: short silence (no skipping)
        zaProcessor.queueInput(pcm16Buffer(500, 0, 0, 500))
        val out1 = zaProcessor.getOutput().order(ByteOrder.nativeOrder())
        val s1 = ShortArray(out1.remaining() / 2) { out1.short }
        assertArrayEquals(shortArrayOf(500, 0, 0, 500), s1)

        // Cycle 2: long silence (200 frames, skipping with retain window)
        val longSamples = mutableListOf<Int>()
        longSamples.add(1000)
        repeat(200) { longSamples.add(0) }
        longSamples.add(1000)
        zaProcessor.queueInput(pcm16Buffer(*longSamples.toIntArray()))
        val out2 = zaProcessor.getOutput().order(ByteOrder.nativeOrder())
        val s2 = ShortArray(out2.remaining() / 2) { out2.short }
        assertEquals(1000.toShort(), s2.first())
        assertEquals(1000.toShort(), s2.last())
        assertTrue(s2.size < 202) // some silence was dropped
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
