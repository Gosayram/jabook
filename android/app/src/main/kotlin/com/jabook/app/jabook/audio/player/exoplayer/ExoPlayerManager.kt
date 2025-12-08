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

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject

/**
 * Manager for ExoPlayer instance.
 *
 * Provides high-level operations for controlling ExoPlayer.
 */
@OptIn(UnstableApi::class)
class ExoPlayerManager
    @Inject
    constructor(
        private val player: ExoPlayer,
    ) {
        /**
         * Gets the underlying ExoPlayer instance.
         */
        fun getPlayer(): ExoPlayer = player

        /**
         * Prepares the player with a single media item.
         */
        fun prepare(mediaItem: MediaItem) {
            player.setMediaItem(mediaItem)
            player.prepare()
        }

        /**
         * Prepares the player with a list of media items.
         */
        fun prepare(mediaItems: List<MediaItem>) {
            player.setMediaItems(mediaItems)
            player.prepare()
        }

        /**
         * Prepares the player with media items starting at a specific index.
         */
        fun prepare(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPosition: Long = 0L,
        ) {
            player.setMediaItems(mediaItems, startIndex, startPosition)
            player.prepare()
        }

        /**
         * Plays the player.
         */
        fun play() {
            player.play()
        }

        /**
         * Pauses the player.
         */
        fun pause() {
            player.pause()
        }

        /**
         * Seeks to a specific position in the current media item.
         */
        fun seekTo(position: Long) {
            player.seekTo(position)
        }

        /**
         * Seeks to a specific media item and position.
         */
        fun seekTo(
            mediaItemIndex: Int,
            position: Long,
        ) {
            player.seekTo(mediaItemIndex, position)
        }

        /**
         * Seeks to the next media item.
         */
        fun seekToNext() {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }

        /**
         * Seeks to the previous media item.
         */
        fun seekToPrevious() {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
        }

        /**
         * Gets the current playback state.
         */
        fun getPlaybackState(): Int = player.playbackState

        /**
         * Gets whether the player is playing.
         */
        fun isPlaying(): Boolean = player.isPlaying

        /**
         * Gets the current position in milliseconds.
         */
        fun getCurrentPosition(): Long = player.currentPosition

        /**
         * Gets the duration of the current media item in milliseconds.
         */
        fun getDuration(): Long = player.duration

        /**
         * Gets the current media item index.
         */
        fun getCurrentMediaItemIndex(): Int = player.currentMediaItemIndex

        /**
         * Gets the current media item.
         */
        fun getCurrentMediaItem(): MediaItem? = player.currentMediaItem

        /**
         * Gets whether there is a next media item.
         */
        fun hasNext(): Boolean = player.hasNextMediaItem()

        /**
         * Gets whether there is a previous media item.
         */
        fun hasPrevious(): Boolean = player.hasPreviousMediaItem()

        /**
         * Sets the playback speed.
         */
        fun setPlaybackSpeed(speed: Float) {
            player.setPlaybackSpeed(speed)
        }

        /**
         * Gets the playback speed.
         */
        fun getPlaybackSpeed(): Float = player.playbackParameters.speed

        /**
         * Adds a Player.Listener.
         */
        fun addListener(listener: Player.Listener) {
            player.addListener(listener)
        }

        /**
         * Removes a Player.Listener.
         */
        fun removeListener(listener: Player.Listener) {
            player.removeListener(listener)
        }

        /**
         * Releases the player.
         * Note: This should be called when the player is no longer needed.
         */
        fun release() {
            player.release()
        }
    }
