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
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InactivityStartConditionPolicyTest {
    @Test
    fun `returns false when isPlaying is true`() {
        assertFalse(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `returns false when mediaItemCount is zero`() {
        assertFalse(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 0,
                playbackState = Player.STATE_READY,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `returns true when STATE_READY and playWhenReady false`() {
        assertTrue(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `returns false when STATE_READY and playWhenReady true`() {
        assertFalse(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun `returns true when STATE_ENDED`() {
        assertTrue(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_ENDED,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `returns false when STATE_BUFFERING`() {
        assertFalse(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `returns false when STATE_IDLE`() {
        assertFalse(
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_IDLE,
                playWhenReady = false,
            ),
        )
    }
}
