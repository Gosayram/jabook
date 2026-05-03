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

internal data class PlaylistInitialPositionDecision(
    val shouldScheduleDeferredApply: Boolean,
    val normalizedTargetTrackIndex: Int?,
)

internal object PlaylistInitialPositionPolicy {
    internal fun decidePostPrepare(
        requestedTrackIndex: Int?,
        requestedPositionMs: Long?,
        playlistSize: Int,
    ): PlaylistInitialPositionDecision {
        if (requestedTrackIndex == null || requestedPositionMs == null || requestedPositionMs <= 0L || playlistSize <= 0) {
            return PlaylistInitialPositionDecision(
                shouldScheduleDeferredApply = false,
                normalizedTargetTrackIndex = null,
            )
        }
        val normalized = requestedTrackIndex.coerceIn(0, playlistSize - 1)
        return PlaylistInitialPositionDecision(
            shouldScheduleDeferredApply = normalized != requestedTrackIndex,
            normalizedTargetTrackIndex = normalized,
        )
    }
}
