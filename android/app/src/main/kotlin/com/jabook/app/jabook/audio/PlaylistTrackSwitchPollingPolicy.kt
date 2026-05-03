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

internal object PlaylistTrackSwitchPollingPolicy {
    internal const val MAX_POLLING_ATTEMPTS = 50
    internal const val POLLING_DELAY_MS = 100L

    internal fun shouldContinuePolling(attempts: Int): Boolean = attempts < MAX_POLLING_ATTEMPTS

    internal fun isSwitchCompleted(
        newIndex: Int,
        targetIndex: Int,
        playbackState: Int,
    ): Boolean = newIndex == targetIndex && isPlayableState(playbackState)

    internal fun isPlayableState(playbackState: Int): Boolean =
        playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
}
