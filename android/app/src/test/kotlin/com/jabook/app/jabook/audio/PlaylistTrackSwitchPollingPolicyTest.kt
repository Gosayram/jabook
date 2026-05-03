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

class PlaylistTrackSwitchPollingPolicyTest {
    @Test
    fun `shouldContinuePolling respects max attempts`() {
        assertThat(PlaylistTrackSwitchPollingPolicy.shouldContinuePolling(0)).isTrue()
        assertThat(
            PlaylistTrackSwitchPollingPolicy.shouldContinuePolling(
                PlaylistTrackSwitchPollingPolicy.MAX_POLLING_ATTEMPTS - 1,
            ),
        ).isTrue()
        assertThat(
            PlaylistTrackSwitchPollingPolicy.shouldContinuePolling(
                PlaylistTrackSwitchPollingPolicy.MAX_POLLING_ATTEMPTS,
            ),
        ).isFalse()
    }

    @Test
    fun `isSwitchCompleted requires target index and playable state`() {
        assertThat(
            PlaylistTrackSwitchPollingPolicy.isSwitchCompleted(
                newIndex = 3,
                targetIndex = 3,
                playbackState = Player.STATE_READY,
            ),
        ).isTrue()
        assertThat(
            PlaylistTrackSwitchPollingPolicy.isSwitchCompleted(
                newIndex = 3,
                targetIndex = 3,
                playbackState = Player.STATE_BUFFERING,
            ),
        ).isTrue()
        assertThat(
            PlaylistTrackSwitchPollingPolicy.isSwitchCompleted(
                newIndex = 2,
                targetIndex = 3,
                playbackState = Player.STATE_READY,
            ),
        ).isFalse()
        assertThat(
            PlaylistTrackSwitchPollingPolicy.isSwitchCompleted(
                newIndex = 3,
                targetIndex = 3,
                playbackState = Player.STATE_IDLE,
            ),
        ).isFalse()
    }
}
