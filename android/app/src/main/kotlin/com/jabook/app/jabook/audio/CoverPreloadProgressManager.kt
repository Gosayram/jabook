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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages cover art preloading progress for playlists.
 *
 * P-05: Shows progress while preloading cover arts for large playlists.
 */
public class CoverPreloadProgressManager(
    private val coverPreloadExecutor: CoverPreloadExecutor,
) {
    private val _progress =
        MutableStateFlow(
            CoverPreloadProgress(
                loaded = 0,
                total = 0,
                phase = CoverPreloadPhase.IDLE,
            ),
        )
    public val progress: StateFlow<CoverPreloadProgress> = _progress

    /**
     * Notifies that a playlist load phase changed.
     */
    public fun onPlaylistPhaseChanged(phase: PlaylistLoadProgress.Phase) {
        when (phase) {
            PlaylistLoadProgress.Phase.LOADING_FIRST,
            PlaylistLoadProgress.Phase.LOADING_BACKGROUND,
            -> startPreloadingCovers()
            PlaylistLoadProgress.Phase.DONE -> stopPreloadingCovers()
            else -> Unit
        }
    }

    private fun startPreloadingCovers() {
        _progress.value =
            CoverPreloadProgress(
                loaded = 0,
                total = 0,
                phase = CoverPreloadPhase.LOADING,
            )
    }

    private fun stopPreloadingCovers() {
        _progress.value =
            CoverPreloadProgress(
                loaded = 0,
                total = 0,
                phase = CoverPreloadPhase.IDLE,
            )
    }
}

public data class CoverPreloadProgress(
    public val loaded: Int,
    public val total: Int,
    public val phase: CoverPreloadPhase,
) {
    public val fraction: Float get() = if (total > 0) loaded.toFloat() / total else 0f
}

public enum class CoverPreloadPhase {
    IDLE,
    LOADING,
    DONE,
}

public interface CoverPreloadExecutor {
    public fun preload(coverUri: String)
}
