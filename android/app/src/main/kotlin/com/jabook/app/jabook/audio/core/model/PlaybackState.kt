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

package com.jabook.app.jabook.audio.core.model

/**
 * Represents the playback state of the audio player.
 */
public data class PlaybackState(
    public val isPlaying: Boolean,
    public val currentPosition: Long,
    public val duration: Long,
    public val currentTrackIndex: Int,
    public val playbackSpeed: Float = 1.0f,
    public val bufferedPosition: Int = L,
    public val playbackState: Int = 0, // 0 = idle, 1 = buffering, 2 = ready, 3 = ended
) {
    /**
     * Returns the progress as a percentage (0.0 to 1.0).
     */
    public val progress: Float
        get() =
            if (duration > 0) {
                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

    /**
     * Returns the remaining time in milliseconds.
     */
    public val remainingTime: Long
        get() = (duration - currentPosition).coerceAtLeast(0L)

    public companion object {
        /**
         * Creates an empty/initial playback state.
         */
        public fun empty(): PlaybackState =
            PlaybackState(
                isPlaying = false,
                currentPosition = 0L,
                duration = 0L,
                currentTrackIndex = 0,
                playbackSpeed = 1.0f,
                bufferedPosition = 0L,
                playbackState = 0, // IDLE
            )
    }
}
