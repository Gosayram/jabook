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

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for AudioFader.
 */
@RunWith(RobolectricTestRunner::class)
class AudioFaderTest {
    private lateinit var audioFader: AudioFader
    private lateinit var player: Player

    @Before
    fun setup() {
        audioFader = AudioFader()
        player = mock()
        whenever(player.volume).thenReturn(1f)
    }

    // ============ Configuration Tests ============

    @Test
    fun `default fade duration is 300ms`() {
        assertEquals(AudioFader.DEFAULT_FADE_DURATION_MS, audioFader.fadeDurationMs)
    }

    @Test
    fun `fade duration is clamped to max value`() {
        audioFader.fadeDurationMs = 5000L
        assertEquals(AudioFader.MAX_FADE_DURATION_MS, audioFader.fadeDurationMs)
    }

    @Test
    fun `fade duration is clamped to min value`() {
        audioFader.fadeDurationMs = -100L
        assertEquals(AudioFader.MIN_FADE_DURATION_MS, audioFader.fadeDurationMs)
    }

    // ============ Fade-in Tests ============

    @Test
    fun `fadeIn with zero duration sets volume immediately`() {
        audioFader.fadeDurationMs = 0L
        var completed = false

        audioFader.fadeIn(player) { completed = true }

        verify(player).volume = 1f
        assertTrue(completed)
    }

    @Test
    fun `fadeIn starts animation when duration is positive`() {
        audioFader.fadeDurationMs = 300L

        audioFader.fadeIn(player)

        assertTrue(audioFader.isAnimating())
    }

    @Test
    fun `fadeIn sets initial volume to zero`() {
        audioFader.fadeDurationMs = 300L

        audioFader.fadeIn(player)

        // Volume is set to 0 initially, then animator updates it
        verify(player, org.mockito.kotlin.atLeastOnce()).volume = 0f
    }

    // ============ Fade-out Tests ============

    @Test
    fun `fadeOut with zero duration calls callback immediately`() {
        audioFader.fadeDurationMs = 0L
        var completed = false

        audioFader.fadeOut(player) { completed = true }

        assertTrue(completed)
    }

    @Test
    fun `fadeOut resets volume after completion with zero duration`() {
        audioFader.fadeDurationMs = 0L

        audioFader.fadeOut(player)

        // Volume set to 0 then reset to 1
        verify(player).volume = 0f
        verify(player).volume = 1f
    }

    @Test
    fun `fadeOut starts animation when duration is positive`() {
        audioFader.fadeDurationMs = 300L

        audioFader.fadeOut(player)

        assertTrue(audioFader.isAnimating())
    }

    // ============ Animation Control Tests ============

    @Test
    fun `cancelCurrentAnimation stops running animation`() {
        audioFader.fadeDurationMs = 1000L
        audioFader.fadeIn(player)
        assertTrue(audioFader.isAnimating())

        audioFader.cancelCurrentAnimation()

        assertFalse(audioFader.isAnimating())
    }

    @Test
    fun `new fade cancels previous animation`() {
        audioFader.fadeDurationMs = 1000L
        audioFader.fadeIn(player)
        val wasAnimating = audioFader.isAnimating()

        audioFader.fadeOut(player)

        assertTrue(wasAnimating)
        assertTrue(audioFader.isAnimating()) // New animation started
    }

    @Test
    fun `release cancels animation and cleans up`() {
        audioFader.fadeDurationMs = 1000L
        audioFader.fadeIn(player)

        audioFader.release()

        assertFalse(audioFader.isAnimating())
    }

    // ============ Callback Tests ============

    @Test
    fun `fadeIn callback is invoked after animation with zero duration`() {
        audioFader.fadeDurationMs = 0L
        var callbackInvoked = false

        audioFader.fadeIn(player) { callbackInvoked = true }

        assertTrue(callbackInvoked)
    }

    @Test
    fun `fadeOut callback is invoked after animation with zero duration`() {
        audioFader.fadeDurationMs = 0L
        var pauseCalled = false

        audioFader.fadeOut(player) { pauseCalled = true }

        assertTrue(pauseCalled)
    }

    @Test
    fun `isAnimating returns false when no animation running`() {
        assertFalse(audioFader.isAnimating())
    }
}
