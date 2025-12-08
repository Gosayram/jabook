// Copyright 2025 Jabook Contributors
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
data class PlaybackState(
    val isPlaying: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val currentTrackIndex: Int,
    val playbackSpeed: Float = 1.0f,
    val bufferedPosition: Long = 0L,
) {
    /**
     * Returns the progress as a percentage (0.0 to 1.0).
     */
    val progress: Float
        get() =
            if (duration > 0) {
                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

    /**
     * Returns the remaining time in milliseconds.
     */
    val remainingTime: Long
        get() = (duration - currentPosition).coerceAtLeast(0L)

    companion object {
        /**
         * Creates an empty/initial playback state.
         */
        fun empty(): PlaybackState =
            PlaybackState(
                isPlaying = false,
                currentPosition = 0L,
                duration = 0L,
                currentTrackIndex = 0,
                playbackSpeed = 1.0f,
                bufferedPosition = 0L,
            )
    }
}
