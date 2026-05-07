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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistPlayerReadyWaitPolicyTest {
    @Test
    fun `shouldContinueWaiting returns false for ready state`() {
        assertThat(
            PlaylistPlayerReadyWaitPolicy.shouldContinueWaiting(
                attempts = 0,
                playbackState = Player.STATE_READY,
            ),
        ).isFalse()
    }

    @Test
    fun `shouldContinueWaiting returns false for buffering state`() {
        assertThat(
            PlaylistPlayerReadyWaitPolicy.shouldContinueWaiting(
                attempts = 0,
                playbackState = Player.STATE_BUFFERING,
            ),
        ).isFalse()
    }

    @Test
    fun `shouldContinueWaiting returns true for non-ready state before max attempts`() {
        assertThat(
            PlaylistPlayerReadyWaitPolicy.shouldContinueWaiting(
                attempts = 10,
                playbackState = Player.STATE_IDLE,
            ),
        ).isTrue()
    }

    @Test
    fun `shouldContinueWaiting returns false at max attempts boundary`() {
        assertThat(
            PlaylistPlayerReadyWaitPolicy.shouldContinueWaiting(
                attempts = PlaylistPlayerReadyWaitPolicy.MAX_ATTEMPTS,
                playbackState = Player.STATE_IDLE,
            ),
        ).isFalse()
    }
}
