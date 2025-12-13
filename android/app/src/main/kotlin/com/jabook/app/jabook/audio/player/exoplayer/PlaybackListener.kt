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

package com.jabook.app.jabook.audio.player.exoplayer

import androidx.media3.common.Player
import com.jabook.app.jabook.audio.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simplified playback listener for ExoPlayer events.
 *
 * Provides reactive state updates through StateFlow.
 */
class PlaybackListener : Player.Listener {
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.empty())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _actualTrackIndex = MutableStateFlow<Int>(0)
    val actualTrackIndex: StateFlow<Int> = _actualTrackIndex.asStateFlow()

    /**
     * Callback when media item transitions.
     * This is the single source of truth for track index.
     */
    override fun onMediaItemTransition(
        mediaItem: androidx.media3.common.MediaItem?,
        reason: Int,
    ) {
        super.onMediaItemTransition(mediaItem, reason)
        // Track index will be updated by the player manager
        android.util.Log.d("PlaybackListener", "Media item transition: ${mediaItem?.mediaId}, reason: $reason")
    }

    /**
     * Callback when playback state changes.
     */
    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        android.util.Log.d("PlaybackListener", "Playback state changed: $playbackState")
    }

    /**
     * Callback when player error occurs.
     */
    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        super.onPlayerError(error)
        android.util.Log.e("PlaybackListener", "Player error: ${error.message}", error)
    }

    /**
     * Updates the playback state from player.
     */
    fun updatePlaybackState(player: Player) {
        _playbackState.value =
            PlaybackState(
                isPlaying = player.isPlaying,
                currentPosition = player.currentPosition,
                duration = player.duration,
                currentTrackIndex = player.currentMediaItemIndex,
                playbackSpeed = player.playbackParameters.speed,
                bufferedPosition = player.bufferedPosition,
                playbackState = player.playbackState, // 0 = idle, 1 = buffering, 2 = ready, 3 = ended
            )
    }

    /**
     * Updates the actual track index.
     * This should be called from onMediaItemTransition.
     */
    fun updateActualTrackIndex(index: Int) {
        _actualTrackIndex.value = index
        android.util.Log.d("PlaybackListener", "Actual track index updated: $index")
    }
}
