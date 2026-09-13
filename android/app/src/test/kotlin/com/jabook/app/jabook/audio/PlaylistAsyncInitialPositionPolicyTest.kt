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

class PlaylistAsyncInitialPositionPolicyTest {
    @Test
    fun `decide returns false when generation is stale`() {
        val decision =
            PlaylistAsyncInitialPositionPolicy.decide(
                isCurrentGeneration = false,
                initialTrackIndex = 1,
                initialPositionMs = 1_000L,
            )

        assertThat(decision.shouldApply).isFalse()
    }

    @Test
    fun `decide returns false when track index is missing`() {
        val decision =
            PlaylistAsyncInitialPositionPolicy.decide(
                isCurrentGeneration = true,
                initialTrackIndex = null,
                initialPositionMs = 1_000L,
            )

        assertThat(decision.shouldApply).isFalse()
    }

    @Test
    fun `decide returns false when position is null`() {
        val nullPosition =
            PlaylistAsyncInitialPositionPolicy.decide(
                isCurrentGeneration = true,
                initialTrackIndex = 1,
                initialPositionMs = null,
            )
        assertThat(nullPosition.shouldApply).isFalse()
    }

    @Test
    fun `decide switches to a nonzero chapter at its start`() {
        val decision =
            PlaylistAsyncInitialPositionPolicy.decide(
                isCurrentGeneration = true,
                initialTrackIndex = 1,
                initialPositionMs = 0L,
            )

        assertThat(decision.shouldApply).isTrue()
    }

    @Test
    fun `decide returns true when generation and initial params are valid`() {
        val decision =
            PlaylistAsyncInitialPositionPolicy.decide(
                isCurrentGeneration = true,
                initialTrackIndex = 1,
                initialPositionMs = 1_000L,
            )

        assertThat(decision.shouldApply).isTrue()
    }
}
