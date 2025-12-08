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

package com.jabook.app.jabook.audio.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jabook.app.jabook.MainActivity
import javax.inject.Inject

/**
 * Manager for MediaSession integration.
 *
 * Provides full integration with Android system controls, Android Auto, Wear OS,
 * Picture-in-Picture, and media button controls.
 */
@OptIn(UnstableApi::class)
class MediaSessionManager
    @Inject
    constructor(
        private val context: Context,
        private val player: ExoPlayer,
    ) {
        private var mediaSession: MediaSession? = null
        private var callback: MediaSessionCallback? = null

        /**
         * Initializes the MediaSession.
         */
        fun initialize(
            onPlay: () -> Unit = {},
            onPause: () -> Unit = {},
            onSkipToNext: () -> Unit = {},
            onSkipToPrevious: () -> Unit = {},
            onRewind: (() -> Unit)? = null,
            onForward: (() -> Unit)? = null,
        ) {
            if (mediaSession != null) {
                return // Already initialized
            }

            // Create callback
            callback =
                MediaSessionCallback(
                    player = player,
                    onPlay = onPlay,
                    onPause = onPause,
                    onSkipToNext = onSkipToNext,
                    onSkipToPrevious = onSkipToPrevious,
                    onRewind = onRewind,
                    onForward = onForward,
                )

            // Create MediaSession
            val sessionActivityPendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

            mediaSession =
                MediaSession
                    .Builder(context, player)
                    .setCallback(callback!!)
                    .setSessionActivity(sessionActivityPendingIntent)
                    .build()

            android.util.Log.d("MediaSessionManager", "MediaSession initialized")
        }

        /**
         * Updates the MediaMetadata.
         * Note: MediaMetadata is read-only in Player and is automatically updated from MediaItem.
         * This method is kept for API compatibility but does not modify player state.
         */
        fun updateMetadata(metadata: MediaMetadata) {
            // MediaMetadata is read-only in Player - it's automatically updated from MediaItem
            // MediaSession automatically reflects metadata from Player's current MediaItem
            android.util.Log.d("MediaSessionManager", "Metadata update requested: ${metadata.title}")
        }

        /**
         * Updates the playback state.
         */
        fun updatePlaybackState(
            state: Int,
            position: Long,
            speed: Float = 1.0f,
        ) {
            // PlaybackState is automatically managed by MediaSession through the Player
            // This method can be used for custom state updates if needed
            android.util.Log.v("MediaSessionManager", "Playback state: $state, position: $position, speed: $speed")
        }

        /**
         * Gets the MediaSession instance.
         */
        fun getMediaSession(): MediaSession? = mediaSession

        /**
         * Releases the MediaSession.
         */
        fun release() {
            mediaSession?.release()
            mediaSession = null
            callback = null
            android.util.Log.d("MediaSessionManager", "MediaSession released")
        }
    }
