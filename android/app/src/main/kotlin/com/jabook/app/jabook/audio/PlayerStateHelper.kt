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

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Helper class for getting player state information.
 */
internal class PlayerStateHelper(
    private val getActivePlayer: () -> ExoPlayer,
    private val getDurationCache: () -> MutableMap<String, Long>,
    private val getDurationForFile: ((String) -> Long?)? = null,
    private val getLastCompletedTrackIndex: (() -> Int)? = null,
    private val getActualPlaylistSize: (() -> Int)? = null, // Get actual playlist size from filePaths
    private val getActualTrackIndex: (() -> Int)? = null, // Get actual track index from onMediaItemTransition
    private val getCurrentFilePaths: (() -> List<String>?)? = null, // Get current file paths to match by URI
) {
    /**
     * Gets current playback position.
     *
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long = getActivePlayer().currentPosition

    /**
     * Gets total duration of current media.
     *
     * @return Duration in milliseconds, or 0 if unknown
     */
    fun getDuration(): Long = getActivePlayer().duration

    /**
     * Gets playlist information.
     *
     * @return Map with playlist information
     */
    fun getPlaylistInfo(): Map<String, Any> {
        val player = getActivePlayer()
        return mapOf(
            "itemCount" to player.mediaItemCount,
            "currentIndex" to player.currentMediaItemIndex,
            "hasNext" to player.hasNextMediaItem(),
            "hasPrevious" to player.hasPreviousMediaItem(),
            "repeatMode" to player.repeatMode,
            "shuffleModeEnabled" to player.shuffleModeEnabled,
        )
    }

    /**
     * Gets current player state.
     *
     * According to best practices:
     * 1. Primary source: player.duration (after STATE_READY)
     * 2. Fallback: MediaMetadataRetriever (only if player doesn't provide duration)
     * 3. Cache duration after getting it from player
     *
     * @return Map with player state information
     */
    fun getPlayerState(): Map<String, Any> {
        val player = getActivePlayer()

        // Get duration - PRIMARY SOURCE: player.duration (ExoPlayer/Media3)
        // This is the most reliable source after player is in STATE_READY
        var duration: Long = player.duration
        val durationSource = if (duration != C.TIME_UNSET && duration > 0) "player" else "unknown"

        android.util.Log.v(
            "AudioPlayerService",
            "Getting duration: player.duration=$duration (${duration / 1000}s), source=$durationSource",
        )

        // If player doesn't provide duration, try fallback: MediaMetadataRetriever
        if (duration == C.TIME_UNSET || duration <= 0) {
            val currentItem = player.currentMediaItem
            val uri = currentItem?.localConfiguration?.uri

            // Fallback: try MediaMetadataRetriever for local files only
            if (uri != null && uri.scheme == "file") {
                val filePath = uri.path
                if (filePath != null) {
                    // Check cache first (cached from previous player.duration or MediaMetadataRetriever)
                    val cachedDuration = getDurationCache()[filePath]
                    if (cachedDuration != null && cachedDuration > 0) {
                        duration = cachedDuration
                        android.util.Log.d("AudioPlayerService", "Using cached duration for $filePath: ${duration}ms")
                    } else {
                        // Cache miss - try database via callback
                        val dbDuration = getDurationForFile?.invoke(filePath)
                        if (dbDuration != null && dbDuration > 0) {
                            duration = dbDuration
                            // Cache it for future use
                            getDurationCache()[filePath] = duration
                            android.util.Log.d("AudioPlayerService", "Got duration from database for $filePath: ${duration}ms")
                        } else {
                            // Database miss - try MediaMetadataRetriever as fallback
                            // Fallback: use MediaMetadataRetriever (only if player didn't provide duration)
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(filePath)
                                val retrieverDuration =
                                    retriever.extractMetadata(
                                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                                    )
                                retriever.release()
                                if (retrieverDuration != null) {
                                    val parsedDuration = retrieverDuration.toLongOrNull()
                                    if (parsedDuration != null && parsedDuration > 0) {
                                        duration = parsedDuration
                                        // Cache it for future use
                                        getDurationCache()[filePath] = duration
                                        android.util.Log.d(
                                            "AudioPlayerService",
                                            "Got duration from MediaMetadataRetriever (fallback) for $filePath: ${duration}ms",
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "AudioPlayerService",
                                    "Failed to get duration from MediaMetadataRetriever for $filePath: ${e.message}",
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Player provided duration - cache it for future use
            val currentItem = player.currentMediaItem
            val uri = currentItem?.localConfiguration?.uri
            if (uri != null && uri.scheme == "file") {
                val filePath = uri.path
                if (filePath != null) {
                    // Cache duration from player (most reliable source)
                    getDurationCache()[filePath] = duration
                    android.util.Log.i(
                        "AudioPlayerService",
                        "Cached duration from player for $filePath: ${duration}ms (${duration / 1000}s)",
                    )
                }
            }
        }

        // If still unset, return 0
        if (duration == C.TIME_UNSET || duration < 0) {
            duration = 0L
        }

        // CRITICAL: Use actualTrackIndex from onMediaItemTransition events as primary source
        // This ensures we get the correct track index even if currentMediaItemIndex hasn't updated yet
        var currentIndex = getActualTrackIndex?.invoke() ?: player.currentMediaItemIndex
        val savedIndex = getLastCompletedTrackIndex?.invoke() ?: -1
        // Use actual playlist size from filePaths if available, otherwise use player.mediaItemCount
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

        // CRITICAL: If currentIndex seems wrong (e.g., 0 but playing file 7), try to find real index by URI
        // This handles cases where ExoPlayer's currentMediaItemIndex doesn't match the actual playing file
        if (currentIndex == 0 && totalTracks > 1) {
            val currentItem = player.currentMediaItem
            val currentUri = currentItem?.localConfiguration?.uri
            val filePaths = getCurrentFilePaths?.invoke()

            if (currentUri != null && filePaths != null && filePaths.isNotEmpty()) {
                val currentPath = currentUri.path
                if (currentPath != null) {
                    // Find the index of the current file in filePaths
                    val realIndex =
                        filePaths.indexOfFirst { filePath ->
                            // Compare paths (handle both absolute and relative paths)
                            filePath == currentPath ||
                                filePath.endsWith(currentPath) ||
                                currentPath.endsWith(filePath)
                        }

                    if (realIndex >= 0 && realIndex != currentIndex) {
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Found real index by URI: currentIndex=$currentIndex, realIndex=$realIndex, " +
                                "file=${currentPath.substringAfterLast('/')}",
                        )
                        currentIndex = realIndex
                    }
                }
            }
        }

        // CRITICAL: Try to extract chapter number from filename if available
        // This handles cases where file order doesn't match chapter numbers (e.g., 08.mp3 at index 8 should be chapter 8, not 9)
        var chapterNumberFromFilename: Int? = null
        val currentItem = player.currentMediaItem
        val currentUri = currentItem?.localConfiguration?.uri
        if (currentUri != null) {
            val currentPath = currentUri.path
            if (currentPath != null) {
                val fileName = currentPath.substringAfterLast('/')
                // Try to extract number from filename (e.g., "08.mp3" -> 8, "01.mp3" -> 1)
                val numberMatch = Regex("""^(\d+)""").find(fileName)
                if (numberMatch != null) {
                    val numberStr = numberMatch.groupValues[1]
                    val number = numberStr.toIntOrNull()
                    if (number != null && number > 0) {
                        chapterNumberFromFilename = number
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Extracted chapter number from filename: file=$fileName, chapterNumber=$chapterNumberFromFilename",
                        )
                    }
                }
            }
        }

        // Log current state for debugging
        android.util.Log.v(
            "AudioPlayerService",
            "PlayerStateHelper: actualTrackIndex=${getActualTrackIndex?.invoke()}, " +
                "currentMediaItemIndex=${player.currentMediaItemIndex}, totalTracks=$totalTracks, " +
                "savedIndex=$savedIndex, using currentIndex=$currentIndex",
        )

        if (savedIndex >= 0 &&
            totalTracks > 0 &&
            savedIndex < totalTracks &&
            (currentIndex == 0 || currentIndex < 0 || currentIndex >= totalTracks)
        ) {
            // Index was reset to 0 or went out of bounds after book completion, but we have saved index
            // Use saved index to show correct track number
            android.util.Log.d(
                "AudioPlayerService",
                "Using saved track index $savedIndex instead of invalid index $currentIndex (total=$totalTracks)",
            )
            currentIndex = savedIndex
        } else if (currentIndex >= totalTracks && totalTracks > 0) {
            // Index is out of bounds but no saved index - use last track index
            // This handles the case when ExoPlayer sets index to >= totalTracks
            val lastTrackIndex = totalTracks - 1
            android.util.Log.d(
                "AudioPlayerService",
                "Index out of bounds ($currentIndex >= $totalTracks), using last track index $lastTrackIndex",
            )
            currentIndex = lastTrackIndex
        } else if (currentIndex < 0 && totalTracks > 0) {
            // Index is negative - use 0 as fallback
            android.util.Log.w(
                "AudioPlayerService",
                "Index is negative ($currentIndex), using 0 as fallback",
            )
            currentIndex = 0
        }

        // Calculate chapter number (1-based) - single source of truth
        // CRITICAL: Prefer chapter number from filename if available, otherwise use index-based calculation
        // This handles cases where file order doesn't match chapter numbers (e.g., 08.mp3 at index 8 should be chapter 8, not 9)
        val chapterNumber =
            if (chapterNumberFromFilename != null) {
                // Use chapter number extracted from filename (most accurate)
                chapterNumberFromFilename!!
            } else if (currentIndex >= 0 && currentIndex < totalTracks) {
                // Fallback to index-based calculation (currentIndex + 1)
                currentIndex + 1
            } else {
                // Fallback to 1 if index is invalid
                android.util.Log.w(
                    "AudioPlayerService",
                    "Invalid currentIndex ($currentIndex) or totalTracks ($totalTracks), using chapterNumber=1",
                )
                1
            }

        android.util.Log.v(
            "AudioPlayerService",
            "PlayerStateHelper: final currentIndex=$currentIndex, chapterNumber=$chapterNumber",
        )

        return mapOf(
            "isPlaying" to player.isPlaying,
            "playWhenReady" to player.playWhenReady, // Added for debugging
            "playbackState" to player.playbackState,
            "currentPosition" to player.currentPosition,
            "duration" to duration,
            "currentIndex" to currentIndex,
            "chapterNumber" to chapterNumber, // 1-based chapter number from native
            "playbackSpeed" to player.playbackParameters.speed,
            "repeatMode" to player.repeatMode,
            "shuffleModeEnabled" to player.shuffleModeEnabled,
            "mediaItemCount" to player.mediaItemCount,
        )
    }
}
