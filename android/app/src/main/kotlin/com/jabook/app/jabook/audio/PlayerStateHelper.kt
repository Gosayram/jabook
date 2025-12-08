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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Helper class for getting player state information.
 */
internal class PlayerStateHelper(
    private val getActivePlayer: () -> ExoPlayer,
    private val getCachedDuration: (String) -> Long?,
    private val saveDurationToCache: (String, Long) -> Unit,
    private val getDurationForFile: ((String) -> Long?)? = null,
    private val getLastCompletedTrackIndex: (() -> Int)? = null,
    private val getActualPlaylistSize: (() -> Int)? = null,
    private val getActualTrackIndex: (() -> Int)? = null,
    private val getCurrentFilePaths: (() -> List<String>?)? = null,
    private val coroutineScope: CoroutineScope? = null, // Injected scope for async tasks
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
                    val cachedDuration = getCachedDuration(filePath)
                    if (cachedDuration != null && cachedDuration > 0) {
                        duration = cachedDuration
                        android.util.Log.d("AudioPlayerService", "Using cached duration for $filePath: ${duration}ms")
                    } else {
                        // Cache miss - try database via callback
                        val dbDuration = getDurationForFile?.invoke(filePath)
                        if (dbDuration != null && dbDuration > 0) {
                            duration = dbDuration
                            // Cache it for future use
                            saveDurationToCache(filePath, duration)
                            android.util.Log.d("AudioPlayerService", "Got duration from database for $filePath: ${duration}ms")
                        } else {
                            // Database miss - fetch from MediaMetadataRetriever in BACKGROUND
                            if (coroutineScope != null) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val retriever = android.media.MediaMetadataRetriever()
                                    try {
                                        retriever.setDataSource(filePath)
                                        val retrieverDuration =
                                            retriever.extractMetadata(
                                                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                                            )

                                        if (retrieverDuration != null) {
                                            val parsedDuration = retrieverDuration.toLongOrNull()
                                            if (parsedDuration != null && parsedDuration > 0) {
                                                // Update cache safely
                                                saveDurationToCache(filePath, parsedDuration)
                                                android.util.Log.d(
                                                    "AudioPlayerService",
                                                    "Async duration fetch success for $filePath: ${parsedDuration}ms",
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w(
                                            "AudioPlayerService",
                                            "Async duration fetch failed for $filePath: ${e.message}",
                                        )
                                    } finally {
                                        try {
                                            retriever.release()
                                        } catch (e: Exception) {
                                            // Ignore errors during release
                                        }
                                    }
                                }
                                android.util.Log.v(
                                    "AudioPlayerService",
                                    "Triggered async duration fetch for $filePath",
                                )
                            } else {
                                android.util.Log.w(
                                    "AudioPlayerService",
                                    "CoroutineScope not provided, cannot fetch duration async for $filePath",
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
                    saveDurationToCache(filePath, duration)
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

        // CRITICAL: URI-based index correction has been DISABLED
        // This correction was causing issues because it assumed files are in sorted order,
        // but the playlist is actually built from chapters array using chapter.fileIndex.
        // The real fix for chapter selection is in player_screen.dart (_seekToChapter)
        // where we now use chapter.fileIndex instead of chapters.indexOf(chapter).
        //
        // Keeping this code commented for reference, but it should NOT be used:

        /*
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
         */

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

        // Calculate chapter number (1-based) from current playlist position
        // CRITICAL: Use index-based calculation only (currentIndex + 1)
        // This ensures consistency with Dart code where chapters are ordered by array position
        val chapterNumber =
            if (currentIndex >= 0 && currentIndex < totalTracks) {
                // Use index-based calculation (currentIndex + 1)
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
            "PlayerStateHelper: final currentIndex=$currentIndex, chapterNumber=$chapterNumber, playbackState=${player.playbackState}",
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
