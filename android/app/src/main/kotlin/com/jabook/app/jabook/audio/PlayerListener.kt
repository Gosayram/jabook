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
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Player event listener with improved error handling and retry logic.
 *
 * Inspired by lissen-android implementation for better error recovery.
 * Uses onEvents() for efficient consolidated event handling (Media3 1.10+ contract).
 */
internal class PlayerListener(
    private val context: Context,
    private val getActivePlayer: () -> ExoPlayer,
    // getNotificationManager callback removed - MediaSession handles notification updates automatically
    private val getIsBookCompleted: () -> Boolean,
    private val setIsBookCompleted: (Boolean) -> Unit,
    private val getSleepTimerEndOfChapter: () -> Boolean,
    private val getSleepTimerEndOfTrack: () -> Boolean,
    private val cancelSleepTimer: () -> Unit,
    private val sendTimerExpiredEvent: () -> Unit,
    private val markSleepTimerPause: () -> Unit = {},
    private val saveCurrentPosition: () -> Unit,
    private val getEmbeddedArtworkPath: () -> String?,
    private val setEmbeddedArtworkPath: (String?) -> Unit,
    private val getCurrentMetadata: () -> Map<String, String>?,
    private val setLastCompletedTrackIndex: ((Int) -> Unit)? = null,
    private val getLastCompletedTrackIndex: (() -> Int)? = null,
    private val getActualPlaylistSize: (() -> Int)? = null, // Get actual playlist size from filePaths
    private val updateActualTrackIndex: ((Int) -> Unit)? = null, // Callback to update actual track index
    private val isPlaylistLoading: (() -> Boolean)? = null, // Check if playlist is currently loading
    private val updateLastPlayedTimestamp: ((String) -> Unit)? = null, // Callback to update last played timestamp
    private val markBookCompleted: ((String) -> Unit)? = null, // Callback to mark book as completed
    private val getCurrentBookId: (() -> String?)? = null, // Get current book ID
    private val preloadNextTrack: ((Int) -> Unit)? = null, // Callback to preload next track (inspired by Easybook)
    private val optimizeMemoryUsage: ((Int) -> Unit)? = null, // Callback to optimize memory usage (inspired by Easybook)
    private val updateAudioVisualizer: ((Int) -> Unit)? = null, // Callback to update audio visualizer (following Rhythm pattern)
    private val getCrossfadeHandler: (() -> CrossfadeHandler?)? = null, // Callback to get crossfade handler (Phase 6)
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null, // Coroutine scope for debounced operations (inspired by Rhythm)
) : Player.Listener {
    // Book completion tracker (extracted from PlayerListener)
    private val bookCompletionTracker: BookCompletionTracker =
        BookCompletionTracker(
            context = context,
            scope = coroutineScope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            getActivePlayer = { getActivePlayer() },
            getIsBookCompleted = { getIsBookCompleted() },
            setIsBookCompleted = { setIsBookCompleted(it) },
            getActualPlaylistSize = { getActualPlaylistSize?.invoke() ?: getActivePlayer().mediaItemCount },
            getLastCompletedTrackIndex = { getLastCompletedTrackIndex?.invoke() ?: -1 },
            setLastCompletedTrackIndex = { setLastCompletedTrackIndex?.invoke(it) },
            saveCurrentPosition = { saveCurrentPosition() },
            getCurrentBookId = { getCurrentBookId?.invoke() },
            markBookCompleted = markBookCompleted,
        )

    // Error handler (extracted from PlayerListener)
    private val playerErrorHandler: PlayerErrorHandler =
        PlayerErrorHandler(
            getActivePlayer = { getActivePlayer() },
            getActualPlaylistSize = { getActualPlaylistSize?.invoke() ?: getActivePlayer().mediaItemCount },
            getCurrentMetadata = { getCurrentMetadata() },
            getCurrentBookId = { getCurrentBookId?.invoke() },
        )

    /**
     * Active LoudnessNormalizer for ReplayGain application.
     * Injected by PlayerConfigurator when processor chain is created.
     */
    var loudnessNormalizer: LoudnessNormalizer? = null

    // CRITICAL: Deferred for waiting on track switch events
    // This allows PlaylistManager to wait for onMediaItemTransition instead of polling
    @Volatile
    private var pendingTrackSwitchDeferred: CompletableDeferred<Int>? = null
    private var lastHandledTransitionIndex: Int = -1
    private var lastHandledTransitionAtElapsedMs: Long = 0L
    private val transitionDedupWindowMs = 300L

    private val audioFocusDuckingController =
        AudioFocusDuckingController(
            getActivePlayer = getActivePlayer,
            scope = coroutineScope,
            onDuckApplied = {
                saveCurrentPosition()
                LogUtils.d("AudioPlayerService", "Saved position on transient audio focus duck event")
            },
        )

    /**
     * Sets a deferred to be completed when track switch occurs.
     * Used by PlaylistManager to wait for onMediaItemTransition event.
     *
     * @param deferred CompletableDeferred to complete with new track index
     */
    public fun setPendingTrackSwitchDeferred(deferred: CompletableDeferred<Int>) {
        this.pendingTrackSwitchDeferred = deferred
        LogUtils.d(
            "AudioPlayerService",
            "Set pendingTrackSwitchDeferred: waiting for track switch event",
        )
    }

    /**
     * Clears the pending deferred (cancels waiting for track switch).
     */
    public fun clearPendingTrackSwitchDeferred() {
        pendingTrackSwitchDeferred?.cancel()
        pendingTrackSwitchDeferred = null
        LogUtils.d("AudioPlayerService", "Cleared pendingTrackSwitchDeferred")
    }

    private fun handleTrackTransitionEvent(
        currentIndex: Int,
        source: String,
    ) {
        if (currentIndex < 0) {
            return
        }
        if (shouldSkipDuplicateTransition(currentIndex, source)) {
            return
        }
        updateTrackIndexFromTransition(currentIndex, source)
        completePendingTrackSwitchDeferred(currentIndex, source)
    }

    private fun shouldSkipDuplicateTransition(
        currentIndex: Int,
        source: String,
    ): Boolean {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isDuplicate =
            currentIndex == lastHandledTransitionIndex &&
                nowElapsedMs - lastHandledTransitionAtElapsedMs <= transitionDedupWindowMs
        if (isDuplicate) {
            LogUtils.v(
                "AudioPlayerService",
                "Skipping duplicate transition event from $source for index $currentIndex " +
                    "(window=${transitionDedupWindowMs}ms)",
            )
            return true
        }
        lastHandledTransitionIndex = currentIndex
        lastHandledTransitionAtElapsedMs = nowElapsedMs
        return false
    }

    private fun updateTrackIndexFromTransition(
        currentIndex: Int,
        source: String,
    ) {
        // CRITICAL: Don't update actualTrackIndex to 0 during playlist loading.
        // ExoPlayer may briefly report index 0 while items are still attaching.
        val isLoading = isPlaylistLoading?.invoke() ?: false
        if (!isLoading || currentIndex != 0) {
            updateActualTrackIndex?.invoke(currentIndex)
            LogUtils.v(
                "AudioPlayerService",
                "Updated actualTrackIndex to $currentIndex from $source (isLoading=$isLoading)",
            )
        } else {
            LogUtils.v(
                "AudioPlayerService",
                "Skipped updating actualTrackIndex to 0 during playlist loading " +
                    "(source=$source, isLoading=$isLoading, currentIndex=$currentIndex)",
            )
        }
    }

    private fun completePendingTrackSwitchDeferred(
        currentIndex: Int,
        source: String,
    ) {
        val deferred = pendingTrackSwitchDeferred ?: return
        try {
            if (deferred.isActive) {
                deferred.complete(currentIndex)
                LogUtils.d(
                    "AudioPlayerService",
                    "Completed pendingTrackSwitchDeferred with index $currentIndex from $source",
                )
            } else {
                LogUtils.v(
                    "AudioPlayerService",
                    "Pending deferred already completed/cancelled before $source (index=$currentIndex)",
                )
            }
        } catch (e: Exception) {
            LogUtils.w(
                "AudioPlayerService",
                "Failed to complete pendingTrackSwitchDeferred from $source: ${e.message}",
            )
        } finally {
            pendingTrackSwitchDeferred = null
        }
    }

    // Use onEvents() for more efficient event handling (inspired by lissen-android)
    // This allows handling multiple events in one callback for better performance
    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        LogUtils.v("AudioPlayerService", "onEvents: $events")

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
            LogUtils.i(
                "AudioPlayerService",
                "EVENT_PLAYBACK_STATE_CHANGED: $stateName, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, mediaItemCount=${player.mediaItemCount}",
            )

            // Update notification when state changes
            // MediaSession automatically updates from ExoPlayer state

            // Update widget when playback state changes
            com.jabook.app.jabook.widget.PlayerWidgetProvider
                .requestUpdate(context)

            // Reset retry and skip counts on successful playback
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                playerErrorHandler.resetCounts()
            }

            // Stop position check when playback state changes to ENDED
            if (playbackState == Player.STATE_ENDED) {
                stopPositionCheck()
            }
            if (PositionTrackingResetPolicy.shouldResetOnPlaybackStateChanged(playbackState)) {
                resetPositionTrackingState()
            }

            // Handle book completion - when last track ends
            if (playbackState == Player.STATE_ENDED) {
                val currentIndex = player.currentMediaItemIndex
                val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

                // Check sleep timer "end of chapter" mode (inspired by EasyBook)
                if (getSleepTimerEndOfChapter() || getSleepTimerEndOfTrack()) {
                    LogUtils.d("AudioPlayerService", "Sleep timer expired (end of chapter), pausing playback")
                    markSleepTimerPause()
                    saveCurrentPosition()
                    player.playWhenReady = false
                    cancelSleepTimer()
                    sendTimerExpiredEvent()
                }

                if (!handleBookCompletion(player, currentIndex, source = "STATE_ENDED")) {
                    // Not last track - ExoPlayer will auto-advance (normal behavior)
                    LogUtils.d(
                        "AudioPlayerService",
                        "Track ended, will auto-advance to next (track $currentIndex of ${totalTracks - 1})",
                    )
                }
            }

            // Handle errors
            if (playbackState == Player.STATE_IDLE) {
                val error = player.playerError
                if (error != null) {
                    LogUtils.e("AudioPlayerService", "Playback error: ${error.message}", error)
                    playerErrorHandler.logErrorContext(error)
                    playerErrorHandler.handlePlayerError(error)
                }
            }
        }

        // Handle playWhenReady changes (important for AudioFocus debugging)
        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            // If book is completed and user manually starts playback, reset the completion flag
            // This allows users to re-play completed books while preventing auto-resume from system
            if (getIsBookCompleted() && player.playWhenReady) {
                // User explicitly started playback - reset completion flag to allow it
                LogUtils.i(
                    "AudioPlayerService",
                    "User manually started playback after book completion, resetting completion flag",
                )
                setIsBookCompleted(false)
                setLastCompletedTrackIndex?.invoke(-1) // Clear saved index
                // Continue with playback instead of blocking
            }

            LogUtils.i(
                "AudioPlayerService",
                "EVENT_PLAY_WHEN_READY_CHANGED: playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, playbackState=${player.playbackState}, mediaItemCount=${player.mediaItemCount}",
            )
            // Match lissen-android: just log, don't interfere with ExoPlayer's AudioFocus handling
            // Post notification update to main thread to ensure player state is fully updated
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
            LogUtils.i(
                "AudioPlayerService",
                "EVENT_IS_PLAYING_CHANGED: isPlaying=$isPlaying, playWhenReady=${player.playWhenReady}, playbackState=$stateName, mediaItemCount=${player.mediaItemCount}",
            )

            // Update widget when playing state changes
            com.jabook.app.jabook.widget.PlayerWidgetProvider
                .requestUpdate(context)

            // CRITICAL: Save position when playback stops for any reason
            // This ensures position is saved in all scenarios (pause, system events, etc.)
            if (!isPlaying && !player.playWhenReady && playbackState == Player.STATE_READY) {
                // Playback stopped but player is still ready (not ended)
                // Save position to ensure it's preserved
                LogUtils.d("AudioPlayerService", "Playback stopped, saving position")
                saveCurrentPosition()
                resetPositionTrackingState()
            }

            // Save position when playback starts (critical event)
            // This ensures position is saved immediately when user resumes playback
            // Save position when playback starts (critical event)
            // This ensures position is saved immediately when user resumes playback
            if (isPlaying && playbackState == Player.STATE_READY) {
                LogUtils.v("AudioPlayerService", "Playback started, position will be saved periodically")
                // Update last played timestamp for activity sorting
                getCurrentBookId?.invoke()?.let { bookId ->
                    updateLastPlayedTimestamp?.invoke(bookId)
                }
            }

            // Sleep timer is automatically managed by SuspendableCountDownTimer (pause/resume)

            // Start/stop position checking for end-of-file detection when duration is incorrect
            // Only check when playing and not completed
            val currentIndex = player.currentMediaItemIndex
            // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
            val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
            val isLastTrack = currentIndex >= totalTracks - 1

            // Always start position check on last track if playing (even if not READY yet)
            // This ensures we catch the end of file even if state changes
            if (isLastTrack && isPlaying && !getIsBookCompleted()) {
                LogUtils.d(
                    "AudioPlayerService",
                    "EVENT_IS_PLAYING_CHANGED: starting position check on last track (index=$currentIndex/$totalTracks, state=$playbackState)",
                )
                startPositionCheck()
            } else if (isPlaying && !getIsBookCompleted() && playbackState == Player.STATE_READY) {
                // Start check on any track when playing and ready
                LogUtils.v(
                    "AudioPlayerService",
                    "EVENT_IS_PLAYING_CHANGED: starting position check (index=$currentIndex/$totalTracks)",
                )
                startPositionCheck()
            } else {
                if (isLastTrack) {
                    LogUtils.d(
                        "AudioPlayerService",
                        "EVENT_IS_PLAYING_CHANGED: NOT starting position check (isPlaying=$isPlaying, completed=${getIsBookCompleted()}, state=$playbackState, index=$currentIndex/$totalTracks)",
                    )
                }
                // Don't stop check on last track if we're not playing - file might have just ended
                if (!isLastTrack) {
                    stopPositionCheck()
                }
            }

            // Start/Stop Crossfade Monitoring
            if (isPlaying && playbackState == Player.STATE_READY) {
                getCrossfadeHandler?.invoke()?.startMonitoring()
            } else {
                getCrossfadeHandler?.invoke()?.stopMonitoring()
            }

            // Don't reset playWhenReady automatically - let ExoPlayer handle AudioFocus
            // The previous check was too aggressive and was preventing playback from starting

            // Post notification update to main thread to ensure player state is fully updated
        }

        // Handle media item transitions
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            val currentIndex = player.currentMediaItemIndex
            LogUtils.v(
                "AudioPlayerService",
                "EVENT_MEDIA_ITEM_TRANSITION received for index=$currentIndex; state sync handled by onMediaItemTransition()",
            )

            // Save position of previous track before transitioning
            // This ensures position is saved even during rapid track changes
            // playbackPositionSaver?.savePosition("track_changed")

            // Track changed - restart position check for new track
            stopPositionCheck()
            // Reset position tracking for new track
            resetPositionTrackingState()

            // Track changed - update notification to show new track's embedded artwork
            // MediaSession automatically updates from ExoPlayer

            // Update widget when track changes
            com.jabook.app.jabook.widget.PlayerWidgetProvider
                .requestUpdate(context)

            // Log track transition for debugging (inspired by lissen-android logging)
            // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
            val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

            // Preload next track for smooth transition (inspired by Easybook)
            if (currentIndex >= 0 && currentIndex < totalTracks - 1) {
                val nextIndex = currentIndex + 1
                // Check if next track is already loaded
                // getMediaItemAt throws IndexOutOfBoundsException if index is invalid, not null
                val nextTrackLoaded =
                    try {
                        player.getMediaItemAt(nextIndex)
                        true // Track exists if no exception thrown
                    } catch (e: IndexOutOfBoundsException) {
                        false // Track doesn't exist
                    } catch (e: Exception) {
                        false // Other error, assume not loaded
                    }

                if (!nextTrackLoaded) {
                    LogUtils.d(
                        "AudioPlayerService",
                        "Next track $nextIndex not loaded yet, preloading for smooth transition",
                    )
                    preloadNextTrack?.invoke(nextIndex)
                } else {
                    LogUtils.v(
                        "AudioPlayerService",
                        "Next track $nextIndex already loaded, no preload needed",
                    )
                }
            }

            // Optimize memory usage for large playlists (inspired by Easybook)
            // Only optimize if playlist is large (> 50 tracks) to avoid unnecessary operations
            if (totalTracks > 50) {
                optimizeMemoryUsage?.invoke(currentIndex)
            }

            // Restart position check if we're playing and on the last track
            if (player.isPlaying &&
                !getIsBookCompleted() &&
                player.playbackState == Player.STATE_READY &&
                currentIndex >= totalTracks - 1
            ) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Media item transition to last track: starting position check (index=$currentIndex/$totalTracks)",
                )
                startPositionCheck()
            }
            val currentItem = player.currentMediaItem
            val title = currentItem?.mediaMetadata?.title?.toString() ?: "Unknown"
            LogUtils.d("AudioPlayerService", "Media item transition:")
            LogUtils.d("AudioPlayerService", "  - Index: $currentIndex")
            LogUtils.d("AudioPlayerService", "  - Title: $title")
            LogUtils.d("AudioPlayerService", "  - Total items: ${player.mediaItemCount}")

            // Check sleep timer "end of chapter" mode (inspired by EasyBook)
            // Trigger when track transitions automatically (not manual seek)
            // Note: We need to check the reason, but onEvents doesn't provide it
            // So we check in onMediaItemTransition override instead
            // This is a fallback check

            // Reset retry count on track change (new track might work even if previous failed)
            playerErrorHandler.resetCounts()

            // Restart position check if playing
            if (player.isPlaying && !getIsBookCompleted() && player.playbackState == Player.STATE_READY) {
                startPositionCheck()
            }
        }

        // Handle playback parameters changes (speed, pitch, etc.)
        if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
            val params = player.playbackParameters
            LogUtils.d(
                "AudioPlayerService",
                "Playback parameters changed: speed=${params.speed}, pitch=${params.pitch}",
            )
        }

        // Handle repeat mode changes
        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
            LogUtils.d("AudioPlayerService", "Repeat mode changed: ${player.repeatMode}")
        }

        // Handle shuffle mode changes
        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            LogUtils.d("AudioPlayerService", "Shuffle mode changed: ${player.shuffleModeEnabled}")
        }
    }

    /**
     * Handles metadata updates to extract ReplayGain info.
     */
    override fun onMetadata(metadata: Metadata) {
        val normalizer = loudnessNormalizer ?: return

        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            // Handle ID3 TXXX frames (UserDefinedTextInformationFrame)
            // Note: Media3 may map these to different classes depending on implementation
            // Using reflection or string checking if direct class access is difficult

            // Check for ReplayGain tags
            // Common keys: REPLAYGAIN_TRACK_GAIN, R128_TRACK_GAIN

            try {
                // Check for TextInformationFrame or similar
                // Since Media3 implementation details vary, we check toString representation or specific fields if accessible
                // Common ID3v2: TXXX frame with description "REPLAYGAIN_TRACK_GAIN"

                // Optimized: Check simple string representation first to avoid heavy reflection
                val entryString = entry.toString()

                if (entryString.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                    // Extract value, e.g., "-5.2 dB"
                    parseAndSetReplayGain(entry, normalizer)
                }
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error processing metadata entry", e)
            }
        }
    }

    private fun parseAndSetReplayGain(
        entry: Metadata.Entry,
        normalizer: LoudnessNormalizer,
    ) {
        try {
            // Retrieve value from entry
            // This depends on the exact class, but usually it's in a 'value' or 'values' field
            // For now, let's try to parse the string representation which usually contains the value
            // Example: "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-5.20 dB"

            val text = entry.toString()
            val valueMatch = Regex("value=([\\-\\+\\d\\.]+)\\s*dB?", RegexOption.IGNORE_CASE).find(text)

            if (valueMatch != null) {
                val dbString = valueMatch.groupValues[1]
                val db = dbString.toFloatOrNull()
                if (db != null) {
                    LogUtils.i("AudioPlayerService", "Found ReplayGain: ${db}dB")
                    normalizer.setReplayGain(db)
                }
            }
        } catch (e: Exception) {
            LogUtils.w("AudioPlayerService", "Failed to parse ReplayGain: ${e.message}")
        }
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
        LogUtils.i(
            "AudioPlayerService",
            "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reasonText",
        )

        // CRITICAL: Save position when playback stops for ANY reason
        // This ensures position is saved in all scenarios:
        // - User pauses and closes app
        // - Device battery dies
        // - Phone call interrupts playback
        // - Other system events
        if (!playWhenReady) {
            LogUtils.d("AudioPlayerService", "Playback paused (reason=$reasonText), saving position")
            saveCurrentPosition()
            if (PositionTrackingResetPolicy.shouldResetOnPlayWhenReadyChanged(playWhenReady)) {
                resetPositionTrackingState()
            }

            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                    LogUtils.i("AudioPlayerService", "Audio focus lost, position saved")
                }
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                    LogUtils.i(
                        "AudioPlayerService",
                        "Audio becoming noisy (e.g., headphones unplugged), position saved",
                    )
                }
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> {
                    LogUtils.d("AudioPlayerService", "User paused playback, position saved")
                }
                else -> {
                    LogUtils.d("AudioPlayerService", "Playback paused for reason: $reasonText, position saved")
                }
            }
        }
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        audioFocusDuckingController.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        playerErrorHandler.logErrorContext(error)
        playerErrorHandler.handlePlayerError(error)
    }

    // onMediaItemTransition is now handled in onEvents() for better performance
    // Keeping this for backward compatibility and sleep timer "end of chapter" handling
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // This is also handled in onEvents, but kept for explicit handling if needed
        val currentIndex = getActivePlayer().currentMediaItemIndex
        val reasonName =
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                else -> "UNKNOWN($reason)"
            }
        val mediaId = mediaItem?.mediaId ?: "unknown"
        LogUtils.i(
            "AudioPlayerService",
            "🎵 Track switch: index=$currentIndex, reason=$reasonName, mediaId=$mediaId",
        )

        handleTrackTransitionEvent(currentIndex, source = "onMediaItemTransition")

        // Check sleep timer "end of chapter" mode (inspired by EasyBook)
        // Trigger when track transitions automatically (not manual seek)
        if ((getSleepTimerEndOfChapter() || getSleepTimerEndOfTrack()) &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        ) {
            LogUtils.d(
                "AudioPlayerService",
                "Sleep timer expired (end of chapter on auto transition), pausing playback",
            )
            markSleepTimerPause()
            saveCurrentPosition()
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

        // Check track availability for automatic transitions (inspired by lissen-android)
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            if (!TrackAvailabilityChecker.isTrackAvailable(player, currentIndex)) {
                LogUtils.w(
                    "AudioPlayerService",
                    "Track $currentIndex is not available, searching for next available track",
                )

                val direction =
                    when {
                        currentIndex > previousIndex ||
                            (currentIndex == 0 && previousIndex == player.mediaItemCount - 1)
                        -> TrackAvailabilityChecker.Direction.FORWARD
                        else -> TrackAvailabilityChecker.Direction.BACKWARD
                    }

                val nextAvailableIndex =
                    TrackAvailabilityChecker.findAvailableTrackIndex(
                        player = player,
                        currentIndex = currentIndex,
                        direction = direction,
                    )

                if (nextAvailableIndex != null && nextAvailableIndex != currentIndex) {
                    player.seekTo(nextAvailableIndex, 0L)
                    LogUtils.d("AudioPlayerService", "Switched to available track: $nextAvailableIndex")
                } else {
                    // No available tracks found, pause playback
                    LogUtils.w("AudioPlayerService", "No available tracks found, pausing playback")
                    player.playWhenReady = false
                }
                return // Skip further processing for this discontinuity
            }
        }

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
                LogUtils.i(
                    "AudioPlayerService",
                    "Manual track switch detected after book completion: $previousIndex -> $currentIndex, resetting completion flag",
                )
                setIsBookCompleted(false)
                setLastCompletedTrackIndex?.invoke(-1) // Clear saved index
            } else if (isAutoTransition) {
                // Block automatic transitions after completion
                LogUtils.w(
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
            LogUtils.d(
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
                LogUtils.i(
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
                        LogUtils.d(
                            "AudioPlayerService",
                            "Prevented invalid transition, seeked back to track $previousIndex at position $lastPosition",
                        )
                    } catch (e: Exception) {
                        LogUtils.e("AudioPlayerService", "Error preventing invalid transition", e)
                    }
                }
                return
            }

            // Stop position check on track change, will restart if playing
            stopPositionCheck()

            // Reset retry count on track change
            playerErrorHandler.resetCounts()

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
                            LogUtils.w(
                                "AudioPlayerService",
                                "Current track file not accessible: ${uri.path}, trying to skip",
                            )
                            // Try to skip to next available track
                            playerErrorHandler.skipToNextAvailableTrack(currentIndex, previousIndex)
                        }
                    }
                }
            }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        // Metadata (including embedded artwork) was extracted from audio file
        // Media3: artworkData and artworkUri are NOT deprecated - use them directly
        // Inspired by lissen-android: prefer artworkUri over artworkData for better performance
        val title = mediaMetadata.title?.toString() ?: "Unknown"
        val artist = mediaMetadata.artist?.toString() ?: "Unknown"
        val album = mediaMetadata.albumTitle?.toString()

        // Log metadata extraction for debugging
        LogUtils.d("AudioPlayerService", "Metadata changed: title=$title, artist=$artist, album=$album")

        // Check if artwork is available (prefer URI, then data)
        val artworkUri = mediaMetadata.artworkUri
        val artworkData = mediaMetadata.artworkData
        val hasArtworkData = artworkData != null && artworkData.isNotEmpty()
        val hasArtworkUri = artworkUri != null

        if (artworkUri != null) {
            LogUtils.d("AudioPlayerService", "Artwork URI available: $artworkUri")
            // Clear embedded artwork path if external URI is available
            setEmbeddedArtworkPath(null)
        } else if (hasArtworkData) {
            LogUtils.d("AudioPlayerService", "Embedded artwork data available: ${artworkData.size} bytes")
            // Save embedded artwork to temporary file for Flutter access
            try {
                val cacheDir = context.cacheDir
                val artworkFile = File(cacheDir, "embedded_artwork_${System.currentTimeMillis()}.jpg")
                artworkFile.outputStream().use { it.write(artworkData) }
                setEmbeddedArtworkPath(artworkFile.absolutePath)
                LogUtils.i("AudioPlayerService", "Saved embedded artwork to: ${artworkFile.absolutePath}")
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Failed to save embedded artwork", e)
                setEmbeddedArtworkPath(null)
            }
        } else {
            LogUtils.d("AudioPlayerService", "No artwork available")
            setEmbeddedArtworkPath(null)
        }

        LogUtils.d("AudioPlayerService", "Media metadata changed:")
        LogUtils.d("AudioPlayerService", "  Title: $title")
        LogUtils.d("AudioPlayerService", "  Artist: $artist")
        LogUtils.d("AudioPlayerService", "  Has artworkData: $hasArtworkData (${artworkData?.size ?: 0} bytes)")
        LogUtils.d("AudioPlayerService", "  Has artworkUri: $hasArtworkUri (${mediaMetadata.artworkUri})")

        if (hasArtworkData || hasArtworkUri) {
            LogUtils.i("AudioPlayerService", "Artwork found! Updating notification...")
        } else {
            LogUtils.w("AudioPlayerService", "No artwork found in metadata")
        }

        // Update notification to show artwork
        // MediaSession automatically updates from ExoPlayer - manual update removed
        // getNotificationManager()?.updateMetadata(getCurrentMetadata(), getEmbeddedArtworkPath())
    }

    private fun startPositionCheck() = bookCompletionTracker.startPositionCheck()

    private fun stopPositionCheck() = bookCompletionTracker.stopPositionCheck()

    private fun handleBookCompletion(
        player: Player,
        currentIndex: Int,
        source: String = "unknown",
    ): Boolean = bookCompletionTracker.handleBookCompletion(player, currentIndex, source)

    private fun resetPositionTrackingState() { /* Handled by BookCompletionTracker */ }

    /**
     * Handles audio session ID changes (following Rhythm pattern).
     * Reinitializes audio visualizer when audio session changes.
     */
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        LogUtils.d("AudioPlayerService", "Audio session ID changed: $audioSessionId")

        // Update audio visualizer with new session ID (following Rhythm pattern)
        if (audioSessionId != 0) {
            updateAudioVisualizer?.invoke(audioSessionId)
            LogUtils.d("AudioPlayerService", "AudioVisualizerManager updated with new session ID: $audioSessionId")
        } else {
            LogUtils.w("AudioPlayerService", "Invalid audio session ID (0), skipping visualizer update")
        }
    }

    fun release() {
        stopPositionCheck()
        audioFocusDuckingController.release()
    }
}
