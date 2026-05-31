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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackInterruptorTest {
    private var pauseCount = 0
    private var resumeCount = 0
    private lateinit var interruptor: PlaybackInterruptor

    @Before
    fun setUp() {
        pauseCount = 0
        resumeCount = 0
        interruptor =
            PlaybackInterruptor(
                onShouldPause = { pauseCount++ },
                onShouldResume = { resumeCount++ },
            )
    }

    // --- Interruption while playing ---

    @Test
    fun `pause when playing`() {
        interruptor.onInterruptionStarted(isPlaying = true)
        assertEquals(1, pauseCount)
        assertTrue(interruptor.isInterrupted())
        assertTrue(interruptor.wasPlayingBeforeInterrupt())
    }

    // --- Interruption while paused ---

    @Test
    fun `no pause when already paused`() {
        interruptor.onInterruptionStarted(isPlaying = false)
        assertEquals(0, pauseCount)
        assertTrue(interruptor.isInterrupted())
        assertFalse(interruptor.wasPlayingBeforeInterrupt())
    }

    // --- Resume after interruption while playing ---

    @Test
    fun `resume after interruption when was playing`() {
        interruptor.onInterruptionStarted(isPlaying = true)
        interruptor.onInterruptionEnded()
        assertEquals(1, resumeCount)
        assertFalse(interruptor.isInterrupted())
    }

    // --- No resume after interruption while paused ---

    @Test
    fun `no resume after interruption when was paused`() {
        interruptor.onInterruptionStarted(isPlaying = false)
        interruptor.onInterruptionEnded()
        assertEquals(0, resumeCount)
    }

    // --- Double interruption ---

    @Test
    fun `double interruption ignored`() {
        interruptor.onInterruptionStarted(isPlaying = true)
        interruptor.onInterruptionStarted(isPlaying = true)
        assertEquals(1, pauseCount)
    }

    // --- End without start ---

    @Test
    fun `end without start ignored`() {
        interruptor.onInterruptionEnded()
        assertEquals(0, resumeCount)
    }

    // --- Reset ---

    @Test
    fun `reset clears state`() {
        interruptor.onInterruptionStarted(isPlaying = true)
        interruptor.reset()
        assertFalse(interruptor.isInterrupted())
    }
}
