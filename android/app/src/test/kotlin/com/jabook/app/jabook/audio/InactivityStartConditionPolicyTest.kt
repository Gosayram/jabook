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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InactivityStartConditionPolicyTest {
    @Test
    fun `isPlaying true always prevents timer start`() {
        val result =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = true,
                mediaItemCount = 10,
                playbackState = Player.STATE_ENDED,
                playWhenReady = false,
            )
        assertFalse(result)
    }

    @Test
    fun `empty media list prevents timer start`() {
        val result =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 0,
                playbackState = Player.STATE_READY,
                playWhenReady = false,
            )
        assertFalse(result)
    }

    @Test
    fun `ready state starts only when playWhenReady is false`() {
        val readyPaused =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
                playWhenReady = false,
            )
        val readyPlaying =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
            )
        assertTrue(readyPaused)
        assertFalse(readyPlaying)
    }

    @Test
    fun `ended state starts timer`() {
        val result =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_ENDED,
                playWhenReady = false,
            )
        assertTrue(result)
    }

    @Test
    fun `buffering and idle states do not start timer`() {
        val buffering =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = false,
            )
        val idle =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_IDLE,
                playWhenReady = false,
            )
        assertFalse(buffering)
        assertFalse(idle)
    }
}
