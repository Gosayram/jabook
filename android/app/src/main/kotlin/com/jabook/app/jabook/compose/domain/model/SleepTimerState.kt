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

package com.jabook.app.jabook.compose.domain.model

/**
 * Represents the state of the sleep timer.
 *
 * Sleep timer automatically pauses playback after a specified duration.
 */
public sealed interface SleepTimerState {
    /**
     * Timer is not active.
     */
    public data object Idle : SleepTimerState

    /**
     * Timer is counting down.
     *
     * @param remainingSeconds Seconds remaining until auto-pause
     */
    public data class Active(
        val remainingSeconds: Int,
    ) : SleepTimerState {
        /**
         * Legacy formatted time (MM:SS) for compatibility with existing tests/consumers.
         * UI formatting should prefer platform-aware formatter in presentation layer.
         */
        public val formattedTime: String
            get() {
                val safeSeconds = remainingSeconds.coerceAtLeast(0)
                val minutes = safeSeconds / 60
                val seconds = safeSeconds % 60
                return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
            }
    }

    /**
     * Timer is set to end of current chapter.
     */
    public data object EndOfChapter : SleepTimerState

    /**
     * Timer is set to end of current track.
     */
    public data class EndOfTrack(
        val fallbackFromChapter: Boolean = false,
    ) : SleepTimerState
}
