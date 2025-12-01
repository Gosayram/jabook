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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.MainActivity

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
    private var mediaSession: MediaSession? = null
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
        initializeMediaSession()
    }

    /**
     * Sets up Player listener to intercept play/pause commands from MediaSession.
     * When playWhenReady changes due to user action (Quick Settings, notification, etc.),
     * we call our callbacks to ensure notification is updated and timers are reset.
     */
    private fun setupPlayerListener() {
        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    // Only call callbacks if the change was triggered by user action
                    // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1 means user explicitly requested play/pause
                    // This happens when user clicks button in Quick Settings, notification, or lockscreen
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        if (playWhenReady && !lastPlayWhenReady) {
                            // User requested play via MediaSession (Quick Settings, notification, etc.)
                            android.util.Log.d("MediaSessionManager", "Play command detected from MediaSession, calling playCallback")
                            playCallback?.invoke()
                        } else if (!playWhenReady && lastPlayWhenReady) {
                            // User requested pause via MediaSession (Quick Settings, notification, etc.)
                            android.util.Log.d("MediaSessionManager", "Pause command detected from MediaSession, calling pauseCallback")
                            pauseCallback?.invoke()
                        }
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

    /**
     * Initializes MediaSession with custom commands.
     *
     * MediaSession.Builder with Player automatically handles all commands.
     * Custom commands (rewind/forward) are added for better control.
     *
     * Audio focus is managed automatically by ExoPlayer through AudioAttributes
     * configured with handleAudioFocus=true in AudioPlayerService.
     *
     * Note: MediaSession automatically extracts and displays artwork from MediaMetadata
     * set in MediaItem (via setArtworkData() in AudioPlayerService.setPlaylist()).
     * No custom BitmapLoader is needed - Media3 handles artwork automatically.
     */
    private fun initializeMediaSession() {
        try {
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
                    .setCallback(
                        object : MediaSession.Callback {
                            override fun onMediaButtonEvent(
                                session: MediaSession,
                                controllerInfo: MediaSession.ControllerInfo,
                                intent: Intent,
                            ): Boolean {
                                android.util.Log.d("MediaSessionManager", "Executing media button event from: $controllerInfo")

                                // Use non-deprecated method for getting KeyEvent (Android API 33+)
                                val keyEvent =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                                    } ?: return super.onMediaButtonEvent(session, controllerInfo, intent)

                                android.util.Log.d("MediaSessionManager", "Got media key event: $keyEvent")

                                if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                                }

                                when (keyEvent.keyCode) {
                                    KEYCODE_MEDIA_NEXT -> {
                                        forwardCallback?.invoke() ?: defaultForward()
                                        return true
                                    }
                                    KEYCODE_MEDIA_PREVIOUS -> {
                                        rewindCallback?.invoke() ?: defaultRewind()
                                        return true
                                    }
                                    else -> return super.onMediaButtonEvent(session, controllerInfo, intent)
                                }
                            }

                            @OptIn(UnstableApi::class)
                            override fun onConnect(
                                session: MediaSession,
                                controller: MediaSession.ControllerInfo,
                            ): MediaSession.ConnectionResult {
                                val rewindCommand = SessionCommand(REWIND_COMMAND, Bundle.EMPTY)
                                val forwardCommand = SessionCommand(FORWARD_COMMAND, Bundle.EMPTY)

                                val sessionCommands =
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                        .buildUpon()
                                        .add(rewindCommand)
                                        .add(forwardCommand)
                                        .build()

                                // Use helper methods for icons (inspired by lissen-android)
                                val rewindButton =
                                    CommandButton
                                        .Builder(provideRewindCommand())
                                        .setSessionCommand(rewindCommand)
                                        .setDisplayName("Rewind ${rewindSeconds}s")
                                        .setEnabled(true)
                                        .build()

                                val forwardButton =
                                    CommandButton
                                        .Builder(provideForwardCommand())
                                        .setSessionCommand(forwardCommand)
                                        .setDisplayName("Forward ${forwardSeconds}s")
                                        .setEnabled(true)
                                        .build()

                                return MediaSession.ConnectionResult
                                    .AcceptedResultBuilder(session)
                                    .setAvailableSessionCommands(sessionCommands)
                                    .setCustomLayout(listOf(rewindButton, forwardButton))
                                    .build()
                            }

                            // Note: Media3 MediaSession.Callback doesn't have onPlay/onPause methods
                            // MediaSession automatically delegates play/pause commands to Player
                            // We use Player listener to intercept state changes and call callbacks if needed

                            override fun onCustomCommand(
                                session: MediaSession,
                                controller: MediaSession.ControllerInfo,
                                customCommand: SessionCommand,
                                args: Bundle,
                            ): ListenableFuture<SessionResult> {
                                android.util.Log.d("MediaSessionManager", "Executing: ${customCommand.customAction}")

                                when (customCommand.customAction) {
                                    FORWARD_COMMAND -> {
                                        forwardCallback?.invoke() ?: defaultForward()
                                    }
                                    REWIND_COMMAND -> {
                                        rewindCallback?.invoke() ?: defaultRewind()
                                    }
                                }

                                return super.onCustomCommand(session, controller, customCommand, args)
                            }
                        },
                    ).setSessionActivity(sessionActivityPendingIntent)
                    .build()

            android.util.Log.d("MediaSessionManager", "MediaSession initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MediaSessionManager", "Failed to initialize MediaSession", e)
            throw e
        }
    }

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

    /**
     * Gets MediaSession instance.
     *
     * @return MediaSession instance (never null after initialization)
     */
    fun getMediaSession(): androidx.media3.session.MediaSession =
        mediaSession ?: throw IllegalStateException("MediaSession not initialized")

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
        try {
            mediaSession?.release()
            mediaSession = null
            android.util.Log.d("MediaSessionManager", "MediaSession released successfully")
        } catch (e: Exception) {
            android.util.Log.e("MediaSessionManager", "Failed to release MediaSession", e)
        }
    }
}
