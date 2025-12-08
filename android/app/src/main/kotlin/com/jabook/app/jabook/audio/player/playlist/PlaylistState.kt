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

package com.jabook.app.jabook.audio.player.playlist

import com.jabook.app.jabook.audio.core.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for playlist.
 *
 * Provides reactive state updates through StateFlow.
 * This is the single source of truth for playlist state.
 */
class PlaylistState {
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actualTrackIndex = MutableStateFlow<Int>(0)
    val actualTrackIndex: StateFlow<Int> = _actualTrackIndex.asStateFlow()

    /**
     * Updates the playlist.
     */
    fun updatePlaylist(playlist: Playlist?) {
        _playlist.value = playlist
        if (playlist != null) {
            _actualTrackIndex.value = playlist.currentIndex
        }
    }

    /**
     * Updates the loading state.
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    /**
     * Updates the actual track index.
     * This should be called from onMediaItemTransition event.
     */
    fun updateActualTrackIndex(index: Int) {
        _actualTrackIndex.value = index
        _playlist.value?.let { playlist ->
            _playlist.value = playlist.withCurrentIndex(index)
        }
    }

    /**
     * Gets the current playlist.
     */
    fun getCurrentPlaylist(): Playlist? = _playlist.value

    /**
     * Gets the current track index.
     */
    fun getCurrentTrackIndex(): Int = _actualTrackIndex.value

    /**
     * Resets the state.
     */
    fun reset() {
        _playlist.value = null
        _isLoading.value = false
        _actualTrackIndex.value = 0
    }
}
