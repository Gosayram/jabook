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
 * with Android system controls, Android Auto, Wear OS, lockscreen controls,
 * and headset button clicks.
 * 
 * MediaSession automatically delegates all commands to the Player,
 * which handles:
 * - Play/Pause from lockscreen, notification, headset buttons
 * - Next/Previous track navigation (single/double/triple headset clicks)
 * - Seek operations
 * - Playback speed changes
 * 
 * The Player's AudioAttributes configuration (with handleAudioFocus=true)
 * automatically handles audio focus management:
 * - Ducking when other apps need temporary focus (navigation, notifications)
 * - Pausing when audio focus is lost (incoming calls)
 * - Resuming when audio focus is regained
 * - Auto-pause on AUDIO_BECOMING_NOISY (headphones unplugged)
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
     * 
     * MediaSession.Builder with Player automatically handles all commands.
     * Headset button clicks (single/double/triple) are handled by the system
     * and routed through MediaSession to the Player.
     * 
     * Audio focus is managed automatically by ExoPlayer through AudioAttributes
     * configured with handleAudioFocus=true in AudioPlayerService.
     */
    private fun initializeMediaSession() {
        // MediaSession.Builder with Player automatically handles all commands
        // No custom callback needed - Player handles everything
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
