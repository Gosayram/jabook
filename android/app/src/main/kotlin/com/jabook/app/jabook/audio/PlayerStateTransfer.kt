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

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Helper for transferring playback state between players.
 * Useful for casting, multi-device playback, or switching audio outputs.
 *
 * Inspired by Rhythm implementation for seamless state transfer.
 */
public object PlayerStateTransfer {
    private const val TAG = "PlayerStateTransfer"

    /**
     * Saved player state for transfer.
     */
    public data class SavedPlayerState(
        val mediaItems: List<MediaItem>,
        val currentMediaItemIndex: Int,
        val currentPosition: Long,
        val playWhenReady: Boolean,
        val playbackSpeed: Float,
        val shuffleModeEnabled: Boolean,
        val repeatMode: Int,
    )

    /**
     * Save the current player state for transfer.
     * @param player The player to save state from
     * @return SavedPlayerState containing all necessary state info
     */
    public fun savePlayerState(player: Player): SavedPlayerState {
        Log.d(TAG, "Saving player state")

        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            mediaItems.add(player.getMediaItemAt(i))
        }

        return SavedPlayerState(
            mediaItems = mediaItems,
            currentMediaItemIndex = player.currentMediaItemIndex,
            currentPosition = player.currentPosition,
            playWhenReady = player.playWhenReady,
            playbackSpeed = player.playbackParameters.speed,
            shuffleModeEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    /**
     * Restore player state from a saved transfer state.
     * @param player The player to restore state to
     * @param savedState The saved state
     */
    public fun restorePlayerState(
        player: Player,
        savedState: SavedPlayerState,
    ) {
        Log.d(TAG, "Restoring player state")

        // Set media items
        player.setMediaItems(savedState.mediaItems, savedState.currentMediaItemIndex, savedState.currentPosition)

        // Restore playback settings
        player.shuffleModeEnabled = savedState.shuffleModeEnabled
        player.repeatMode = savedState.repeatMode
        player.setPlaybackSpeed(savedState.playbackSpeed)
        player.playWhenReady = savedState.playWhenReady

        // Prepare the player
        player.prepare()
    }

    /**
     * Transfer playback from one player to another seamlessly.
     * Example: Switching from local to Cast player
     * @param fromPlayer Source player
     * @param toPlayer Destination player
     */
    public fun transferPlayback(
        fromPlayer: Player,
        toPlayer: Player,
    ) {
        Log.d(TAG, "Transferring playback from ${fromPlayer.javaClass.simpleName} to ${toPlayer.javaClass.simpleName}")

        try {
            // Save state from source player
            val savedState = savePlayerState(fromPlayer)

            // Pause source player
            fromPlayer.pause()

            // Restore state to destination player
            restorePlayerState(toPlayer, savedState)

            Log.d(TAG, "Playback transfer completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error transferring playback", e)
        }
    }
}
