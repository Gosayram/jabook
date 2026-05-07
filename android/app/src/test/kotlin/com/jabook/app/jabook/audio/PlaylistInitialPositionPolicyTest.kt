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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistInitialPositionPolicyTest {
    @Test
    fun `decidePostPrepare returns no deferred apply when track index is null`() {
        val decision =
            PlaylistInitialPositionPolicy.decidePostPrepare(
                requestedTrackIndex = null,
                requestedPositionMs = 1_000L,
                playlistSize = 3,
            )

        assertThat(decision.shouldScheduleDeferredApply).isFalse()
        assertThat(decision.normalizedTargetTrackIndex).isNull()
    }

    @Test
    fun `decidePostPrepare returns no deferred apply when position is null or non-positive`() {
        val nullPositionDecision =
            PlaylistInitialPositionPolicy.decidePostPrepare(
                requestedTrackIndex = 1,
                requestedPositionMs = null,
                playlistSize = 3,
            )
        val zeroPositionDecision =
            PlaylistInitialPositionPolicy.decidePostPrepare(
                requestedTrackIndex = 1,
                requestedPositionMs = 0L,
                playlistSize = 3,
            )

        assertThat(nullPositionDecision.shouldScheduleDeferredApply).isFalse()
        assertThat(nullPositionDecision.normalizedTargetTrackIndex).isNull()
        assertThat(zeroPositionDecision.shouldScheduleDeferredApply).isFalse()
        assertThat(zeroPositionDecision.normalizedTargetTrackIndex).isNull()
    }

    @Test
    fun `decidePostPrepare returns no deferred apply when playlist is empty`() {
        val decision =
            PlaylistInitialPositionPolicy.decidePostPrepare(
                requestedTrackIndex = 1,
                requestedPositionMs = 1_000L,
                playlistSize = 0,
            )

        assertThat(decision.shouldScheduleDeferredApply).isFalse()
        assertThat(decision.normalizedTargetTrackIndex).isNull()
    }

    @Test
    fun `decidePostPrepare keeps in-range index without deferred apply`() {
        val decision =
            PlaylistInitialPositionPolicy.decidePostPrepare(
                requestedTrackIndex = 2,
                requestedPositionMs = 5_000L,
                playlistSize = 5,
            )

        assertThat(decision.shouldScheduleDeferredApply).isFalse()
        assertThat(decision.normalizedTargetTrackIndex).isEqualTo(2)
    }

    @Test
    fun `decidePostPrepare schedules deferred apply when index is clamped`() {
        val decision =
            PlaylistInitialPositionPolicy.decidePostPrepare(
                requestedTrackIndex = 99,
                requestedPositionMs = 5_000L,
                playlistSize = 5,
            )

        assertThat(decision.shouldScheduleDeferredApply).isTrue()
        assertThat(decision.normalizedTargetTrackIndex).isEqualTo(4)
    }
}
