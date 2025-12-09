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
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized class for saving playback position and state.
 *
 * All position saving logic is in this file for easy reading and maintenance.
 * Uses two-level strategy:
 * 1. MethodChannel → Flutter (primary path)
 * 2. SharedPreferences directly (fallback)
 *
 * Also handles periodic position saving and full media item state persistence.
 */
internal class PlaybackPositionSaver(
    private val getActivePlayer: () -> ExoPlayer,
    private val getMethodChannel: () -> MethodChannel?,
    private val context: Context,
    private val getGroupPath: (() -> String?)? = null,
    private val isPlaylistLoading: (() -> Boolean)? = null,
    private val getActualTrackIndex: (() -> Int)? = null, // Get actual track index from onMediaItemTransition
    private val getCurrentFilePaths: (() -> List<String>?)? = null, // Get current file paths to match by URI
    private val getDurationForFile: ((String) -> Long?)? = null, // Get duration for file path (for position-based calculation)
    private val playerPersistenceManager: PlayerPersistenceManager? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val getEmbeddedArtworkPath: (() -> String?)? = null,
    private val getCurrentMetadata: (() -> Map<String, String>?)? = null,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    private var lastSaveTime: Long = 0
    private val minSaveIntervalMs = 1000L // Minimum 1 second between saves (High precision)

    // Periodic position saving
    private var positionSaveJob: Job? = null
    private var lastPeriodicSaveTime: Long = 0

    // Key prefixes matching Flutter PlaybackPositionService
    private val groupPositionPrefix = "group_pos_"
    private val trackIndexPrefix = "track_idx_"
    private val trackPositionPrefix = "track_pos_"

    /**
     * Saves current playback position.
     *
     * Uses debounce to avoid saving too frequently.
     * Tries MethodChannel first, falls back to SharedPreferences if unavailable.
     *
     * @param reason Reason for saving (for logging)
     */
    fun savePosition(reason: String = "unknown") {
        // Debounce: don't save too frequently (except for critical events)
        val now = System.currentTimeMillis()
        if (now - lastSaveTime < minSaveIntervalMs &&
            reason != "service_destroyed" &&
            reason != "activity_paused" &&
            reason != "activity_stopped" &&
            reason != "pause" &&
            reason != "stop"
        ) {
            android.util.Log.v(
                "PlaybackPositionSaver",
                "Skipping save (too soon): reason=$reason, lastSave=${now - lastSaveTime}ms ago",
            )
            return
        }
        lastSaveTime = now

        // Don't save if playlist is currently loading (prevents saving incorrect index)
        if (isPlaylistLoading?.invoke() == true) {
            android.util.Log.v(
                "PlaybackPositionSaver",
                "Skipping save: playlist is currently loading (reason=$reason)",
            )
            return
        }

        // Get current position
        val player = getActivePlayer()
        if (player.mediaItemCount == 0) {
            android.util.Log.d("PlaybackPositionSaver", "No media items, skipping save")
            return
        }

        // CRITICAL: Use actualTrackIndex from onMediaItemTransition events as single source of truth
        // This ensures we save the correct track index even if currentMediaItemIndex hasn't updated yet
        var currentIndex = getActualTrackIndex?.invoke() ?: player.currentMediaItemIndex

        // CRITICAL: Always verify index using URI if available (not just for index 0)
        // This handles cases where actualTrackIndex or currentMediaItemIndex is incorrect
        // This is especially important during playlist loading or after seek operations
        val currentItem = player.currentMediaItem
        val currentUri = currentItem?.localConfiguration?.uri
        val filePaths = getCurrentFilePaths?.invoke()

        if (currentUri != null && filePaths != null && filePaths.isNotEmpty()) {
            val currentPath = currentUri.path
            if (currentPath != null) {
                // Log for debugging
                val currentFileName = currentPath.substringAfterLast('/')
                android.util.Log.d(
                    "PlaybackPositionSaver",
                    "Looking for file: $currentFileName in filePaths (size=${filePaths.size}), currentIndex=$currentIndex",
                )

                // Find the index of the current file in filePaths
                val realIndex =
                    filePaths.indexOfFirst { filePath ->
                        // Compare paths (handle both absolute and relative paths)
                        filePath == currentPath ||
                            filePath.endsWith(currentPath) ||
                            currentPath.endsWith(filePath)
                    }

                if (realIndex >= 0) {
                    // Use URI-based index if it differs from currentIndex
                    // This ensures we always save the correct index
                    if (realIndex != currentIndex) {
                        android.util.Log.d(
                            "PlaybackPositionSaver",
                            "Found real index by URI for saving: currentIndex=$currentIndex, realIndex=$realIndex, " +
                                "file=${currentPath.substringAfterLast('/')}",
                        )
                        currentIndex = realIndex
                    }
                } else {
                    android.util.Log.w(
                        "PlaybackPositionSaver",
                        "Current file URI not found in filePaths: ${currentPath.substringAfterLast('/')}, " +
                            "using currentIndex=$currentIndex",
                    )
                    // Fallback: Try to calculate index by position if URI matching failed
                    // This is useful when file paths don't match exactly (e.g., different URI schemes)
                    // Calculate total position by accumulating previous track durations
                    var totalPosition = 0L
                    for (i in 0 until currentIndex.coerceAtMost(filePaths.size - 1)) {
                        val duration = getDurationForFile?.invoke(filePaths[i]) ?: 0L
                        if (duration > 0) {
                            totalPosition += duration
                        }
                    }
                    totalPosition += player.currentPosition

                    // Try to calculate index from total position
                    val calculatedIndex = calculateTrackIndexByPosition(filePaths, totalPosition)
                    if (calculatedIndex >= 0 && calculatedIndex < filePaths.size) {
                        android.util.Log.d(
                            "PlaybackPositionSaver",
                            "Calculated index by position: currentIndex=$currentIndex, calculatedIndex=$calculatedIndex",
                        )
                        // Use calculated index if it's different and seems more reliable
                        // (only if we have durations for most tracks)
                        if (calculatedIndex != currentIndex) {
                            currentIndex = calculatedIndex
                        }
                    }
                }
            }
        }

        val currentPosition = player.currentPosition

        // Validate position before saving
        // Check that index is within bounds
        if (filePaths != null && filePaths.isNotEmpty()) {
            if (currentIndex < 0 || currentIndex >= filePaths.size) {
                android.util.Log.w(
                    "PlaybackPositionSaver",
                    "Invalid track index for saving: $currentIndex (playlist size=${filePaths.size}), skipping save",
                )
                return
            }
        }

        // Validate position is non-negative
        if (currentPosition < 0) {
            android.util.Log.w(
                "PlaybackPositionSaver",
                "Invalid position for saving: $currentPosition, skipping save",
            )
            return
        }

        // Optional: Validate position against track duration if available
        // Note: Duration might not be available immediately after loading, so this is optional
        val trackDuration = player.duration
        if (trackDuration > 0 && currentPosition > trackDuration) {
            android.util.Log.w(
                "PlaybackPositionSaver",
                "Position exceeds track duration: position=$currentPosition, duration=$trackDuration, " +
                    "clamping to duration",
            )
            // Clamp position to duration instead of skipping save
            // This ensures we save a valid position even if player state is slightly off
        }

        android.util.Log.i(
            "PlaybackPositionSaver",
            "Saving position: track=$currentIndex (actualTrackIndex=${getActualTrackIndex?.invoke()}, " +
                "currentMediaItemIndex=${player.currentMediaItemIndex}), position=${currentPosition}ms, reason=$reason",
        )

        // Try to save via MethodChannel first
        val savedViaMethodChannel = savePositionViaMethodChannel(currentIndex, currentPosition)

        // If MethodChannel unavailable, use fallback
        if (!savedViaMethodChannel) {
            android.util.Log.w(
                "PlaybackPositionSaver",
                "MethodChannel unavailable, using SharedPreferences fallback",
            )
            savePositionViaSharedPreferences(currentIndex, currentPosition)
        }
    }

    /**
     * Stores current media item detailed state for persistence.
     * Called when media item changes or playback position changes significantly.
     */
    @OptIn(UnstableApi::class) // For mediaMetadata
    fun storeCurrentMediaItem() {
        if (playerPersistenceManager == null || coroutineScope == null) {
            android.util.Log.w("PlaybackPositionSaver", "Cannot store media item: deps not initialized")
            return
        }

        val player = getActivePlayer()
        val currentMediaItem = player.currentMediaItem ?: return
        val mediaId = currentMediaItem.mediaId ?: return

        // Get current position and duration
        val positionMs = player.currentPosition
        val durationMs = player.duration

        // Get artwork path (prefer embedded, fallback to metadata)
        val artworkPath =
            getEmbeddedArtworkPath?.invoke()
                ?: currentMediaItem.mediaMetadata.artworkUri?.toString()
                ?: ""

        // Get metadata
        val currentMetadataMap = getCurrentMetadata?.invoke()
        val title = currentMetadataMap?.get("title") ?: currentMediaItem.mediaMetadata.title?.toString() ?: ""
        val artist = currentMetadataMap?.get("artist") ?: currentMediaItem.mediaMetadata.artist?.toString() ?: ""
        val groupPath = getGroupPath?.invoke() ?: ""

        // Save to SharedPreferences asynchronously
        coroutineScope.launch {
            playerPersistenceManager.saveCurrentMediaItem(
                mediaId,
                positionMs,
                durationMs,
                artworkPath,
                title,
                artist,
                groupPath,
            )
        }
    }

    /**
     * Starts periodic position saving with adaptive intervals.
     *
     * Inspired by lissen-android's PlaybackSynchronizationService.
     * Saves more frequently (every 5s) when near start/end of track,
     * less frequently (every 30s) in the middle.
     * This improves position accuracy while maintaining reasonable performance.
     */
    fun startPeriodicPositionSaving() {
        stopPeriodicPositionSaving() // Stop existing job if any

        if (getGroupPath?.invoke() == null) {
            android.util.Log.v("PlaybackPositionSaver", "Periodic position saving not started: no groupPath")
            return
        }

        if (coroutineScope == null) {
            android.util.Log.e("PlaybackPositionSaver", "Cannot start periodic saving: coroutineScope is null")
            return
        }

        android.util.Log.d("PlaybackPositionSaver", "Starting periodic position saving with adaptive intervals")
        positionSaveJob =
            coroutineScope.launch {
                while (true) {
                    try {
                        val player = getActivePlayer()

                        // Only save if playing and not loading playlist
                        if (!player.isPlaying || isPlaylistLoading?.invoke() == true) {
                            delay(5000) // Check again in 5 seconds
                            continue
                        }

                        val position = player.currentPosition
                        val duration = player.duration

                        if (duration <= 0) {
                            // Duration unknown, save with default interval
                            delay(10000) // 10 seconds default
                            savePosition("periodic")
                            // Also store media item for playback resumption
                            storeCurrentMediaItem()
                            continue
                        }

                        // Calculate position ratio (0.0 to 1.0)
                        val positionRatio = position.toDouble() / duration.toDouble()

                        // Define "near start" and "near end" thresholds
                        // Save frequently if within first 10% or last 10% of track
                        val nearStartThreshold = 0.1 // 10% from start
                        val nearEndThreshold = 0.9 // 10% from end
                        val isNearStart = positionRatio < nearStartThreshold
                        val isNearEnd = positionRatio > nearEndThreshold

                        // Calculate time since last save
                        val now = System.currentTimeMillis()
                        val timeSinceLastSave = now - lastPeriodicSaveTime

                        // Save if:
                        // - Near start/end and 5+ seconds passed (frequent saves)
                        // - In middle and 30+ seconds passed (less frequent)
                        val shouldSave =
                            when {
                                isNearStart || isNearEnd -> timeSinceLastSave >= 2000L // 2 seconds (High Precision)
                                else -> timeSinceLastSave >= 5000L // 5 seconds (High Precision)
                            }

                        if (shouldSave || lastPeriodicSaveTime == 0L) {
                            savePosition("periodic")
                            // Also store media item for playback resumption
                            storeCurrentMediaItem()
                            lastPeriodicSaveTime = now
                        }

                        // Wait before next check (adaptive delay)
                        val delayMs =
                            when {
                                isNearStart || isNearEnd -> 2000L // Check every 2 seconds
                                else -> 5000L // Check every 5 seconds
                            }
                        delay(delayMs)
                    } catch (e: Exception) {
                        android.util.Log.w("PlaybackPositionSaver", "Error in periodic position saving", e)
                        delay(10000) // Wait before retrying
                    }
                }
            }
    }

    /**
     * Stops periodic position saving.
     */
    fun stopPeriodicPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = null
        android.util.Log.v("PlaybackPositionSaver", "Stopped periodic position saving")
    }

    /**
     * Saves position via MethodChannel (primary path).
     *
     * @param currentIndex Current track index
     * @param currentPosition Current position in milliseconds
     * @return true if saved successfully, false otherwise
     */
    private fun savePositionViaMethodChannel(
        currentIndex: Int,
        currentPosition: Long,
    ): Boolean {
        val methodChannel = getMethodChannel()
        if (methodChannel == null) {
            android.util.Log.d("PlaybackPositionSaver", "MethodChannel is null, cannot save via MethodChannel")
            return false
        }

        return try {
            // Call Flutter method to save position
            // This is a call from Kotlin to Flutter (reverse direction)
            // Flutter will handle groupPath internally
            methodChannel.invokeMethod(
                "saveCurrentPosition",
                mapOf(
                    "trackIndex" to currentIndex,
                    "positionMs" to currentPosition,
                ),
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        android.util.Log.d(
                            "PlaybackPositionSaver",
                            "Position save request sent successfully via MethodChannel: track=$currentIndex, position=${currentPosition}ms",
                        )
                    }

                    override fun error(
                        errorCode: String,
                        errorMessage: String?,
                        errorDetails: Any?,
                    ) {
                        android.util.Log.w(
                            "PlaybackPositionSaver",
                            "Failed to send position save request via MethodChannel: $errorCode - $errorMessage",
                        )
                    }

                    override fun notImplemented() {
                        android.util.Log.w(
                            "PlaybackPositionSaver",
                            "saveCurrentPosition method not implemented in Flutter - using fallback",
                        )
                    }
                },
            )
            // Note: invokeMethod is async, but we return true immediately
            // The actual save happens in Flutter via callback
            true
        } catch (e: Exception) {
            android.util.Log.e("PlaybackPositionSaver", "Exception sending position save request via MethodChannel", e)
            false
        }
    }

    /**
     * Saves position via SharedPreferences (fallback).
     *
     * Uses the same key format as Flutter PlaybackPositionService for compatibility.
     * Gets groupPath from callback or SharedPreferences for proper saving.
     * This is a last resort when MethodChannel is unavailable.
     *
     * @param currentIndex Current track index
     * @param currentPosition Current position in milliseconds
     */
    private fun savePositionViaSharedPreferences(
        currentIndex: Int,
        currentPosition: Long,
    ) {
        try {
            // Try to get groupPath from callback first
            var groupPath = getGroupPath?.invoke()

            // If not available from callback, try to get from SharedPreferences
            if (groupPath == null) {
                groupPath = prefs.getString("current_group_path", null)
            }

            if (groupPath == null) {
                android.util.Log.w(
                    "PlaybackPositionSaver",
                    "Cannot save via SharedPreferences: no groupPath available (track=$currentIndex, position=${currentPosition}ms)",
                )
                return
            }

            // Sanitize groupPath for use as SharedPreferences key
            val sanitizedPath = sanitizeKey(groupPath)

            // Save position using the same format as Flutter PlaybackPositionService
            val groupKey = "${groupPositionPrefix}$sanitizedPath"
            val trackIndexKey = "${trackIndexPrefix}$sanitizedPath"
            val trackPositionKey = "${trackPositionPrefix}$sanitizedPath}_$currentIndex"

            prefs
                .edit()
                .putInt(groupKey, currentPosition.toInt())
                .putInt(trackIndexKey, currentIndex)
                .putInt(trackPositionKey, currentPosition.toInt())
                .apply()

            android.util.Log.i(
                "PlaybackPositionSaver",
                "Position saved via SharedPreferences fallback: groupPath=$sanitizedPath, track=$currentIndex, position=${currentPosition}ms",
            )
        } catch (e: Exception) {
            android.util.Log.e("PlaybackPositionSaver", "Exception saving via SharedPreferences", e)
        }
    }

    /**
     * Calculates track index based on total position and file durations.
     *
     * This is a fallback method inspired by lissen-android's calculateChapterIndex.
     * It computes the track index by accumulating durations until the position is reached.
     *
     * @param filePaths List of file paths in the playlist
     * @param totalPositionMs Total position in milliseconds (accumulated across all tracks)
     * @return Track index, or -1 if calculation fails
     */
    private fun calculateTrackIndexByPosition(
        filePaths: List<String>,
        totalPositionMs: Long,
    ): Int {
        if (filePaths.isEmpty() || totalPositionMs < 0) {
            return -1
        }

        var accumulatedDuration = 0L
        for ((index, filePath) in filePaths.withIndex()) {
            val duration = getDurationForFile?.invoke(filePath) ?: 0L
            if (duration <= 0) {
                // If duration is unknown, we can't calculate accurately
                // Skip this track and continue (might be loading)
                continue
            }

            val trackEnd = accumulatedDuration + duration
            // Use small threshold (100ms) to handle floating point precision
            if (totalPositionMs < trackEnd - 100) {
                return index
            }
            accumulatedDuration = trackEnd
        }

        // If position exceeds all tracks, return last track
        return filePaths.size - 1
    }

    /**
     * Sanitizes a path to be used as a SharedPreferences key.
     *
     * SharedPreferences keys have limitations, so we need to sanitize paths.
     * This matches the implementation in Flutter PlaybackPositionService.
     */
    private fun sanitizeKey(key: String): String = key.replace(Regex("[^\\w\\-.]"), "_")
}
