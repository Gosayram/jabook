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

/**
 * P-09: Tracks playlist load progress as a data model for UI display.
 *
 * When loading a large playlist (≥50 tracks), the UI needs to show
 * progress instead of a generic "buffering..." message.
 *
 * @property loaded Number of tracks loaded so far
 * @property total Total number of tracks in the playlist
 * @property phase Current loading phase
 */
public data class PlaylistLoadProgress(
    val loaded: Int,
    val total: Int,
    val phase: Phase,
) {
    /**
     * Loading phase of the playlist.
     */
    public enum class Phase {
        /** No loading in progress. */
        IDLE,

        /** Loading the first track (user wants to play immediately). */
        LOADING_FIRST,

        /** Loading critical tracks (current + next few). */
        LOADING_CRITICAL,

        /** Loading remaining tracks in background. */
        LOADING_BACKGROUND,

        /** All tracks loaded. */
        DONE,
    }

    /** Progress fraction 0.0–1.0. */
    val fraction: Float
        get() = if (total > 0) (loaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    /** Whether loading is in progress. */
    val isLoading: Boolean
        get() = phase != Phase.IDLE && phase != Phase.DONE

    /** Whether the first track is ready for playback. */
    val isFirstTrackReady: Boolean
        get() = phase == Phase.LOADING_CRITICAL || phase == Phase.LOADING_BACKGROUND || phase == Phase.DONE

    public companion object {
        /** Default idle state. */
        public val IDLE: PlaylistLoadProgress = PlaylistLoadProgress(0, 0, Phase.IDLE)

        /**
         * Creates a progress for a playlist of [total] tracks.
         */
        public fun of(total: Int): PlaylistLoadProgress = PlaylistLoadProgress(0, total, Phase.IDLE)
    }
}
