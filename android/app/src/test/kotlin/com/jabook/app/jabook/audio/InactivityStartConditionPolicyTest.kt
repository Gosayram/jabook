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

public class InactivityStartConditionPolicyTest {
    @Test
    public fun `shouldStart returns false when isPlaying is true`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = true,
            mediaItemCount = 1,
            playbackState = Player.STATE_READY,
            playWhenReady = false,
        )
        assertFalse(result)
    }

    @Test
    public fun `shouldStart returns false when mediaItemCount is zero`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = false,
            mediaItemCount = 0,
            playbackState = Player.STATE_ENDED,
            playWhenReady = false,
        )
        assertFalse(result)
    }

    @Test
    public fun `shouldStart returns true when state is ENDED`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = false,
            mediaItemCount = 1,
            playbackState = Player.STATE_ENDED,
            playWhenReady = false,
        )
        assertTrue(result)
    }

    @Test
    public fun `shouldStart returns false when state is READY with playWhenReady true`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = false,
            mediaItemCount = 1,
            playbackState = Player.STATE_READY,
            playWhenReady = true,
        )
        assertFalse(result)
    }

    @Test
    public fun `shouldStart returns true when state is READY with playWhenReady false`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = false,
            mediaItemCount = 1,
            playbackState = Player.STATE_READY,
            playWhenReady = false,
        )
        assertTrue(result)
    }

    @Test
    public fun `shouldStart returns false when state is BUFFERING`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = false,
            mediaItemCount = 1,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = false,
        )
        assertFalse(result)
    }

    @Test
    public fun `shouldStart returns false when state is IDLE`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = false,
            mediaItemCount = 1,
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
        )
        assertFalse(result)
    }

    @Test
    public fun `shouldStart combined isPlaying and zero mediaItemCount returns false`() {
        val result = InactivityStartConditionPolicy.shouldStart(
            isPlaying = true,
            mediaItemCount = 0,
            playbackState = Player.STATE_ENDED,
            playWhenReady = false,
        )
        assertFalse(result)
    }
}