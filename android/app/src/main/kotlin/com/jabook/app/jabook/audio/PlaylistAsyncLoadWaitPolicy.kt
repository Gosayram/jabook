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

internal data class PlaylistAsyncLoadWaitDecision(
    val shouldDelayForCriticalStart: Boolean,
    val delayMs: Long,
)

internal object PlaylistAsyncLoadWaitPolicy {
    private const val SMALL_PLAYLIST_START_DELAY_MS = 100L

    internal fun decide(isLargePlaylist: Boolean): PlaylistAsyncLoadWaitDecision =
        if (isLargePlaylist) {
            PlaylistAsyncLoadWaitDecision(
                shouldDelayForCriticalStart = false,
                delayMs = 0L,
            )
        } else {
            PlaylistAsyncLoadWaitDecision(
                shouldDelayForCriticalStart = true,
                delayMs = SMALL_PLAYLIST_START_DELAY_MS,
            )
        }
}
