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
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.ErrorHandler
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Player event listener with improved error handling and retry logic.
 *
 * Inspired by lissen-android implementation for better error recovery.
 * Uses onEvents() for more efficient event handling (Media3 1.8+).
 */
internal class PlayerListener(
    private val context: Context,
    private val getActivePlayer: () -> ExoPlayer,
    private val getNotificationManager: () -> NotificationManager?,
    private val getIsBookCompleted: () -> Boolean,
    private val setIsBookCompleted: (Boolean) -> Unit,
    private val getSleepTimerEndOfChapter: () -> Boolean,
    private val getSleepTimerEndTime: () -> Long,
    private val cancelSleepTimer: () -> Unit,
    private val sendTimerExpiredEvent: () -> Unit,
    private val saveCurrentPosition: () -> Unit,
    private val startSleepTimerCheck: () -> Unit,
    private val getEmbeddedArtworkPath: () -> String?,
    private val setEmbeddedArtworkPath: (String?) -> Unit,
    private val getCurrentMetadata: () -> Map<String, String>?,
    private val setLastCompletedTrackIndex: ((Int) -> Unit)? = null,
    private val getLastCompletedTrackIndex: (() -> Int)? = null,
    private val getActualPlaylistSize: (() -> Int)? = null, // Get actual playlist size from filePaths
    private val playbackPositionSaver: PlaybackPositionSaver? = null,
    private val updateActualTrackIndex: ((Int) -> Unit)? = null, // Callback to update actual track index
    private val isPlaylistLoading: (() -> Boolean)? = null, // Check if playlist is currently loading
    private val storeCurrentMediaItem: (() -> Unit)? = null, // Callback to store current media item for playback resumption
) : Player.Listener {
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L // 2 seconds

    // CRITICAL: Deferred for waiting on track switch events
    // This allows PlaylistManager to wait for onMediaItemTransition instead of polling
    @Volatile
    private var pendingTrackSwitchDeferred: CompletableDeferred<Int>? = null

    /**
     * Sets a deferred to be completed when track switch occurs.
     * Used by PlaylistManager to wait for onMediaItemTransition event.
     *
     * @param deferred CompletableDeferred to complete with new track index
     */
    fun setPendingTrackSwitchDeferred(deferred: CompletableDeferred<Int>) {
        pendingTrackSwitchDeferred = deferred
        android.util.Log.d(
            "AudioPlayerService",
            "Set pendingTrackSwitchDeferred: waiting for track switch event",
        )
    }

    /**
     * Clears the pending deferred (cancels waiting for track switch).
     */
    fun clearPendingTrackSwitchDeferred() {
        pendingTrackSwitchDeferred?.cancel()
        pendingTrackSwitchDeferred = null
        android.util.Log.d("AudioPlayerService", "Cleared pendingTrackSwitchDeferred")
    }

    // Handler for periodic position checks to detect end of file when duration is incorrect
    private val positionCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var positionCheckRunnable: Runnable? = null
    private var lastPosition: Long = -1L // Track last position to detect when playback stops advancing
    private var positionStoppedCount: Int = 0 // Count how many times position hasn't changed
    private var positionStoppedStartTime: Long = -1L // When position first stopped advancing
    private val positionCheckIntervalMs = 1000L // Check every second
    private val endOfFileThresholdMs = 500L // Consider file ended if within 500ms of duration
    private val positionStoppedThreshold = 2 // Consider file ended if position hasn't changed for 2 checks (2 seconds)
    private val maxPositionStoppedTimeMs = 3000L // Maximum time position can be stopped before considering file ended (3 seconds)

    // Use onEvents() for more efficient event handling (inspired by lissen-android)
    // This allows handling multiple events in one callback for better performance
    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        // Log all events for debugging
        android.util.Log.d("AudioPlayerService", "onEvents called: $events")

        // Handle playback state changes
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            val playbackState = player.playbackState
            val stateName =
                when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
            android.util.Log.i(
                "AudioPlayerService",
                "EVENT_PLAYBACK_STATE_CHANGED: $stateName, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, mediaItemCount=${player.mediaItemCount}",
            )

            // Update notification when state changes
            // MediaSession automatically updates from ExoPlayer state
            getNotificationManager()?.updateNotification()

            // Reset retry count on successful playback
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                retryCount = 0
            }

            // Stop position check when playback state changes to ENDED
            if (playbackState == Player.STATE_ENDED) {
                stopPositionCheck()
            }

            // Handle book completion - when last track ends
            if (playbackState == Player.STATE_ENDED) {
                val currentIndex = player.currentMediaItemIndex
                // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
                // This fixes issue where player.mediaItemCount may be incorrect (e.g., 20 instead of 16)
                val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

                // Check sleep timer "end of chapter" mode (inspired by EasyBook)
                if (getSleepTimerEndOfChapter()) {
                    android.util.Log.d("AudioPlayerService", "Sleep timer expired (end of chapter), pausing playback")
                    player.playWhenReady = false
                    cancelSleepTimer()
                    sendTimerExpiredEvent()
                }

                // CRITICAL: If index is 0, invalid, or out of bounds, use saved index or calculate last track
                // This handles case when ExoPlayer reset index before STATE_ENDED or tried to go beyond last track
                val actualIndex =
                    if ((currentIndex == 0 || currentIndex < 0 || currentIndex >= totalTracks) && totalTracks > 0) {
                        val savedIndex = getLastCompletedTrackIndex?.invoke() ?: -1
                        if (savedIndex >= 0 && savedIndex < totalTracks) {
                            android.util.Log.d(
                                "AudioPlayerService",
                                "STATE_ENDED: Index is invalid ($currentIndex), using saved index $savedIndex",
                            )
                            savedIndex
                        } else {
                            // If no saved index, check if we should use last track
                            // This happens when last track ended and ExoPlayer tried to go to next (index >= totalTracks)
                            val lastTrackIndex = totalTracks - 1
                            android.util.Log.d(
                                "AudioPlayerService",
                                "STATE_ENDED: Index is invalid ($currentIndex), using calculated last track $lastTrackIndex (total=$totalTracks)",
                            )
                            lastTrackIndex
                        }
                    } else {
                        currentIndex
                    }

                if (actualIndex >= totalTracks - 1) {
                    // Last track finished - book completed
                    // Save values BEFORE any operations that might reset index
                    val lastIndex = actualIndex
                    val lastPosition = player.currentPosition

                    android.util.Log.i(
                        "AudioPlayerService",
                        "Book completed: last track finished (track $lastIndex of ${totalTracks - 1}, position=${lastPosition}ms)",
                    )

                    // Set flag to prevent further playback
                    setIsBookCompleted(true)

                    // Save last track index so getState() can return correct index even if ExoPlayer resets it
                    setLastCompletedTrackIndex?.invoke(lastIndex)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Saved last completed track index: $lastIndex",
                    )

                    // Stop playback completely to prevent auto-advance
                    // IMPORTANT: Don't call player.stop() - it resets index to 0 and clears playlist
                    // Instead, just pause and seek to end of last track
                    try {
                        // First, pause playback
                        player.pause()
                        player.playWhenReady = false

                        // Seek to end of last track to show completion
                        // This preserves the index and shows correct track number
                        if (lastIndex >= 0 && lastIndex < totalTracks) {
                            // Seek to the end of the last track (or current position if near end)
                            val seekPosition = if (lastPosition > 0) lastPosition else Long.MAX_VALUE
                            player.seekTo(lastIndex, seekPosition)
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Seeked to last track $lastIndex at position $seekPosition to preserve index",
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Error handling book completion", e)
                        // Fallback to just pausing
                        player.playWhenReady = false
                    }

                    // Save final position
                    saveCurrentPosition()

                    // Update notification to show completion
                    getNotificationManager()?.updateNotification()

                    // Send broadcast to UI to show completion message
                    val intent =
                        Intent("com.jabook.app.jabook.BOOK_COMPLETED").apply {
                            setPackage(context.packageName) // Set package for explicit broadcast
                            putExtra("last_track_index", lastIndex)
                        }
                    context.sendBroadcast(intent)
                } else {
                    // Not last track - ExoPlayer will auto-advance (normal behavior)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Track ended, will auto-advance to next (track $currentIndex of ${totalTracks - 1})",
                    )
                }
            }

            // Handle errors
            if (playbackState == Player.STATE_IDLE) {
                val error = player.playerError
                if (error != null) {
                    android.util.Log.e("AudioPlayerService", "Playback error: ${error.message}", error)
                    handlePlayerError(error)
                }
            }
        }

        // Handle playWhenReady changes (important for AudioFocus debugging)
        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            // Prevent auto-resume if book is completed
            if (getIsBookCompleted() && player.playWhenReady) {
                android.util.Log.w(
                    "AudioPlayerService",
                    "Attempted to resume playback after book completion, preventing",
                )
                player.playWhenReady = false
                // Post notification update and return early
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    getNotificationManager()?.updateNotification()
                }
                return
            }

            android.util.Log.i(
                "AudioPlayerService",
                "EVENT_PLAY_WHEN_READY_CHANGED: playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, playbackState=${player.playbackState}, mediaItemCount=${player.mediaItemCount}",
            )
            // Match lissen-android: just log, don't interfere with ExoPlayer's AudioFocus handling
            // Post notification update to main thread to ensure player state is fully updated
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                getNotificationManager()?.updateNotification()
            }
        }

        // Handle playing state changes
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
            val isPlaying = player.isPlaying
            val playbackState = player.playbackState
            val stateName =
                when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
            android.util.Log.i(
                "AudioPlayerService",
                "EVENT_IS_PLAYING_CHANGED: isPlaying=$isPlaying, playWhenReady=${player.playWhenReady}, playbackState=$stateName, mediaItemCount=${player.mediaItemCount}",
            )

            // Save position when playback stops (if not by user request)
            // This handles cases where playback stops due to system events
            if (!isPlaying && !player.playWhenReady && playbackState == Player.STATE_READY) {
                // Playback stopped but player is still ready (not ended)
                // This might be due to system events, save position
                android.util.Log.d("AudioPlayerService", "Playback stopped (not by user), saving position")
                playbackPositionSaver?.savePosition("playback_stopped")
            }

            // Save position when playback starts (critical event)
            // This ensures position is saved immediately when user resumes playback
            if (isPlaying && playbackState == Player.STATE_READY) {
                android.util.Log.v("AudioPlayerService", "Playback started, position will be saved periodically")
            }

            // Restart sleep timer check when playback starts (if timer is active)
            if (isPlaying && getSleepTimerEndTime() > 0) {
                startSleepTimerCheck()
            }

            // Start/stop position checking for end-of-file detection when duration is incorrect
            // Only check when playing and not completed
            val currentIndex = player.currentMediaItemIndex
            // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
            val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
            val isLastTrack = currentIndex >= totalTracks - 1

            // Always start position check on last track if playing (even if not READY yet)
            // This ensures we catch the end of file even if state changes
            if (isLastTrack && isPlaying && !getIsBookCompleted()) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "EVENT_IS_PLAYING_CHANGED: starting position check on last track (index=$currentIndex/$totalTracks, state=$playbackState)",
                )
                startPositionCheck()
            } else if (isPlaying && !getIsBookCompleted() && playbackState == Player.STATE_READY) {
                // Start check on any track when playing and ready
                android.util.Log.v(
                    "AudioPlayerService",
                    "EVENT_IS_PLAYING_CHANGED: starting position check (index=$currentIndex/$totalTracks)",
                )
                startPositionCheck()
            } else {
                if (isLastTrack) {
                    android.util.Log.d(
                        "AudioPlayerService",
                        "EVENT_IS_PLAYING_CHANGED: NOT starting position check (isPlaying=$isPlaying, completed=${getIsBookCompleted()}, state=$playbackState, index=$currentIndex/$totalTracks)",
                    )
                }
                // Don't stop check on last track if we're not playing - file might have just ended
                if (!isLastTrack) {
                    stopPositionCheck()
                }
            }

            // Don't reset playWhenReady automatically - let ExoPlayer handle AudioFocus
            // The previous check was too aggressive and was preventing playback from starting

            // Post notification update to main thread to ensure player state is fully updated
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                getNotificationManager()?.updateNotification()
            }
        }

        // Handle media item transitions
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            // CRITICAL: Update actual track index from onMediaItemTransition event
            // This is the single source of truth for current track index
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex >= 0) {
                // CRITICAL: Don't update actualTrackIndex to 0 during playlist loading
                // ExoPlayer may switch to first track (index 0) during loading, which would reset our target index
                val isLoading = isPlaylistLoading?.invoke() ?: false
                if (!isLoading || currentIndex != 0) {
                    updateActualTrackIndex?.invoke(currentIndex)
                    android.util.Log.v(
                        "AudioPlayerService",
                        "Updated actualTrackIndex to $currentIndex from EVENT_MEDIA_ITEM_TRANSITION " +
                            "(isLoading=$isLoading)",
                    )
                } else {
                    android.util.Log.v(
                        "AudioPlayerService",
                        "Skipped updating actualTrackIndex to 0 during playlist loading " +
                            "(isLoading=$isLoading, currentIndex=$currentIndex)",
                    )
                }

                // Store current media item for playback resumption
                storeCurrentMediaItem?.invoke()

                // CRITICAL: Complete deferred if waiting for track switch
                // This allows PlaylistManager to wait for onMediaItemTransition instead of polling
                pendingTrackSwitchDeferred?.let { deferred ->
                    try {
                        deferred.complete(currentIndex)
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Completed pendingTrackSwitchDeferred with index $currentIndex",
                        )
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "AudioPlayerService",
                            "Failed to complete pendingTrackSwitchDeferred: ${e.message}",
                        )
                    } finally {
                        pendingTrackSwitchDeferred = null
                    }
                }
            }

            // Save position of previous track before transitioning
            // This ensures position is saved even during rapid track changes
            playbackPositionSaver?.savePosition("track_changed")

            // Track changed - restart position check for new track
            stopPositionCheck()
            // Reset position tracking for new track
            lastPosition = -1L
            positionStoppedCount = 0
            positionStoppedStartTime = -1L

            // Track changed - update notification to show new track's embedded artwork
            // MediaSession automatically updates from ExoPlayer
            getNotificationManager()?.updateNotification()

            // Log track transition for debugging (inspired by lissen-android logging)
            // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
            val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

            // Restart position check if we're playing and on the last track
            if (player.isPlaying &&
                !getIsBookCompleted() &&
                player.playbackState == Player.STATE_READY &&
                currentIndex >= totalTracks - 1
            ) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Media item transition to last track: starting position check (index=$currentIndex/$totalTracks)",
                )
                startPositionCheck()
            }
            val currentItem = player.currentMediaItem
            val title = currentItem?.mediaMetadata?.title?.toString() ?: "Unknown"
            android.util.Log.d("AudioPlayerService", "Media item transition:")
            android.util.Log.d("AudioPlayerService", "  - Index: $currentIndex")
            android.util.Log.d("AudioPlayerService", "  - Title: $title")
            android.util.Log.d("AudioPlayerService", "  - Total items: ${player.mediaItemCount}")

            // Check sleep timer "end of chapter" mode (inspired by EasyBook)
            // Trigger when track transitions automatically (not manual seek)
            // Note: We need to check the reason, but onEvents doesn't provide it
            // So we check in onMediaItemTransition override instead
            // This is a fallback check

            // Reset retry count on track change (new track might work even if previous failed)
            retryCount = 0

            // Restart position check if playing
            if (player.isPlaying && !getIsBookCompleted() && player.playbackState == Player.STATE_READY) {
                startPositionCheck()
            }
        }

        // Handle playback parameters changes (speed, pitch, etc.)
        if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
            val params = player.playbackParameters
            android.util.Log.d(
                "AudioPlayerService",
                "Playback parameters changed: speed=${params.speed}, pitch=${params.pitch}",
            )
        }

        // Handle repeat mode changes
        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
            android.util.Log.d("AudioPlayerService", "Repeat mode changed: ${player.repeatMode}")
        }

        // Handle shuffle mode changes
        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            android.util.Log.d("AudioPlayerService", "Shuffle mode changed: ${player.shuffleModeEnabled}")
        }
    }

    // Keep individual listeners for backward compatibility and specific handling
    override fun onPlaybackStateChanged(playbackState: Int) {
        // This is also handled in onEvents, but kept for explicit handling
        getNotificationManager()?.updateNotification()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // This is also handled in onEvents, but kept for explicit handling
        getNotificationManager()?.updateNotification()
    }

    /**
     * Handles playWhenReady changes, including audio focus loss events.
     *
     * This is called when playback is paused/resumed, including when audio focus is lost
     * (e.g., incoming call, other audio app starts).
     */
    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        val reasonText =
            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                else -> "UNKNOWN($reason)"
            }
        android.util.Log.i(
            "AudioPlayerService",
            "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reasonText",
        )

        // Save position when playback stops due to audio focus loss or becoming noisy
        if (!playWhenReady) {
            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                    android.util.Log.i("AudioPlayerService", "Audio focus lost, saving position")
                    playbackPositionSaver?.savePosition("audio_focus_lost")
                }
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                    android.util.Log.i("AudioPlayerService", "Audio becoming noisy, saving position")
                    playbackPositionSaver?.savePosition("audio_becoming_noisy")
                }
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        android.util.Log.e("AudioPlayerService", "Player error occurred", error)
        handlePlayerError(error)
    }

    /**
     * Handles player errors with automatic retry for network errors.
     *
     * Inspired by lissen-android: improved error handling with detailed messages.
     *
     * @param error The playback error that occurred
     */
    private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        ErrorHandler.handlePlaybackError("AudioPlayerService", error, "Player error during playback")

        val errorCode = error.errorCode
        val userFriendlyMessage =
            when (errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                    // Network errors - try to retry automatically
                    if (retryCount < maxRetries) {
                        retryCount++
                        android.util.Log.w(
                            "AudioPlayerService",
                            "Network connection failed, retrying ($retryCount/$maxRetries)...",
                        )

                        // Retry after delay with exponential backoff
                        val backoffDelay = retryDelayMs * retryCount
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            getActivePlayer().prepare()
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Retry attempt $retryCount after network error (delay: ${backoffDelay}ms)",
                            )
                        }, backoffDelay)

                        return // Don't show error message yet, wait for retry
                    }
                    "Network error: Unable to connect. Please check your internet connection."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    if (retryCount < maxRetries) {
                        retryCount++
                        android.util.Log.w(
                            "AudioPlayerService",
                            "Network timeout, retrying ($retryCount/$maxRetries)...",
                        )
                        val backoffDelay = retryDelayMs * retryCount
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            getActivePlayer().prepare()
                        }, backoffDelay)
                        return
                    }
                    "Network timeout: Connection timed out. Please try again."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    "Server error: Unable to load audio from server. Please try again later."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                    // File not found - try to skip to next available track
                    handleFileNotFound()
                    "File not found: Audio file is missing or has been moved."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                    "Permission denied: Cannot access audio file. Please check file permissions."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                -> {
                    "Format error: Audio file is corrupted or in an unsupported format."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                -> {
                    "Decoder error: Unable to decode audio. The format may not be supported on this device."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> {
                    "Audio error: Unable to initialize audio playback. Please try again."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                    "Audio error: Failed to write audio data. Please try again."
                }
                else -> {
                    val errorMessage = error.message ?: "Unknown error"
                    "Playback error: $errorMessage (code: $errorCode)"
                }
            }

        android.util.Log.e("AudioPlayerService", "Player error (user-friendly): $userFriendlyMessage")
        getNotificationManager()?.updateNotification()

        // Store error for retrieval via MethodChannel if needed
        // Error will be automatically propagated through state stream
    }

    /**
     * Handles file not found errors by attempting to skip to next available track.
     *
     * Inspired by lissen-android's approach to handle missing files gracefully.
     */
    private fun handleFileNotFound() {
        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

        if (totalTracks <= 1) {
            // Only one track or no tracks, can't skip
            android.util.Log.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
            return
        }

        // Don't advance if we're at the last track
        if (currentIndex >= totalTracks - 1) {
            android.util.Log.w("AudioPlayerService", "Last track, cannot skip forward")
            player.playWhenReady = false
            return
        }

        // Try to skip to next track (not using modulo to prevent circular navigation)
        val nextIndex = currentIndex + 1
        if (nextIndex < totalTracks) {
            android.util.Log.w(
                "AudioPlayerService",
                "File not found at index $currentIndex, skipping to next track $nextIndex",
            )
            try {
                player.seekTo(nextIndex, 0L)
                // Auto-play next track if player was playing
                // Use playWhenReady instead of play() for better compatibility
                if (player.isPlaying || player.playWhenReady) {
                    player.playWhenReady = true
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to skip to next track", e)
                // Don't rethrow - log and continue
            }
        } else {
            // No more tracks available, pause playback
            android.util.Log.w("AudioPlayerService", "No more tracks available, pausing playback")
            try {
                player.playWhenReady = false
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to pause playback", e)
            }
        }
    }

    // onMediaItemTransition is now handled in onEvents() for better performance
    // Keeping this for backward compatibility and sleep timer "end of chapter" handling
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // This is also handled in onEvents, but kept for explicit handling if needed
        val currentIndex = getActivePlayer().currentMediaItemIndex
        android.util.Log.d(
            "AudioPlayerService",
            "Media item transition (explicit): index=$currentIndex, reason=$reason",
        )

        // CRITICAL: Update actual track index from onMediaItemTransition event
        // This is the single source of truth for current track index
        if (currentIndex >= 0) {
            // CRITICAL: Don't update actualTrackIndex to 0 during playlist loading
            // ExoPlayer may switch to first track (index 0) during loading, which would reset our target index
            val isLoading = isPlaylistLoading?.invoke() ?: false
            if (!isLoading || currentIndex != 0) {
                updateActualTrackIndex?.invoke(currentIndex)
                android.util.Log.v(
                    "AudioPlayerService",
                    "Updated actualTrackIndex to $currentIndex from onMediaItemTransition " +
                        "(isLoading=$isLoading)",
                )
            } else {
                android.util.Log.v(
                    "AudioPlayerService",
                    "Skipped updating actualTrackIndex to 0 during playlist loading " +
                        "(isLoading=$isLoading, currentIndex=$currentIndex)",
                )
            }

            // CRITICAL: Complete deferred if waiting for track switch
            // This allows PlaylistManager to wait for onMediaItemTransition instead of polling
            pendingTrackSwitchDeferred?.let { deferred ->
                try {
                    deferred.complete(currentIndex)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Completed pendingTrackSwitchDeferred with index $currentIndex (explicit handler)",
                    )
                } catch (e: Exception) {
                    android.util.Log.w(
                        "AudioPlayerService",
                        "Failed to complete pendingTrackSwitchDeferred: ${e.message}",
                    )
                } finally {
                    pendingTrackSwitchDeferred = null
                }
            }
        }

        // Check sleep timer "end of chapter" mode (inspired by EasyBook)
        // Trigger when track transitions automatically (not manual seek)
        if (getSleepTimerEndOfChapter() && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            android.util.Log.d(
                "AudioPlayerService",
                "Sleep timer expired (end of chapter on auto transition), pausing playback",
            )
            val player = getActivePlayer()
            player.playWhenReady = false
            cancelSleepTimer()
            sendTimerExpiredEvent()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val player = getActivePlayer()
        val previousIndex = oldPosition.mediaItemIndex
        val currentIndex = newPosition.mediaItemIndex
        // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

        // Allow manual track switching even if book is completed
        // Only block automatic transitions from last track
        val isManualSeek =
            reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        val isAutoTransition = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == 4

        // If book is completed, only allow manual seeks to different tracks
        // Block automatic transitions that would go beyond last track
        if (getIsBookCompleted()) {
            if (isManualSeek && currentIndex != previousIndex) {
                // Manual track switch - reset completion flag to allow navigation
                android.util.Log.i(
                    "AudioPlayerService",
                    "Manual track switch detected after book completion: $previousIndex -> $currentIndex, resetting completion flag",
                )
                setIsBookCompleted(false)
                setLastCompletedTrackIndex?.invoke(-1) // Clear saved index
            } else if (isAutoTransition) {
                // Block automatic transitions after completion
                android.util.Log.w(
                    "AudioPlayerService",
                    "Automatic transition detected after book completion, ignoring",
                )
                return
            }
            // For other reasons, allow the transition (e.g., internal state changes)
        }

        // Handle position discontinuities (e.g., track changes, seeks)
        // Inspired by lissen-android: handle unavailable tracks gracefully
        if (currentIndex != previousIndex) {
            android.util.Log.d(
                "AudioPlayerService",
                "Position discontinuity: $previousIndex -> $currentIndex, reason=$reason, totalTracks=$totalTracks (actual=${getActualPlaylistSize?.invoke()}, player=${player.mediaItemCount})",
            )

            // CRITICAL: If we're transitioning from last track to index 0 or invalid index,
            // it means ExoPlayer tried to auto-advance to next track (which doesn't exist)
            // This happens when file ends but ExoPlayer doesn't detect it properly
            // We need to handle book completion HERE, before index resets
            // Note: reason=4 is DISCONTINUITY_REASON_AUTO_TRANSITION
            if (previousIndex >= 0 &&
                previousIndex >= totalTracks - 1 &&
                (currentIndex == 0 || currentIndex < 0 || currentIndex >= totalTracks) &&
                (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == 4)
            ) {
                // Last track ended, ExoPlayer tried to go to next (doesn't exist), index reset to 0
                android.util.Log.i(
                    "AudioPlayerService",
                    "Detected end of book: transition from last track $previousIndex to invalid index $currentIndex (total=$totalTracks)",
                )
                // Use the PREVIOUS index (last track) for completion
                handleBookCompletion(player, previousIndex)
                // Prevent the transition by seeking back to last track
                if (previousIndex >= 0 && previousIndex < totalTracks) {
                    try {
                        val lastPosition = oldPosition.positionMs
                        player.seekTo(previousIndex, lastPosition.coerceAtLeast(0L))
                        player.pause()
                        player.playWhenReady = false
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Prevented invalid transition, seeked back to track $previousIndex at position $lastPosition",
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Error preventing invalid transition", e)
                    }
                }
                return
            }

            // Stop position check on track change, will restart if playing
            stopPositionCheck()

            // Reset retry count on track change
            retryCount = 0

            // Inspired by lissen-android: check if current track is available
            // If track is not available, try to find next available track
            val currentItem = getActivePlayer().currentMediaItem
            if (currentItem != null) {
                // Check if track URI is accessible
                val uri = currentItem.localConfiguration?.uri
                if (uri != null) {
                    // For file URIs, check if file exists
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: "")
                        if (!file.exists() || !file.canRead()) {
                            android.util.Log.w(
                                "AudioPlayerService",
                                "Current track file not accessible: ${uri.path}, trying to skip",
                            )
                            // Try to skip to next available track
                            skipToNextAvailableTrack(currentIndex, previousIndex)
                        }
                    }
                }
            }
        }
    }

    /**
     * Skips to next available track if current track is unavailable.
     * Inspired by lissen-android's PlaybackNotificationService.
     *
     * @param currentIndex Current track index
     * @param previousIndex Previous track index
     */
    private fun skipToNextAvailableTrack(
        currentIndex: Int,
        previousIndex: Int,
    ) {
        val player = getActivePlayer()

        if (player.mediaItemCount <= 1) {
            // Only one track or no tracks, can't skip
            android.util.Log.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
            return
        }

        // Determine direction (forward or backward)
        val direction =
            when {
                currentIndex > previousIndex || (currentIndex == 0 && previousIndex == player.mediaItemCount - 1) -> 1 // FORWARD
                else -> -1 // BACKWARD
            }

        // Try to find next available track
        var nextIndex = currentIndex
        var attempts = 0
        val maxAttempts = player.mediaItemCount

        while (attempts < maxAttempts) {
            nextIndex =
                when (direction) {
                    1 -> {
                        // Forward: don't use modulo, check bounds
                        if (nextIndex + 1 < player.mediaItemCount) {
                            nextIndex + 1
                        } else {
                            // Reached end, can't go forward
                            android.util.Log.w("AudioPlayerService", "Reached last track, cannot skip forward")
                            player.playWhenReady = false
                            return
                        }
                    }
                    else -> {
                        // Backward: can use modulo or bounds check
                        if (nextIndex - 1 >= 0) nextIndex - 1 else player.mediaItemCount - 1
                    }
                }

            // Check if this track is available
            val item = player.getMediaItemAt(nextIndex)
            val uri = item.localConfiguration?.uri

            if (uri != null) {
                val isAvailable =
                    when (uri.scheme) {
                        "file" -> {
                            val file = File(uri.path ?: "")
                            file.exists() && file.canRead()
                        }
                        "http", "https" -> true // Assume network URLs are available
                        else -> true // Assume other schemes are available
                    }

                if (isAvailable) {
                    android.util.Log.d("AudioPlayerService", "Found available track at index $nextIndex, seeking to it")
                    try {
                        player.seekTo(nextIndex, 0L)
                        // Restore playWhenReady if was playing
                        if (player.playWhenReady) {
                            player.playWhenReady = true
                        }
                        return
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to seek to available track", e)
                    }
                }
            }

            attempts++
        }

        // No available tracks found, pause playback
        android.util.Log.w("AudioPlayerService", "No available tracks found, pausing playback")
        try {
            player.playWhenReady = false
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to pause playback", e)
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        // Metadata (including embedded artwork) was extracted from audio file
        // Media3 1.8: artworkData and artworkUri are NOT deprecated - use them directly
        // Inspired by lissen-android: prefer artworkUri over artworkData for better performance
        val title = mediaMetadata.title?.toString() ?: "Unknown"
        val artist = mediaMetadata.artist?.toString() ?: "Unknown"
        val album = mediaMetadata.albumTitle?.toString()

        // Log metadata extraction for debugging
        android.util.Log.d("AudioPlayerService", "Metadata changed: title=$title, artist=$artist, album=$album")

        // Check if artwork is available (prefer URI, then data)
        val artworkUri = mediaMetadata.artworkUri
        val artworkData = mediaMetadata.artworkData
        val hasArtworkData = artworkData != null && artworkData.isNotEmpty()
        val hasArtworkUri = artworkUri != null

        if (artworkUri != null) {
            android.util.Log.d("AudioPlayerService", "Artwork URI available: $artworkUri")
            // Clear embedded artwork path if external URI is available
            setEmbeddedArtworkPath(null)
        } else if (hasArtworkData) {
            android.util.Log.d("AudioPlayerService", "Embedded artwork data available: ${artworkData?.size ?: 0} bytes")
            // Save embedded artwork to temporary file for Flutter access
            try {
                val cacheDir = context.cacheDir
                val artworkFile = File(cacheDir, "embedded_artwork_${System.currentTimeMillis()}.jpg")
                artworkFile.outputStream().use { it.write(artworkData) }
                setEmbeddedArtworkPath(artworkFile.absolutePath)
                android.util.Log.i("AudioPlayerService", "Saved embedded artwork to: ${artworkFile.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to save embedded artwork", e)
                setEmbeddedArtworkPath(null)
            }
        } else {
            android.util.Log.d("AudioPlayerService", "No artwork available")
            setEmbeddedArtworkPath(null)
        }

        android.util.Log.d("AudioPlayerService", "Media metadata changed:")
        android.util.Log.d("AudioPlayerService", "  Title: $title")
        android.util.Log.d("AudioPlayerService", "  Artist: $artist")
        android.util.Log.d("AudioPlayerService", "  Has artworkData: $hasArtworkData (${artworkData?.size ?: 0} bytes)")
        android.util.Log.d("AudioPlayerService", "  Has artworkUri: $hasArtworkUri (${mediaMetadata.artworkUri})")

        if (hasArtworkData || hasArtworkUri) {
            android.util.Log.i("AudioPlayerService", "Artwork found! Updating notification...")
        } else {
            android.util.Log.w("AudioPlayerService", "No artwork found in metadata")
        }

        // Update notification to show artwork
        // MediaSession automatically updates from ExoPlayer
        getNotificationManager()?.updateMetadata(getCurrentMetadata(), getEmbeddedArtworkPath())
    }

    /**
     * Starts periodic position checking to detect end of file when duration is incorrect.
     * This is a workaround for cases where ExoPlayer doesn't correctly detect file end
     * due to incorrect duration metadata.
     */
    private fun startPositionCheck() {
        stopPositionCheck() // Stop any existing check

        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
        android.util.Log.i(
            "AudioPlayerService",
            "Starting position check: index=$currentIndex/$totalTracks (actual=${getActualPlaylistSize?.invoke()}, player=${player.mediaItemCount}), isPlaying=${player.isPlaying}, state=${player.playbackState}",
        )

        positionCheckRunnable =
            object : Runnable {
                override fun run() {
                    if (getIsBookCompleted()) {
                        stopPositionCheck()
                        return
                    }

                    val player = getActivePlayer()

                    // Stop check if book is already completed or player is in ENDED state
                    if (player.playbackState == Player.STATE_ENDED) {
                        stopPositionCheck()
                        return
                    }

                    // Don't stop check immediately if isPlaying is false - file might have just ended
                    // and we need to check if position stopped advancing
                    // Only stop if we're not on the last track
                    val currentIndex = player.currentMediaItemIndex
                    // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
                    val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
                    if (!player.isPlaying && currentIndex < totalTracks - 1) {
                        // Not playing and not on last track - stop check
                        stopPositionCheck()
                        return
                    }

                    // Get current state (currentIndex and totalTracks already defined above)
                    val currentPosition = player.currentPosition
                    val duration = player.duration

                    // Log position check for debugging (only on last track to reduce spam)
                    if (currentIndex >= totalTracks - 1) {
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Position check: index=$currentIndex/$totalTracks, position=${currentPosition}ms (${currentPosition / 1000}s), duration=${duration}ms (${duration / 1000}s), isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, state=${player.playbackState}",
                        )
                    }

                    // Only check on the last track
                    if (currentIndex >= totalTracks - 1) {
                        // Check 1: Position reached or exceeded duration (if duration is valid)
                        if (duration != C.TIME_UNSET && duration > 0) {
                            if (currentPosition >= duration) {
                                android.util.Log.i(
                                    "AudioPlayerService",
                                    "Detected end of last track: position reached/exceeded duration (position=$currentPosition, duration=$duration)",
                                )
                                handleBookCompletion(player, currentIndex)
                                stopPositionCheck()
                                return
                            }

                            // Check 2: Position is very close to duration (within threshold)
                            val remaining = duration - currentPosition
                            if (remaining <= endOfFileThresholdMs) {
                                android.util.Log.i(
                                    "AudioPlayerService",
                                    "Detected end of last track by position check: position=$currentPosition, duration=$duration, remaining=$remaining",
                                )
                                handleBookCompletion(player, currentIndex)
                                stopPositionCheck()
                                return
                            }
                        }

                        // Check 3: Position stopped advancing (file ended but position didn't update)
                        // This is the MOST IMPORTANT check - handles cases where duration is incorrect
                        // and file actually ended but ExoPlayer doesn't update position or state
                        // This check works even if duration is wrong!
                        // Note: We check position stopped even if isPlaying is false, because ExoPlayer
                        // may stop playing when file ends, but position might not update
                        if (lastPosition >= 0 && currentPosition == lastPosition) {
                            // Position hasn't changed - file might have ended
                            val currentTime = System.currentTimeMillis()

                            // Record when position first stopped
                            if (positionStoppedStartTime < 0) {
                                positionStoppedStartTime = currentTime
                            }

                            // Check if we were playing recently (within last check) or if position is near duration
                            val wasPlayingRecently = player.isPlaying || player.playWhenReady
                            val isNearEnd =
                                duration != C.TIME_UNSET &&
                                    duration > 0 &&
                                    currentPosition >= duration - (endOfFileThresholdMs * 2)

                            // Calculate how long position has been stopped
                            val stoppedTimeMs = currentTime - positionStoppedStartTime

                            // On last track, if position stopped and we were playing, it's likely the file ended
                            // This works even if duration metadata is wrong!
                            if (wasPlayingRecently || isNearEnd || stoppedTimeMs >= maxPositionStoppedTimeMs) {
                                positionStoppedCount++
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Position not advancing on last track: position=$currentPosition, stopped for $positionStoppedCount checks (${stoppedTimeMs}ms), isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, duration=$duration, nearEnd=$isNearEnd",
                                )

                                // If position stopped for threshold checks OR for max time, consider file ended
                                if (positionStoppedCount >= positionStoppedThreshold ||
                                    stoppedTimeMs >= maxPositionStoppedTimeMs
                                ) {
                                    // Position hasn't changed for multiple checks or max time - file definitely ended
                                    // This works even if duration is incorrect!
                                    android.util.Log.i(
                                        "AudioPlayerService",
                                        "Detected end of last track: position stopped advancing (position=$currentPosition, duration=$duration, stopped for $positionStoppedCount checks/${stoppedTimeMs}ms, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady})",
                                    )
                                    handleBookCompletion(player, currentIndex)
                                    stopPositionCheck()
                                    return
                                }
                            } else {
                                // Not playing and not near end - might be paused, reset counter
                                positionStoppedCount = 0
                                positionStoppedStartTime = -1L
                            }
                        } else if (lastPosition >= 0 && currentPosition != lastPosition) {
                            // Position changed, reset counter
                            if (positionStoppedCount > 0) {
                                android.util.Log.v(
                                    "AudioPlayerService",
                                    "Position resumed advancing: was stopped for $positionStoppedCount checks, new position=$currentPosition",
                                )
                            }
                            positionStoppedCount = 0
                            positionStoppedStartTime = -1L
                        }
                        lastPosition = currentPosition
                    } else {
                        // Not on last track, reset tracking
                        lastPosition = -1L
                        positionStoppedCount = 0
                        positionStoppedStartTime = -1L
                    }

                    // Check 4: Playback stopped but we're still in READY state (file ended prematurely)
                    // This can happen when file is shorter than reported duration
                    if (currentIndex >= totalTracks - 1 &&
                        player.playbackState == Player.STATE_READY &&
                        !player.isPlaying &&
                        !player.playWhenReady
                    ) {
                        // If we're near the end (within threshold) or position stopped, consider it completed
                        val isNearEnd =
                            duration != C.TIME_UNSET &&
                                duration > 0 &&
                                currentPosition >= duration - endOfFileThresholdMs
                        val positionStopped =
                            lastPosition >= 0 &&
                                currentPosition == lastPosition &&
                                positionStoppedCount >= 1

                        if (isNearEnd || positionStopped) {
                            android.util.Log.i(
                                "AudioPlayerService",
                                "Detected end of last track: playback stopped near end (position=$currentPosition, duration=$duration, nearEnd=$isNearEnd, positionStopped=$positionStopped)",
                            )
                            handleBookCompletion(player, currentIndex)
                            stopPositionCheck()
                            return
                        }
                    }

                    // Schedule next check
                    positionCheckHandler.postDelayed(this, positionCheckIntervalMs)
                }
            }

        positionCheckHandler.postDelayed(positionCheckRunnable!!, positionCheckIntervalMs)
    }

    /**
     * Stops periodic position checking.
     */
    private fun stopPositionCheck() {
        positionCheckRunnable?.let {
            positionCheckHandler.removeCallbacks(it)
            positionCheckRunnable = null
        }
        // Reset tracking when stopping
        lastPosition = -1L
        positionStoppedCount = 0
        positionStoppedStartTime = -1L
    }

    /**
     * Handles book completion when detected by position check.
     * This is called when we detect that the file has ended even though ExoPlayer
     * hasn't transitioned to STATE_ENDED (due to incorrect duration metadata).
     */
    private fun handleBookCompletion(
        player: Player,
        currentIndex: Int,
    ) {
        // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
        // This fixes issue where player.mediaItemCount may be incorrect (e.g., 20 instead of 16)
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

        // CRITICAL: If index is 0, invalid, or out of bounds, use saved index or calculate last track
        // This handles case when ExoPlayer reset index or tried to go beyond last track
        val actualIndex =
            if ((currentIndex == 0 || currentIndex < 0 || currentIndex >= totalTracks) && totalTracks > 0) {
                // Index might have been reset or gone out of bounds, check if we're actually on last track
                // by checking if we were just playing and position is near end
                val savedIndex = getLastCompletedTrackIndex?.invoke() ?: -1
                if (savedIndex >= 0 && savedIndex < totalTracks) {
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Index is invalid ($currentIndex), using saved index $savedIndex for book completion",
                    )
                    savedIndex
                } else {
                    // No saved index, but if totalTracks > 0, last track is totalTracks - 1
                    // This happens when last track ended and ExoPlayer tried to go to next (index >= totalTracks)
                    // Only use this if we're sure we're at the end
                    if (player.currentPosition > 0 && player.duration > 0) {
                        val lastTrackIndex = totalTracks - 1
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Using calculated last track index $lastTrackIndex (total=$totalTracks, originalIndex=$currentIndex)",
                        )
                        lastTrackIndex
                    } else {
                        // If we can't determine, use last track index if index is out of bounds
                        // This handles the case when ExoPlayer sets index to >= totalTracks
                        if (currentIndex >= totalTracks) {
                            val lastTrackIndex = totalTracks - 1
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Index out of bounds ($currentIndex >= $totalTracks), using last track index $lastTrackIndex",
                            )
                            lastTrackIndex
                        } else {
                            currentIndex
                        }
                    }
                }
            } else {
                currentIndex
            }

        if (actualIndex >= totalTracks - 1) {
            // Last track finished - book completed
            // Save values BEFORE stopping player (stop() resets index to 0)
            val lastIndex = actualIndex
            val lastPosition = player.currentPosition

            android.util.Log.i(
                "AudioPlayerService",
                "Book completed: last track finished (track $lastIndex of ${totalTracks - 1}, position=${lastPosition}ms, originalIndex=$currentIndex) - detected by position check",
            )

            // Set flag to prevent further playback
            setIsBookCompleted(true)

            // Save last track index so getState() can return correct index even if ExoPlayer resets it
            setLastCompletedTrackIndex?.invoke(lastIndex)
            android.util.Log.d(
                "AudioPlayerService",
                "Saved last completed track index: $lastIndex",
            )

            // Stop playback completely to prevent auto-advance
            // IMPORTANT: Don't call player.stop() - it resets index to 0 and clears playlist
            // Instead, just pause and seek to end of last track
            try {
                // First, pause playback
                player.pause()
                player.playWhenReady = false

                // Seek to end of last track to show completion
                // This preserves the index and shows correct track number
                if (lastIndex >= 0 && lastIndex < totalTracks) {
                    // Seek to the end of the last track (or current position if near end)
                    val seekPosition = if (lastPosition > 0) lastPosition else Long.MAX_VALUE
                    player.seekTo(lastIndex, seekPosition)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Seeked to last track $lastIndex at position $seekPosition to preserve index",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Error handling book completion", e)
                // Fallback to just pausing
                player.playWhenReady = false
            }

            // Save final position
            saveCurrentPosition()

            // Update notification to show completion
            getNotificationManager()?.updateNotification()

            // Send broadcast to UI to show completion message
            val intent =
                Intent("com.jabook.app.jabook.BOOK_COMPLETED").apply {
                    setPackage(context.packageName) // Set package for explicit broadcast
                    putExtra("last_track_index", lastIndex)
                }
            context.sendBroadcast(intent)
        }
    }
}
