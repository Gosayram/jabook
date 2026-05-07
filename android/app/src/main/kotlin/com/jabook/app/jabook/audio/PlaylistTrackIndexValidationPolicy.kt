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

internal enum class PlaylistTrackIndexValidationFailure {
    OUT_OF_EXPECTED_BOUNDS,
    OUT_OF_PLAYER_BOUNDS,
}

internal data class PlaylistTrackIndexValidationResult(
    val isValid: Boolean,
    val failure: PlaylistTrackIndexValidationFailure? = null,
)

internal object PlaylistTrackIndexValidationPolicy {
    internal fun validate(
        trackIndex: Int,
        expectedCount: Int,
        playerItemCount: Int,
    ): PlaylistTrackIndexValidationResult {
        if (trackIndex >= expectedCount) {
            return PlaylistTrackIndexValidationResult(
                isValid = false,
                failure = PlaylistTrackIndexValidationFailure.OUT_OF_EXPECTED_BOUNDS,
            )
        }
        if (trackIndex >= playerItemCount) {
            return PlaylistTrackIndexValidationResult(
                isValid = false,
                failure = PlaylistTrackIndexValidationFailure.OUT_OF_PLAYER_BOUNDS,
            )
        }
        return PlaylistTrackIndexValidationResult(isValid = true)
    }
}
