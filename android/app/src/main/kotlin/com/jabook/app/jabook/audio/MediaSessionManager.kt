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

package com.jabook.app.jabook.audio

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi

/**
 * Manages MediaSession for system integration.
 *
 * This class handles MediaSession creation and updates for integration
 * with Android system controls, Android Auto, Wear OS, etc.
 */
@UnstableApi
class MediaSessionManager(
    private val context: Context,
    private val player: ExoPlayer
) {
    private var mediaSession: MediaSession? = null
    
    init {
        initializeMediaSession()
    }
    
    /**
     * Initializes MediaSession.
     */
    private fun initializeMediaSession() {
        mediaSession = MediaSession.Builder(context, player).build()
    }
    
    /**
     * Gets MediaSession instance.
     */
    fun getMediaSession(): androidx.media3.session.MediaSession? = mediaSession
    
    /**
     * Updates media metadata.
     */
    fun updateMetadata() {
        // Metadata is automatically updated from ExoPlayer
        // This method can be used for custom metadata updates if needed
    }
    
    /**
     * Releases MediaSession resources.
     */
    fun release() {
        mediaSession?.release()
        mediaSession = null
    }
}

