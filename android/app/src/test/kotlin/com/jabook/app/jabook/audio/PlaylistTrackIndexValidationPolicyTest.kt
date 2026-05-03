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

class PlaylistTrackIndexValidationPolicyTest {
    @Test
    fun `validate fails when track index is outside expected bounds`() {
        val result =
            PlaylistTrackIndexValidationPolicy.validate(
                trackIndex = 5,
                expectedCount = 5,
                playerItemCount = 10,
            )

        assertThat(result.isValid).isFalse()
        assertThat(result.failure).isEqualTo(PlaylistTrackIndexValidationFailure.OUT_OF_EXPECTED_BOUNDS)
    }

    @Test
    fun `validate fails when track index is outside player bounds`() {
        val result =
            PlaylistTrackIndexValidationPolicy.validate(
                trackIndex = 4,
                expectedCount = 10,
                playerItemCount = 4,
            )

        assertThat(result.isValid).isFalse()
        assertThat(result.failure).isEqualTo(PlaylistTrackIndexValidationFailure.OUT_OF_PLAYER_BOUNDS)
    }

    @Test
    fun `validate succeeds when track index is valid for expected and player sizes`() {
        val result =
            PlaylistTrackIndexValidationPolicy.validate(
                trackIndex = 3,
                expectedCount = 5,
                playerItemCount = 5,
            )

        assertThat(result.isValid).isTrue()
        assertThat(result.failure).isNull()
    }
}
