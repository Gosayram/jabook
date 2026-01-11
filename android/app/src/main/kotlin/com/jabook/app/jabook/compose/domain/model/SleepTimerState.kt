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
    data object Idle : SleepTimerState

    /**
     * Timer is counting down.
     *
     * @param remainingSeconds Seconds remaining until auto-pause
     */
    public data class Active(
        public val remainingSeconds: Int,
    ) : SleepTimerState {
        /**
         * Formatted time string (MM:SS).
         */
        public val formattedTime: String
            get() {
                public val minutes = remainingSeconds / 60
                public val seconds = remainingSeconds % 60
                return String.format("%02d:%02d", minutes, seconds)
            }
    }

    /**
     * Timer is set to end of current chapter.
     */
    data object EndOfChapter : SleepTimerState
}
