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
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton

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
 * Custom commands (rewind/forward) are added for better control.
 *
 * The Player's AudioAttributes configuration (with handleAudioFocus=true)
 * automatically handles audio focus management:
 * - Ducking when other apps need temporary focus (navigation, notifications)
 * - Pausing when audio focus is lost (incoming calls)
 * - Resuming when audio focus is regained
 * - Auto-pause on AUDIO_BECOMING_NOISY (headphones unplugged)
 *
 * Inspired by lissen-android implementation for custom commands.
 */
@OptIn(UnstableApi::class)
class MediaSessionManager(
    private val context: Context,
    private val player: ExoPlayer,
    private var playCallback: (() -> Unit)? = null,
    private var pauseCallback: (() -> Unit)? = null,
) {
    // private var mediaSession: MediaSession? = null // Removed duplicate session
    private var rewindCallback: (() -> Unit)? = null
    private var forwardCallback: (() -> Unit)? = null
    private var rewindSeconds: Long = 15L
    private var forwardSeconds: Long = 30L
    private var lastPlayWhenReady: Boolean = player.playWhenReady

    companion object {
        private const val REWIND_COMMAND = "com.jabook.app.jabook.audio.REWIND"
        private const val FORWARD_COMMAND = "com.jabook.app.jabook.audio.FORWARD"
        private const val DEFAULT_REWIND_SECONDS = 15L
        private const val DEFAULT_FORWARD_SECONDS = 30L

        /**
         * Provides rewind command icon.
         * Inspired by lissen-android implementation.
         */
        private fun provideRewindCommand() = CommandButton.ICON_SKIP_BACK

        /**
         * Provides forward command icon.
         * Inspired by lissen-android implementation.
         */
        private fun provideForwardCommand() = CommandButton.ICON_SKIP_FORWARD
    }

    init {
        rewindSeconds = DEFAULT_REWIND_SECONDS
        forwardSeconds = DEFAULT_FORWARD_SECONDS
        lastPlayWhenReady = player.playWhenReady
        setupPlayerListener()
        // initializeMediaSession() // Removed duplicate session creation
    }

    /**
     * Sets up Player listener to intercept play/pause commands from MediaSession.
     * When playWhenReady changes due to user action (Quick Settings, notification, etc.),
     * we call our callbacks to ensure notification is updated and timers are reset.
     *
     * CRITICAL: Enhanced logging for Play/Pause diagnostics, especially for Samsung devices.
     */
    private fun setupPlayerListener() {
        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    // Enhanced logging for diagnostics
                    val reasonText =
                        when (reason) {
                            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
                            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                            else -> "UNKNOWN($reason)"
                        }
                    android.util.Log.d(
                        "MediaSessionManager",
                        "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reasonText, " +
                            "lastPlayWhenReady=$lastPlayWhenReady, playbackState=${player.playbackState}",
                    )

                    // Only call callbacks if the change was triggered by user action
                    // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1 means user explicitly requested play/pause
                    // This happens when user clicks button in Quick Settings, notification, or lockscreen
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        if (playWhenReady && !lastPlayWhenReady) {
                            // User requested play via MediaSession (Quick Settings, notification, etc.)
                            android.util.Log.i(
                                "MediaSessionManager",
                                "Play command detected from MediaSession (USER_REQUEST), calling playCallback",
                            )
                            playCallback?.invoke()
                        } else if (!playWhenReady && lastPlayWhenReady) {
                            // User requested pause via MediaSession (Quick Settings, notification, etc.)
                            android.util.Log.i(
                                "MediaSessionManager",
                                "Pause command detected from MediaSession (USER_REQUEST), calling pauseCallback",
                            )
                            pauseCallback?.invoke()
                        } else {
                            android.util.Log.d(
                                "MediaSessionManager",
                                "PlayWhenReady changed but no callback needed: playWhenReady=$playWhenReady, lastPlayWhenReady=$lastPlayWhenReady",
                            )
                        }
                    } else {
                        android.util.Log.d(
                            "MediaSessionManager",
                            "PlayWhenReady changed but not from user request (reason=$reasonText), skipping callbacks",
                        )
                    }
                    lastPlayWhenReady = playWhenReady
                }
            },
        )
    }

    /**
     * Sets callbacks for rewind and forward actions.
     *
     * @param rewindCallback Callback for rewind action (default: -15 seconds)
     * @param forwardCallback Callback for forward action (default: +30 seconds)
     */
    fun setCallbacks(
        rewindCallback: (() -> Unit)? = null,
        forwardCallback: (() -> Unit)? = null,
    ) {
        this.rewindCallback = rewindCallback
        this.forwardCallback = forwardCallback
    }

    /**
     * Updates skip durations for rewind and forward actions.
     *
     * @param rewindSeconds Duration in seconds for rewind action
     * @param forwardSeconds Duration in seconds for forward action
     */
    fun updateSkipDurations(
        rewindSeconds: Long,
        forwardSeconds: Long,
    ) {
        this.rewindSeconds = rewindSeconds.coerceAtLeast(1L)
        this.forwardSeconds = forwardSeconds.coerceAtLeast(1L)

        android.util.Log.d(
            "MediaSessionManager",
            "Updated skip durations: rewind=${this.rewindSeconds}s, forward=${this.forwardSeconds}s",
        )
    }

    /**
     * Gets current rewind duration in seconds.
     */
    fun getRewindDuration(): Long = rewindSeconds

    /**
     * Gets current forward duration in seconds.
     */
    fun getForwardDuration(): Long = forwardSeconds

    // initializeMediaSession removed to prevent duplicate session
    // Logic moved/handled by AudioPlayerLibrarySessionCallback and AudioPlayerService

    /**
     * Default rewind action: seek back by configured seconds.
     */
    private fun defaultRewind() {
        val currentPosition = player.currentPosition
        val newPosition = (currentPosition - rewindSeconds * 1000).coerceAtLeast(0L)
        player.seekTo(newPosition)
        android.util.Log.d("MediaSessionManager", "Rewind: ${rewindSeconds}s")
    }

    /**
     * Default forward action: seek forward by configured seconds.
     */
    private fun defaultForward() {
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration != C.TIME_UNSET) {
            val newPosition = (currentPosition + forwardSeconds * 1000).coerceAtMost(duration)
            player.seekTo(newPosition)
            android.util.Log.d("MediaSessionManager", "Forward: ${forwardSeconds}s")
        }
    }

    // getMediaSession removed - use AudioPlayerService.mediaSession
    // fun getMediaSession(): androidx.media3.session.MediaSession =
    //    mediaSession ?: throw IllegalStateException("MediaSession not initialized")

    /**
     * Updates media metadata.
     */
    fun updateMetadata() {
        // Metadata is automatically updated from ExoPlayer
        // This method can be used for custom metadata updates if needed
    }

    fun release() {
        try {
            // mediaSession?.release()
            // mediaSession = null
            android.util.Log.d("MediaSessionManager", "MediaSession released successfully")
        } catch (e: Exception) {
            android.util.Log.e("MediaSessionManager", "Failed to release MediaSession", e)
        }
    }
}
