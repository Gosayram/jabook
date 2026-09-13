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

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils

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
public class MediaSessionManager(
    private val context: Context,
    private var player: ExoPlayer,
    private var playCallback: (() -> Unit)? = null,
    private var pauseCallback: (() -> Unit)? = null,
) {
    private var rewindCallback: (() -> Unit)? = null
    private var forwardCallback: (() -> Unit)? = null
    private var rewindSeconds: Long = 0L
    private var forwardSeconds: Long = 0L
    private var lastPlayWhenReady: Boolean = player.playWhenReady

    public companion object {
        private const val DEFAULT_REWIND_SECONDS = 15L
        private const val DEFAULT_FORWARD_SECONDS = 30L
    }

    /**
     * Sets up Player listener to intercept play/pause commands from MediaSession.
     * When playWhenReady changes due to user action (Quick Settings, notification, etc.),
     * we call our callbacks to ensure notification is updated and timers are reset.
     *
     * CRITICAL: Enhanced logging for Play/Pause diagnostics, especially for Samsung devices.
     */
    private val playerListener =
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
                LogUtils.d(
                    "MediaSessionManager",
                    "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reasonText, " +
                        "lastPlayWhenReady=$lastPlayWhenReady, playbackState=${player.playbackState}",
                )

                if (playWhenReady != lastPlayWhenReady) {
                    if (playWhenReady) {
                        LogUtils.i("MediaSessionManager", "Playback started ($reasonText), calling playCallback")
                        playCallback?.invoke()
                    } else {
                        LogUtils.i("MediaSessionManager", "Playback paused ($reasonText), calling pauseCallback")
                        pauseCallback?.invoke()
                    }
                }
                lastPlayWhenReady = playWhenReady
            }
        }

    init {
        rewindSeconds = DEFAULT_REWIND_SECONDS
        forwardSeconds = DEFAULT_FORWARD_SECONDS
        lastPlayWhenReady = player.playWhenReady
        setupPlayerListener()
    }

    /**
     * Sets up Player listener to intercept play/pause commands from MediaSession.
     * When playWhenReady changes due to user action (Quick Settings, notification, etc.),
     * we call our callbacks to ensure notification is updated and timers are reset.
     *
     * CRITICAL: Enhanced logging for Play/Pause diagnostics, especially for Samsung devices.
     */
    private fun setupPlayerListener() {
        player.addListener(playerListener)
    }

    /** Moves command observation to the player currently owned by the service. */
    public fun updatePlayer(newPlayer: ExoPlayer) {
        if (player === newPlayer) return
        player.removeListener(playerListener)
        player = newPlayer
        lastPlayWhenReady = player.playWhenReady
        player.addListener(playerListener)
    }

    /**
     * Sets callbacks for rewind and forward actions.
     *
     * @param rewindCallback Callback for rewind action (default: -15 seconds)
     * @param forwardCallback Callback for forward action (default: +30 seconds)
     */
    public fun setCallbacks(
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
    public fun updateSkipDurations(
        rewindSeconds: Long,
        forwardSeconds: Long,
    ) {
        this.rewindSeconds = rewindSeconds.coerceAtLeast(1L)
        this.forwardSeconds = forwardSeconds.coerceAtLeast(1L)

        LogUtils.d(
            "MediaSessionManager",
            "Updated skip durations: rewind=${this.rewindSeconds}s, forward=${this.forwardSeconds}s",
        )
    }

    /**
     * Gets current rewind duration in seconds.
     */
    public fun getRewindDuration(): Long = rewindSeconds

    /**
     * Gets current forward duration in seconds.
     */
    public fun getForwardDuration(): Long = forwardSeconds

    // initializeMediaSession removed to prevent duplicate session
    // Logic moved/handled by AudioPlayerLibrarySessionCallback and AudioPlayerService

    public fun release() {
        try {
            player.removeListener(playerListener)
            LogUtils.d("MediaSessionManager", "MediaSession released successfully")
        } catch (e: Exception) {
            LogUtils.e("MediaSessionManager", "Failed to release MediaSession", e)
        }
    }
}
