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
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.ErrorHandler
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    // getNotificationManager callback removed - MediaSession handles notification updates automatically
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
    private val updateActualTrackIndex: ((Int) -> Unit)? = null, // Callback to update actual track index
    private val isPlaylistLoading: (() -> Boolean)? = null, // Check if playlist is currently loading
    private val storeCurrentMediaItem: (() -> Unit)? = null, // Callback to store current media item for playback resumption
    private val updateLastPlayedTimestamp: ((String) -> Unit)? = null, // Callback to update last played timestamp
    private val markBookCompleted: ((String) -> Unit)? = null, // Callback to mark book as completed
    private val getCurrentBookId: (() -> String?)? = null, // Get current book ID
    private val preloadNextTrack: ((Int) -> Unit)? = null, // Callback to preload next track (inspired by Easybook)
    private val optimizeMemoryUsage: ((Int) -> Unit)? = null, // Callback to optimize memory usage (inspired by Easybook)
    private val updateAudioVisualizer: ((Int) -> Unit)? = null, // Callback to update audio visualizer (following Rhythm pattern)
    private val getCrossfadeHandler: (() -> CrossfadeHandler?)? = null, // Callback to get crossfade handler (Phase 6)
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null, // Coroutine scope for debounced operations (inspired by Rhythm)
) : Player.Listener {
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L // 2 seconds

    // Error skipping mechanism (Auxio pattern)
    private var skipCount = 0
    private val maxSkips = 5 // Prevent infinite skip loops for bad playlists

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

    // Debounce mechanism for notification updates (inspired by Rhythm)
    // Prevents multiple rapid updates that can cause UI jank
    private var notificationUpdateJob: kotlinx.coroutines.Job? = null
    private val notificationDebounceMs = 150L

    /**
     * Schedules a debounced notification update.
     * Cancels any pending update and schedules a new one after delay.
     * Inspired by Rhythm's scheduleCustomLayoutUpdate().
     * NOTE: Now a no-op since MediaSession handles notification updates automatically.
     */
    private fun scheduleNotificationUpdate() {
        // No-op: MediaSession + MediaLibraryService handle notification updates automatically
        // via ExoPlayer state changes. Manual updates are no longer needed.
    }

    // Import kotlinx.coroutines.launch extension removed - causing conflict with standard library

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
        storeCurrentMediaItem?.invoke()
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

    // Coroutine-based position check (inspired by Rhythm's crossfade monitoring)
    // Uses Job instead of Handler for better performance and cancellation
    private var positionCheckJob: kotlinx.coroutines.Job? = null
    private var lastPosition: Long = -1L // Track last position to detect when playback stops advancing
    private var positionStoppedCount: Int = 0 // Count how many times position hasn't changed
    private var positionStoppedStartTime: Long = -1L // When position first stopped advancing
    private val positionCheckIntervalMs = 1000L // Check every second
    private val minEndOfFileThresholdMs = 500L
    private val maxEndOfFileThresholdMs = 5000L
    private val endOfFileThresholdPercent = 0.01
    private val positionStoppedThreshold = 2 // Consider file ended if position hasn't changed for 2 checks (2 seconds)
    private val maxPositionStoppedTimeMs = 3000L // Maximum time position can be stopped before considering file ended (3 seconds)

    private fun resetPositionTrackingState() {
        lastPosition = -1L
        positionStoppedCount = 0
        positionStoppedStartTime = -1L
    }

    private fun calculateEndOfFileThresholdMs(durationMs: Long): Long {
        if (durationMs == C.TIME_UNSET || durationMs <= 0) {
            return minEndOfFileThresholdMs
        }
        val proportionalThreshold = (durationMs * endOfFileThresholdPercent).toLong()
        return proportionalThreshold.coerceIn(minEndOfFileThresholdMs, maxEndOfFileThresholdMs)
    }

    // Use onEvents() for more efficient event handling (inspired by lissen-android)
    // This allows handling multiple events in one callback for better performance
    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        // CRITICAL: Use Log.e to bypass LogUtils filtering for debugging
        Log.e("JABOOK_LISTENER", "=== onEvents START ===")
        Log.e("JABOOK_LISTENER", "Events: $events")
        Log.e(
            "JABOOK_LISTENER",
            "Player state: playbackState=${player.playbackState}, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, mediaItemCount=${player.mediaItemCount}",
        )

        // Log all events for debugging
        LogUtils.d("AudioPlayerService", "onEvents called: $events")

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
            scheduleNotificationUpdate()

            // Update widget when playback state changes
            com.jabook.app.jabook.widget.PlayerWidgetProvider
                .requestUpdate(context)

            // Reset retry and skip counts on successful playback
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                retryCount = 0
                skipCount = 0
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
                    LogUtils.d("AudioPlayerService", "Sleep timer expired (end of chapter), pausing playback")
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
                            LogUtils.d(
                                "AudioPlayerService",
                                "STATE_ENDED: Index is invalid ($currentIndex), using saved index $savedIndex",
                            )
                            savedIndex
                        } else {
                            // If no saved index, check if we should use last track
                            // This happens when last track ended and ExoPlayer tried to go to next (index >= totalTracks)
                            val lastTrackIndex = totalTracks - 1
                            LogUtils.d(
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

                    LogUtils.i(
                        "AudioPlayerService",
                        "Book completed: last track finished (track $lastIndex of ${totalTracks - 1}, position=${lastPosition}ms)",
                    )

                    // Set flag to prevent further playback
                    setIsBookCompleted(true)

                    // Mark book as completed for activity sorting
                    getCurrentBookId?.invoke()?.let { bookId ->
                        markBookCompleted?.invoke(bookId)
                    }

                    // Save last track index so getState() can return correct index even if ExoPlayer resets it
                    setLastCompletedTrackIndex?.invoke(lastIndex)
                    LogUtils.d(
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
                            LogUtils.d(
                                "AudioPlayerService",
                                "Seeked to last track $lastIndex at position $seekPosition to preserve index",
                            )
                        }
                    } catch (e: Exception) {
                        LogUtils.e("AudioPlayerService", "Error handling book completion", e)
                        // Fallback to just pausing
                        player.playWhenReady = false
                    }

                    // Save final position
                    saveCurrentPosition()

                    // Update notification to show completion
                    scheduleNotificationUpdate()

                    // Send broadcast to UI to show completion message
                    val intent =
                        Intent("com.jabook.app.jabook.BOOK_COMPLETED").apply {
                            setPackage(context.packageName) // Set package for explicit broadcast
                            putExtra("last_track_index", lastIndex)
                        }
                    context.sendBroadcast(intent)
                } else {
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
                    handlePlayerError(error)
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
            scheduleNotificationUpdate()
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
            scheduleNotificationUpdate()
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
            scheduleNotificationUpdate()

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
            retryCount = 0

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

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // This is also handled in onEvents, but kept for explicit handling
        scheduleNotificationUpdate()
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

            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                    LogUtils.i("AudioPlayerService", "Audio focus lost, position saved")
                }
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                    LogUtils.i("AudioPlayerService", "Audio becoming noisy (e.g., headphones unplugged), position saved")
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

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        val mediaItem = player.currentMediaItem
        val mediaId = mediaItem?.mediaId ?: "unknown"
        val metadata = getCurrentMetadata.invoke()

        // Enhanced error logging with context (inspired by Easybook)
        val bookId = getCurrentBookId?.invoke() ?: "unknown"
        val bookName = metadata?.get("title") ?: "unknown"
        val chapterUrl = mediaId
        val chapterIdx = currentIndex

        LogUtils.e(
            "AudioPlayerService",
            "❌ Playback error: track=$currentIndex, mediaId=$mediaId, code=${error.errorCode}, message=${error.message}",
            error,
        )
        LogUtils.e(
            "AudioPlayerService",
            "❌ Error context: bookId=$bookId, bookName=$bookName, chapterIdx=$chapterIdx, chapterUrl=$chapterUrl",
        )

        // Log HTTP-specific error details (inspired by Easybook)
        val cause = error.cause
        if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            LogUtils.e(
                "AudioPlayerService",
                "❌ HTTP error: responseCode=${cause.responseCode}, responseMessage=${cause.responseMessage}",
            )
            if (!cause.headerFields.isEmpty()) {
                LogUtils.e(
                    "AudioPlayerService",
                    "❌ HTTP headers: ${cause.headerFields}",
                )
            }
        } else if (cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException) {
            LogUtils.e(
                "AudioPlayerService",
                "❌ HTTP data source error: type=${cause.type}",
            )
        } else if (cause is java.io.IOException) {
            LogUtils.e(
                "AudioPlayerService",
                "❌ IO error: ${cause.message}",
            )
        }

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
                        LogUtils.w(
                            "AudioPlayerService",
                            "Network connection failed, retrying ($retryCount/$maxRetries)...",
                        )

                        // Retry after delay with exponential backoff
                        val backoffDelay = retryDelayMs * retryCount
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val player = getActivePlayer()
                            player.prepare()
                            // Following RiMusic pattern: set playWhenReady after prepare to resume playback
                            player.playWhenReady = true
                            LogUtils.d(
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
                        LogUtils.w(
                            "AudioPlayerService",
                            "Network timeout, retrying ($retryCount/$maxRetries)...",
                        )
                        val backoffDelay = retryDelayMs * retryCount
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val player = getActivePlayer()
                            player.prepare()
                            // Following RiMusic pattern: set playWhenReady after prepare to resume playback
                            player.playWhenReady = true
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
                    if (attemptSkipOnError()) {
                        "File not found, skipping to next track..."
                    } else {
                        "File not found: Audio file is missing or has been moved."
                    }
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                    if (attemptSkipOnError()) {
                        "Permission denied, skipping..."
                    } else {
                        "Permission denied: Cannot access audio file."
                    }
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                -> {
                    if (attemptSkipOnError()) {
                        "Format error, skipping..."
                    } else {
                        "Format error: Audio file is corrupted or in an unsupported format."
                    }
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                -> {
                    if (attemptSkipOnError()) {
                        "Decoder error, skipping..."
                    } else {
                        "Decoder error: Unable to decode audio."
                    }
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> {
                    if (attemptSkipOnError()) {
                        "Audio track error, skipping..."
                    } else {
                        "Audio error: Unable to initialize audio playback."
                    }
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                    if (attemptSkipOnError()) {
                        "Audio write error, skipping..."
                    } else {
                        "Audio error: Failed to write audio data."
                    }
                }
                else -> {
                    val errorMessage = error.message ?: "Unknown error"
                    "Playback error: $errorMessage (code: $errorCode)"
                }
            }

        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
        val metadata = getCurrentMetadata.invoke()
        val bookId = getCurrentBookId?.invoke() ?: "unknown"
        val bookName = metadata?.get("title") ?: "unknown"

        LogUtils.e(
            "AudioPlayerService",
            "❌ Playback error (user-friendly): $userFriendlyMessage (track=$currentIndex/$totalTracks, retry=$retryCount/$maxRetries, bookId=$bookId, bookName=$bookName)",
        )
        scheduleNotificationUpdate()

        // Store error for retrieval via MethodChannel if needed
        // Error will be automatically propagated through state stream
    }

    /**
     * Attempts to skip to the next track if an error occurs.
     * Returns true if skip was initiated, false if max skips reached or cannot skip.
     */
    private fun attemptSkipOnError(): Boolean {
        if (skipCount < maxSkips) {
            skipCount++
            LogUtils.w(
                "AudioPlayerService",
                "Attempting to skip track due to error (skip $skipCount/$maxSkips)",
            )
            handleFileNotFound() // Reuse existing skip logic
            return true
        } else {
            LogUtils.e(
                "AudioPlayerService",
                "Max skips reached ($maxSkips), stopping playback to prevent loop.",
            )
            return false
        }
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
            LogUtils.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
            return
        }

        // Don't advance if we're at the last track
        if (currentIndex >= totalTracks - 1) {
            LogUtils.w("AudioPlayerService", "Last track, cannot skip forward")
            player.playWhenReady = false
            return
        }

        // Try to skip to next track (not using modulo to prevent circular navigation)
        val nextIndex = currentIndex + 1
        if (nextIndex < totalTracks) {
            LogUtils.w(
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
                LogUtils.e("AudioPlayerService", "Failed to skip to next track", e)
                // Don't rethrow - log and continue
            }
        } else {
            // No more tracks available, pause playback
            LogUtils.w("AudioPlayerService", "No more tracks available, pausing playback")
            try {
                player.playWhenReady = false
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Failed to pause playback", e)
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
        if (getSleepTimerEndOfChapter() && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            LogUtils.d(
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
                            LogUtils.w(
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
            LogUtils.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
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
                            LogUtils.w("AudioPlayerService", "Reached last track, cannot skip forward")
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
                    LogUtils.d("AudioPlayerService", "Found available track at index $nextIndex, seeking to it")
                    try {
                        player.seekTo(nextIndex, 0L)
                        // Restore playWhenReady if was playing
                        if (player.playWhenReady) {
                            player.playWhenReady = true
                        }
                        return
                    } catch (e: Exception) {
                        LogUtils.e("AudioPlayerService", "Failed to seek to available track", e)
                    }
                }
            }

            attempts++
        }

        // No available tracks found, pause playback
        LogUtils.w("AudioPlayerService", "No available tracks found, pausing playback")
        try {
            player.playWhenReady = false
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to pause playback", e)
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
        LogUtils.i(
            "AudioPlayerService",
            "Starting position check: index=$currentIndex/$totalTracks (actual=${getActualPlaylistSize?.invoke()}, player=${player.mediaItemCount}), isPlaying=${player.isPlaying}, state=${player.playbackState}",
        )

        // Use coroutine instead of Handler for better performance (inspired by Rhythm)
        val scope = coroutineScope ?: return
        positionCheckJob =
            scope.launch {
                while (coroutineContext.isActive) {
                    if (getIsBookCompleted()) {
                        break
                    }

                    val player = getActivePlayer()

                    // Stop check if book is already completed or player is in ENDED state
                    if (player.playbackState == Player.STATE_ENDED) {
                        break
                    }

                    // Don't stop check immediately if isPlaying is false - file might have just ended
                    // and we need to check if position stopped advancing
                    // Only stop if we're not on the last track
                    val currentIndex = player.currentMediaItemIndex
                    // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
                    val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
                    if (!player.isPlaying && currentIndex < totalTracks - 1) {
                        // Not playing and not on last track - stop check
                        break
                    }

                    // Get current state (currentIndex and totalTracks already defined above)
                    val currentPosition = player.currentPosition
                    val duration = player.duration
                    val endOfFileThresholdMs = calculateEndOfFileThresholdMs(duration)

                    // Log position check for debugging (only on last track to reduce spam)
                    if (currentIndex >= totalTracks - 1) {
                        LogUtils.d(
                            "AudioPlayerService",
                            "Position check: index=$currentIndex/$totalTracks, position=${currentPosition}ms (${currentPosition / 1000}s), duration=${duration}ms (${duration / 1000}s), isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, state=${player.playbackState}",
                        )
                    }

                    // Only check on the last track
                    if (currentIndex >= totalTracks - 1) {
                        // Check 1: Position reached or exceeded duration (if duration is valid)
                        if (duration != C.TIME_UNSET && duration > 0) {
                            if (currentPosition >= duration) {
                                LogUtils.i(
                                    "AudioPlayerService",
                                    "Detected end of last track: position reached/exceeded duration (position=$currentPosition, duration=$duration)",
                                )
                                handleBookCompletion(player, currentIndex)
                                break
                            }

                            // Check 2: Smart completion detection (inspired by Easybook)
                            // Mark as completed when within 3 minutes of the end (180000ms)
                            // This helps users know they're near completion and prevents issues with credits/silence
                            val remaining = duration - currentPosition
                            val smartCompletionThresholdMs = 180000L // 3 minutes

                            if (remaining <= smartCompletionThresholdMs && remaining > endOfFileThresholdMs) {
                                LogUtils.i(
                                    "AudioPlayerService",
                                    "Smart completion: within 3 minutes of end (remaining=${remaining}ms, ${remaining / 1000}s)",
                                )
                                handleBookCompletion(player, currentIndex)
                                break
                            }

                            // Check 3: Position is very close to duration (within threshold)
                            if (remaining <= endOfFileThresholdMs) {
                                LogUtils.i(
                                    "AudioPlayerService",
                                    "Detected end of last track by position check: position=$currentPosition, duration=$duration, remaining=$remaining",
                                )
                                handleBookCompletion(player, currentIndex)
                                break
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
                                LogUtils.d(
                                    "AudioPlayerService",
                                    "Position not advancing on last track: position=$currentPosition, stopped for $positionStoppedCount checks (${stoppedTimeMs}ms), isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, duration=$duration, nearEnd=$isNearEnd",
                                )

                                // If position stopped for threshold checks OR for max time, consider file ended
                                if (positionStoppedCount >= positionStoppedThreshold ||
                                    stoppedTimeMs >= maxPositionStoppedTimeMs
                                ) {
                                    // Position hasn't changed for multiple checks or max time - file definitely ended
                                    // This works even if duration is incorrect!
                                    LogUtils.i(
                                        "AudioPlayerService",
                                        "Detected end of last track: position stopped advancing (position=$currentPosition, duration=$duration, stopped for $positionStoppedCount checks/${stoppedTimeMs}ms, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady})",
                                    )
                                    handleBookCompletion(player, currentIndex)
                                    break
                                }
                            } else {
                                // Not playing and not near end - might be paused, reset counter
                                positionStoppedCount = 0
                                positionStoppedStartTime = -1L
                            }
                        } else if (lastPosition >= 0 && currentPosition != lastPosition) {
                            // Position changed, reset counter
                            if (positionStoppedCount > 0) {
                                LogUtils.v(
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
                        resetPositionTrackingState()
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
                            LogUtils.i(
                                "AudioPlayerService",
                                "Detected end of last track: playback stopped near end (position=$currentPosition, duration=$duration, nearEnd=$isNearEnd, positionStopped=$positionStopped)",
                            )
                            handleBookCompletion(player, currentIndex)
                            break
                        }
                    }

                    // Wait for next check interval
                    kotlinx.coroutines.delay(positionCheckIntervalMs)
                }
                // Reset tracking when loop ends
                resetPositionTrackingState()
            }
    }

    /**
     * Stops periodic position checking.
     */
    private fun stopPositionCheck() {
        positionCheckJob?.cancel()
        positionCheckJob = null
        // Reset tracking when stopping
        resetPositionTrackingState()
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
                    LogUtils.d(
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
                        LogUtils.d(
                            "AudioPlayerService",
                            "Using calculated last track index $lastTrackIndex (total=$totalTracks, originalIndex=$currentIndex)",
                        )
                        lastTrackIndex
                    } else {
                        // If we can't determine, use last track index if index is out of bounds
                        // This handles the case when ExoPlayer sets index to >= totalTracks
                        if (currentIndex >= totalTracks) {
                            val lastTrackIndex = totalTracks - 1
                            LogUtils.d(
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

            LogUtils.i(
                "AudioPlayerService",
                "Book completed: last track finished (track $lastIndex of ${totalTracks - 1}, position=${lastPosition}ms, originalIndex=$currentIndex) - detected by position check",
            )

            // Set flag to prevent further playback
            setIsBookCompleted(true)

            // Save last track index so getState() can return correct index even if ExoPlayer resets it
            setLastCompletedTrackIndex?.invoke(lastIndex)
            LogUtils.d(
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
                    LogUtils.d(
                        "AudioPlayerService",
                        "Seeked to last track $lastIndex at position $seekPosition to preserve index",
                    )
                }
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error handling book completion", e)
                // Fallback to just pausing
                player.playWhenReady = false
            }

            // Save final position
            saveCurrentPosition()

            // Update notification to show completion
            scheduleNotificationUpdate()

            // Send broadcast to UI to show completion message
            val intent =
                Intent("com.jabook.app.jabook.BOOK_COMPLETED").apply {
                    setPackage(context.packageName) // Set package for explicit broadcast
                    putExtra("last_track_index", lastIndex)
                }
            context.sendBroadcast(intent)
        }
    }

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
}
