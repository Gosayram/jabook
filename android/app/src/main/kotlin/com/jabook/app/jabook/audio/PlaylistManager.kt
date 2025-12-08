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
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.jabook.app.jabook.audio.ErrorHandler
import com.jabook.app.jabook.audio.SavedPlaybackState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages playlist preparation and MediaSource creation.
 *
 * Handles optimized lazy loading of playlists for fast startup.
 */
@OptIn(UnstableApi::class)
internal class PlaylistManager(
    private val context: Context,
    private val mediaCache: Cache,
    private val getActivePlayer: () -> ExoPlayer,
    private val getNotificationManager: () -> NotificationManager?,
    private val playerServiceScope: CoroutineScope,
    private val mediaItemDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val getFlavorSuffix: () -> String,
    private val setPendingTrackSwitchDeferred: ((CompletableDeferred<Int>) -> Unit)? = null, // Callback to set deferred in PlayerListener
    private val durationManager: DurationManager,
    private val playerPersistenceManager: PlayerPersistenceManager,
    private val playbackController: PlaybackController,
    private val getCurrentTrackIndex: () -> Int = { 0 }, // fallback
) {
    // State managed by PlaylistManager
    var currentFilePaths: List<String>? = null
        private set
    var currentMetadata: Map<String, String>? = null
        private set
    var currentGroupPath: String? = null
        private set
    internal var isPlaylistLoading = false
        internal set
    internal var currentLoadingPlaylist: List<String>? = null
        internal set
    var lastPlaylistLoadTime: Long = 0
        private set
    var lastCompletedTrackIndex: Int = -1
    var isBookCompleted = false
    var actualTrackIndex: Int = 0

    // Saved state for restoration
    var savedPlaybackState: SavedPlaybackState? = null

    // Track active loading job to prevent duplicates
    @Volatile
    private var activeLoadingJob: Job? = null

    /**
     * Sets playlist from file paths or URLs.
     *
     * Supports both local file paths and HTTP(S) URLs for network streaming.
     * Uses coroutines for async operations.
     *
     * @param filePaths List of absolute file paths or HTTP(S) URLs to audio files
     * @param metadata Optional metadata map (title, artist, album, etc.)
     * @param initialTrackIndex Optional track index to load first (for saved position). If null, loads first track.
     * @param initialPosition Optional position in milliseconds to seek to after loading initial track
     * @param groupPath Optional group path for saving playback position (used for fallback saving)
     * @param callback Optional callback to notify when playlist is ready (for Flutter)
     */
    fun setPlaylist(
        filePaths: List<String>,
        metadata: Map<String, String>? = null,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        groupPath: String? = null,
        callback: ((Boolean, Exception?) -> Unit)? = null,
    ) {
        // Prevent duplicate calls - if playlist is already loading, ignore
        if (isPlaylistLoading) {
            android.util.Log.w(
                "AudioPlayerService",
                "Playlist already loading, ignoring duplicate setPlaylist call: ${filePaths.size} items, initialTrackIndex=$initialTrackIndex",
            )
            callback?.invoke(true, null) // Call callback to unblock Flutter
            return
        }

        // Clear saved state to prevent restoration from interfering with new playlist
        savedPlaybackState = null

        // Reset book completion flag when setting new playlist
        isBookCompleted = false
        lastCompletedTrackIndex = -1 // Reset saved index for new book

        // Initialize actualTrackIndex from initialTrackIndex or default to 0
        actualTrackIndex = initialTrackIndex?.coerceIn(0, filePaths.size - 1) ?: 0
        android.util.Log.d(
            "AudioPlayerService",
            "Initialized actualTrackIndex to $actualTrackIndex (from initialTrackIndex=$initialTrackIndex)",
        )

        // Clear duration cache using DurationManager
        durationManager.clearCache()

        // Store file paths and groupPath
        currentFilePaths = filePaths
        currentMetadata = metadata
        currentGroupPath = groupPath

        // Save groupPath to SharedPreferences for fallback position saving
        if (groupPath != null) {
            playerPersistenceManager.saveGroupPathToSharedPreferences(groupPath)
        }

        android.util.Log.d(
            "AudioPlayerService",
            "Setting playlist with ${filePaths.size} items, initialTrackIndex=$initialTrackIndex, initialPosition=$initialPosition, groupPath=$groupPath",
        )

        // Mark as loading and record load time
        isPlaylistLoading = true
        currentLoadingPlaylist = filePaths
        lastPlaylistLoadTime = System.currentTimeMillis()

        playerServiceScope.launch {
            try {
                preparePlaybackOptimized(filePaths, metadata, initialTrackIndex, initialPosition)
                android.util.Log.d("AudioPlayerService", "Playlist prepared successfully")

                // Call callback first to unblock Flutter
                withContext(Dispatchers.Main) {
                    callback?.invoke(true, null)
                }

                // Apply initial position if provided (in background, non-blocking)
                if (initialTrackIndex != null && initialPosition != null && initialPosition > 0) {
                    val firstTrackIndex = initialTrackIndex.coerceIn(0, filePaths.size - 1)
                    if (firstTrackIndex != initialTrackIndex) {
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Target track ($initialTrackIndex) differs from first loaded track ($firstTrackIndex), scheduling position application",
                        )
                        playerServiceScope.launch {
                            playbackController.applyInitialPosition(initialTrackIndex, initialPosition, filePaths.size)
                        }
                    } else {
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Target track ($initialTrackIndex) is first loaded track, position already applied in preparePlaybackOptimized",
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to prepare playback", e)
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "preparePlayback failed")
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, e)
                }
            } finally {
                // Clear loading flag when done
                isPlaylistLoading = false
                currentLoadingPlaylist = null
            }
        }
    }

    /**
     * Prepares playback asynchronously with optimized lazy loading.
     *
     * CRITICAL OPTIMIZATION: Only creates the first MediaItem (or saved position track) synchronously.
     * Remaining items are added asynchronously in background to avoid blocking startup.
     * This dramatically speeds up player initialization, especially for large playlists.
     *
     * @param filePaths List of file paths or URLs
     * @param metadata Optional metadata
     * @param initialTrackIndex Optional track index to load first (for saved position). If null, loads first track (index 0).
     */
    suspend fun preparePlaybackOptimized(
        filePaths: List<String>,
        metadata: Map<String, String>?,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val dataSourceFactory = SimpleMediaDataSourceFactory()

            // Determine which track to load first
            // CRITICAL: Always load track 0 first, then switch to target track after all tracks are loaded
            // This ensures ExoPlayer has a valid playlist structure before switching tracks
            val firstTrackIndex = 0
            android.util.Log.d(
                "AudioPlayerService",
                "Loading first track: index=$firstTrackIndex (target=$initialTrackIndex, total=${filePaths.size})",
            )

            // CRITICAL: Create only the first MediaSource synchronously for fast startup
            // This allows player to start immediately while other tracks load in background
            val firstMediaSource =
                createMediaSourceForIndex(
                    filePaths,
                    firstTrackIndex,
                    metadata,
                    dataSourceFactory,
                )

            // Set first MediaSource and prepare player immediately
            withContext(Dispatchers.Main) {
                val activePlayer = getActivePlayer()
                activePlayer.playWhenReady = false

                // Clear any existing items first
                activePlayer.clearMediaItems()

                // CRITICAL: Add first item at the correct index to ensure it's the current track
                // This prevents ExoPlayer from switching to a different track when other tracks load
                activePlayer.addMediaSource(firstTrackIndex, firstMediaSource)
                activePlayer.prepare()

                // CRITICAL: Position will be applied after all tracks are loaded
                // This prevents ExoPlayer from switching to another track when MediaItems are added
                if (initialTrackIndex != null && initialPosition != null && initialPosition > 0) {
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Target track is $initialTrackIndex (first loaded: $firstTrackIndex), " +
                            "will apply position ${initialPosition}ms after all tracks are loaded",
                    )
                    // Apply position after all tracks are loaded to prevent track switching
                    // This is handled in the async loading coroutine after all MediaItems are added
                }

                getNotificationManager()?.updateNotification()

                android.util.Log.i(
                    "AudioPlayerService",
                    "First MediaItem loaded and prepared: index=$firstTrackIndex, " +
                        "state=${activePlayer.playbackState}, " +
                        "remaining items will load asynchronously",
                )
            }

            // Load remaining MediaSources asynchronously in background (non-blocking)
            // This doesn't block playback startup
            // OPTIMIZATION: Use limited dispatcher to control parallel MediaItem creation (max 16)
            // Modern devices can handle more concurrent I/O operations efficiently
            // Cancel previous loading job if exists to prevent duplicates
            activeLoadingJob?.cancel()
            activeLoadingJob =
                playerServiceScope.launch(mediaItemDispatcher) {
                    try {
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Starting async MediaItems loading (previous job cancelled if existed)",
                        )
                        val remainingIndices = filePaths.indices.filter { it != firstTrackIndex }
                        val totalItems = filePaths.size
                        val isLargePlaylist = totalItems > 100
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Loading ${remainingIndices.size} remaining MediaItems asynchronously in parallel (large playlist: $isLargePlaylist)",
                        )

                        // OPTIMIZATION: Prioritize loading critical tracks for faster navigation
                        // Priority order:
                        // 1. 2 previous tracks (if exist) - highest priority for backward navigation
                        // 2. 2 next tracks (if exist) - highest priority for forward navigation
                        // 3. All other tracks - can load in any order
                        val criticalPrevious = mutableListOf<Int>() // 2 tracks before target
                        val criticalNext = mutableListOf<Int>() // 2 tracks after target
                        val otherIndices = mutableListOf<Int>() // All other tracks

                        for (index in remainingIndices) {
                            when {
                                // 2 previous tracks (if exist) - highest priority
                                index >= firstTrackIndex - 2 && index < firstTrackIndex -> {
                                    criticalPrevious.add(index)
                                }
                                // 2 next tracks (if exist) - highest priority
                                index > firstTrackIndex && index <= firstTrackIndex + 2 -> {
                                    criticalNext.add(index)
                                }
                                // All other tracks - lower priority
                                else -> {
                                    otherIndices.add(index)
                                }
                            }
                        }

                        // Sort critical tracks to maintain order (previous descending, next ascending)
                        criticalPrevious.sortDescending() // Load closest to target first
                        criticalNext.sort() // Load closest to target first
                        // Other tracks sorted by index to maintain order
                        otherIndices.sort()

                        android.util.Log.d(
                            "AudioPlayerService",
                            "Loading priority: ${criticalPrevious.size} previous (target-2 to target-1), " +
                                "${criticalNext.size} next (target+1 to target+2), ${otherIndices.size} others",
                        )

                        // Mutex to synchronize addMediaSource calls and ensure correct order
                        val addMutex = Mutex()
                        // Track which indices have been added to ensure we add in order
                        // First track is already added, so mark it as added
                        val addedIndices = mutableSetOf<Int>(firstTrackIndex)

                        // Helper function to load a single MediaItem in parallel
                        suspend fun loadMediaItem(
                            index: Int,
                            priority: String,
                        ) {
                            try {
                                val mediaSource =
                                    createMediaSourceForIndex(
                                        filePaths,
                                        index,
                                        metadata,
                                        dataSourceFactory,
                                    )

                                // Wait for all previous indices to be added before adding this one
                                // Do this outside Main thread to avoid blocking
                                var waitAttempts = 0
                                while (waitAttempts < 100) { // Max 10 seconds wait
                                    val allPreviousAdded =
                                        addMutex.withLock {
                                            (0 until index).all { it == firstTrackIndex || it in addedIndices }
                                        }
                                    if (allPreviousAdded) {
                                        break
                                    }
                                    delay(100)
                                    waitAttempts++
                                }

                                // Add to player with synchronization to maintain order
                                withContext(Dispatchers.Main) {
                                    addMutex.withLock {
                                        // CRITICAL: Check if already added BEFORE getting player (prevent duplicates)
                                        // This must be done atomically with the add operation
                                        if (index in addedIndices) {
                                            android.util.Log.w(
                                                "AudioPlayerService",
                                                "MediaItem at index $index already added, skipping duplicate (playlist size: ${getActivePlayer().mediaItemCount})",
                                            )
                                            return@withContext
                                        }

                                        val activePlayer = getActivePlayer()
                                        val currentCount = activePlayer.mediaItemCount

                                        // Double-check: verify the index is not already in the player
                                        // This prevents race conditions where another coroutine added it
                                        if (currentCount > index) {
                                            // Check if this position already has a media item
                                            try {
                                                val existingItem = activePlayer.getMediaItemAt(index)
                                                if (existingItem.localConfiguration?.uri?.path == filePaths[index]) {
                                                    android.util.Log.w(
                                                        "AudioPlayerService",
                                                        "MediaItem at index $index already exists in player, skipping duplicate",
                                                    )
                                                    addedIndices.add(index) // Mark as added to prevent future attempts
                                                    return@withContext
                                                }
                                            } catch (e: Exception) {
                                                // Index might be out of bounds, continue with add
                                            }
                                        }

                                        // CRITICAL: Use index parameter to insert at correct position
                                        // This ensures tracks are added in the correct order, not at the end
                                        activePlayer.addMediaSource(index, mediaSource)
                                        addedIndices.add(index)
                                        android.util.Log.v(
                                            "AudioPlayerService",
                                            "Added $priority MediaItem at index $index (playlist size: ${activePlayer.mediaItemCount})",
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w(
                                    "AudioPlayerService",
                                    "Failed to create $priority MediaItem $index, skipping: ${e.message}",
                                )
                                // Continue with other items - one failure shouldn't stop the rest
                            }
                        }

                        // Load all tracks in parallel using launch for each
                        // The dispatcher will limit parallelism to 16 concurrent tasks
                        // Order: critical previous, critical next, then others
                        val allIndices = criticalPrevious + criticalNext + otherIndices
                        val jobs =
                            allIndices.map { index ->
                                val priority =
                                    when {
                                        index in criticalPrevious -> "critical_previous"
                                        index in criticalNext -> "critical_next"
                                        else -> "other"
                                    }
                                playerServiceScope.launch(mediaItemDispatcher) {
                                    loadMediaItem(index, priority)
                                }
                            }

                        // Wait for all jobs to complete (optional - they run in background anyway)
                        // This allows us to verify completion if needed
                        if (isLargePlaylist) {
                            // For large playlists, don't wait - let them load in background
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Large playlist: ${jobs.size} MediaItems loading in parallel background",
                            )
                        } else {
                            // For smaller playlists, wait briefly to ensure critical tracks load
                            kotlinx.coroutines.delay(100) // Small delay to let priority tracks start
                        }

                        android.util.Log.i(
                            "AudioPlayerService",
                            "All ${filePaths.size} MediaItems scheduled for parallel loading " +
                                "(critical previous: ${criticalPrevious.size}, " +
                                "critical next: ${criticalNext.size}, others: ${otherIndices.size})",
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        android.util.Log.d("AudioPlayerService", "MediaItems loading job cancelled")
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Error loading remaining MediaItems asynchronously", e)
                        // Don't throw - player is already working with first item
                    } finally {
                        // Apply initial position after all tracks are loaded
                        // This prevents ExoPlayer from switching to another track when MediaItems are added
                        // CRITICAL: Apply position for ANY target track, not just if it matches firstTrackIndex
                        if (initialTrackIndex != null && initialPosition != null && initialPosition > 0) {
                            withContext(Dispatchers.Main) {
                                val activePlayer = getActivePlayer()
                                // Wait for all tracks to be loaded
                                // CRITICAL: Wait longer and verify multiple times that all tracks are loaded
                                // ExoPlayer may temporarily show incorrect mediaItemCount during loading
                                var attempts = 0
                                var stableCount = 0
                                var lastCount = 0
                                while (attempts < 200) { // Increased timeout to 20 seconds
                                    val currentCount = activePlayer.mediaItemCount
                                    if (currentCount == filePaths.size) {
                                        // Count is correct, verify it's stable
                                        if (currentCount == lastCount) {
                                            stableCount++
                                            if (stableCount >= 5) {
                                                // Count has been stable for 5 checks (500ms), all tracks loaded
                                                android.util.Log.d(
                                                    "AudioPlayerService",
                                                    "All tracks loaded and stable: mediaItemCount=$currentCount (expected ${filePaths.size})",
                                                )
                                                break
                                            }
                                        } else {
                                            stableCount = 0
                                        }
                                    } else {
                                        stableCount = 0
                                    }
                                    lastCount = currentCount
                                    delay(100)
                                    attempts++
                                }

                                // Final verification that all tracks are loaded
                                val finalCount = activePlayer.mediaItemCount

                                // CRITICAL: Do NOT apply position if not all tracks are loaded
                                // This prevents incorrect track switching and actualTrackIndex reset
                                if (finalCount < filePaths.size) {
                                    android.util.Log.w(
                                        "AudioPlayerService",
                                        "Not all tracks loaded after waiting: mediaItemCount=$finalCount (expected ${filePaths.size}). Skipping position application to prevent incorrect track switching.",
                                    )
                                    return@withContext // Exit early, don't apply position
                                }

                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "All tracks confirmed loaded: mediaItemCount=$finalCount (expected ${filePaths.size}), proceeding with position application",
                                )

                                // Wait for player to be ready
                                attempts = 0
                                while (attempts < 50 &&
                                    activePlayer.playbackState != Player.STATE_READY &&
                                    activePlayer.playbackState != Player.STATE_BUFFERING
                                ) {
                                    delay(100)
                                    attempts++
                                }

                                // Apply position now that all tracks are loaded
                                try {
                                    android.util.Log.d(
                                        "AudioPlayerService",
                                        "Applying initial position: track=$initialTrackIndex, position=${initialPosition}ms, mediaItemCount=$finalCount (expected ${filePaths.size})",
                                    )

                                    // CRITICAL: Use filePaths.size for bounds check, not mediaItemCount
                                    // mediaItemCount may be incorrect during loading, but filePaths.size is always correct
                                    if (initialTrackIndex >= filePaths.size) {
                                        android.util.Log.e(
                                            "AudioPlayerService",
                                            "ERROR: Target track $initialTrackIndex is out of bounds (filePaths.size=${filePaths.size})! Cannot apply position.",
                                        )
                                        return@withContext
                                    }

                                    // Double-check that track is available in player
                                    if (initialTrackIndex >= activePlayer.mediaItemCount) {
                                        android.util.Log.e(
                                            "AudioPlayerService",
                                            "ERROR: Target track $initialTrackIndex >= mediaItemCount=${activePlayer.mediaItemCount} even though all tracks should be loaded! Cannot apply position.",
                                        )
                                        return@withContext
                                    }

                                    // Track is available, proceed with position application
                                    // CRITICAL: Always switch to target track first, then apply position
                                    // This ensures ExoPlayer correctly updates currentMediaItemIndex
                                    val currentIndex = activePlayer.currentMediaItemIndex
                                    android.util.Log.d(
                                        "AudioPlayerService",
                                        "Current track: $currentIndex, target: $initialTrackIndex, mediaItemCount: ${activePlayer.mediaItemCount}",
                                    )

                                    if (currentIndex != initialTrackIndex) {
                                        android.util.Log.d(
                                            "AudioPlayerService",
                                            "Switching from track $currentIndex to target track $initialTrackIndex",
                                        )

                                        // CRITICAL: Use CompletableDeferred to wait for onMediaItemTransition event
                                        // This is more reliable than polling currentMediaItemIndex
                                        val trackSwitchDeferred = CompletableDeferred<Int>()
                                        setPendingTrackSwitchDeferred?.invoke(trackSwitchDeferred)

                                        // CRITICAL: Use seekToDefaultPosition first to switch tracks
                                        // This is more reliable than seekTo with position 0
                                        try {
                                            activePlayer.seekToDefaultPosition(initialTrackIndex)
                                        } catch (e: Exception) {
                                            android.util.Log.w(
                                                "AudioPlayerService",
                                                "seekToDefaultPosition failed, trying seekTo: ${e.message}",
                                            )
                                            activePlayer.seekTo(initialTrackIndex, 0)
                                        }

                                        // Wait for onMediaItemTransition event instead of polling
                                        try {
                                            val actualIndex = trackSwitchDeferred.await()
                                            android.util.Log.d(
                                                "AudioPlayerService",
                                                "Successfully switched to track $actualIndex (expected $initialTrackIndex) via onMediaItemTransition event",
                                            )

                                            // Verify that we got the correct index
                                            if (actualIndex != initialTrackIndex) {
                                                android.util.Log.w(
                                                    "AudioPlayerService",
                                                    "Track switch returned index $actualIndex instead of expected $initialTrackIndex",
                                                )
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.w(
                                                "AudioPlayerService",
                                                "Failed to wait for track switch event: ${e.message}, falling back to polling",
                                            )
                                            // Fallback to polling if deferred fails
                                            var switchAttempts = 0
                                            while (switchAttempts < 50) {
                                                val newIndex = activePlayer.currentMediaItemIndex
                                                val isReady =
                                                    activePlayer.playbackState == Player.STATE_READY ||
                                                        activePlayer.playbackState == Player.STATE_BUFFERING

                                                if (newIndex == initialTrackIndex && isReady) {
                                                    android.util.Log.d(
                                                        "AudioPlayerService",
                                                        "Successfully switched to track $initialTrackIndex after $switchAttempts attempts (fallback)",
                                                    )
                                                    break
                                                }
                                                delay(100)
                                                switchAttempts++
                                            }
                                        }
                                    }

                                    // Now seek to the specific position within the target track
                                    android.util.Log.d(
                                        "AudioPlayerService",
                                        "Applying position ${initialPosition}ms to track $initialTrackIndex (currentIndex=${activePlayer.currentMediaItemIndex})",
                                    )

                                    // Use seekTo with both index and position
                                    activePlayer.seekTo(initialTrackIndex, initialPosition)

                                    // Wait for position to be applied and index to stabilize
                                    delay(500)

                                    // Verify final state
                                    val finalIndex = activePlayer.currentMediaItemIndex
                                    val finalPosition = activePlayer.currentPosition

                                    android.util.Log.i(
                                        "AudioPlayerService",
                                        "Initial position applied: targetTrack=$initialTrackIndex, targetPosition=${initialPosition}ms, finalIndex=$finalIndex, finalPosition=${finalPosition}ms",
                                    )

                                    if (finalIndex != initialTrackIndex) {
                                        android.util.Log.e(
                                            "AudioPlayerService",
                                            "ERROR: Final index ($finalIndex) differs from target ($initialTrackIndex) after seekTo! This will cause incorrect chapter number display.",
                                        )
                                        // Try one more time as last resort with seekToDefaultPosition
                                        try {
                                            activePlayer.seekToDefaultPosition(initialTrackIndex)
                                            delay(300)
                                            activePlayer.seekTo(initialTrackIndex, initialPosition)
                                            delay(500)
                                        } catch (e: Exception) {
                                            android.util.Log.e(
                                                "AudioPlayerService",
                                                "Retry failed: ${e.message}",
                                            )
                                        }
                                        val lastIndex = activePlayer.currentMediaItemIndex
                                        android.util.Log.w(
                                            "AudioPlayerService",
                                            "After retry: currentMediaItemIndex=$lastIndex (expected=$initialTrackIndex)",
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w(
                                        "AudioPlayerService",
                                        "Failed to apply initial position after all tracks loaded: ${e.message}",
                                    )
                                }
                            }
                        }

                        // Clear job reference when done
                        activeLoadingJob = null
                    }
                }

            // Verify playlist order after all items are loaded (in separate coroutine to not block)
            playerServiceScope.launch {
                try {
                    // Wait a bit for items to load
                    delay(2000)
                    withContext(Dispatchers.Main) {
                        val activePlayer = getActivePlayer()
                        if (activePlayer.mediaItemCount == filePaths.size) {
                            var orderMismatchCount = 0
                            for (i in 0 until activePlayer.mediaItemCount) {
                                val item = activePlayer.getMediaItemAt(i)
                                val expectedPath = filePaths[i]
                                val actualPath = item.localConfiguration?.uri?.path
                                if (actualPath != expectedPath) {
                                    orderMismatchCount++
                                    android.util.Log.w(
                                        "AudioPlayerService",
                                        "Playlist order mismatch at index $i: expected $expectedPath, got $actualPath",
                                    )
                                }
                            }
                            if (orderMismatchCount == 0) {
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Playlist order verified: all ${activePlayer.mediaItemCount} items are in correct order",
                                )
                            } else {
                                android.util.Log.w(
                                    "AudioPlayerService",
                                    "Playlist order verification found $orderMismatchCount mismatches out of ${activePlayer.mediaItemCount} items",
                                )
                            }
                        } else {
                            android.util.Log.w(
                                "AudioPlayerService",
                                "Playlist size mismatch: expected ${filePaths.size}, got ${activePlayer.mediaItemCount}",
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Error loading remaining MediaItems asynchronously", e)
                    // Don't throw - player is already working with first item
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to prepare playback", e)
            throw e
        }
    }

    /**
     * Creates a MediaSource for a specific file index.
     * Helper method to avoid code duplication.
     */
    private fun createMediaSourceForIndex(
        filePaths: List<String>,
        index: Int,
        metadata: Map<String, String>?,
        dataSourceFactory: SimpleMediaDataSourceFactory,
    ): MediaSource {
        val path = filePaths[index]

        // Determine if path is a URL or file path
        val isUrl = path.startsWith("http://") || path.startsWith("https://")
        val uri: Uri

        if (isUrl) {
            // Handle HTTP(S) URL
            uri = Uri.parse(path)
            android.util.Log.d("AudioPlayerService", "Creating MediaItem $index from URL: $path")
        } else {
            // Handle local file path
            val file = File(path)
            if (!file.exists()) {
                android.util.Log.w("AudioPlayerService", "File does not exist: $path")
            }
            uri = Uri.fromFile(file)
            android.util.Log.d("AudioPlayerService", "Creating MediaItem $index from file: $path")
        }

        val metadataBuilder =
            androidx.media3.common.MediaMetadata
                .Builder()

        val fileName =
            if (isUrl) {
                val urlPath = Uri.parse(path).lastPathSegment ?: "Track ${index + 1}"
                urlPath.substringBeforeLast('.', urlPath)
            } else {
                File(path).nameWithoutExtension
            }
        val providedTitle = metadata?.get("title") ?: metadata?.get("trackTitle")
        val providedArtist = metadata?.get("artist") ?: metadata?.get("author")
        val providedAlbum = metadata?.get("album") ?: metadata?.get("bookTitle")

        // Get flavor suffix for title
        val flavorSuffix = getFlavorSuffix()
        val flavorText = if (flavorSuffix.isEmpty()) "" else " - $flavorSuffix"

        // Always add flavor suffix to title for quick settings player
        val baseTitle = providedTitle ?: fileName.ifEmpty { "Track ${index + 1}" }
        val titleWithFlavor = if (flavorText.isEmpty()) baseTitle else "$baseTitle$flavorText"
        metadataBuilder.setTitle(titleWithFlavor)

        if (providedArtist != null) {
            metadataBuilder.setArtist(providedArtist)
        }

        if (providedAlbum != null) {
            metadataBuilder.setAlbumTitle(providedAlbum)
        }

        val artworkUriString = metadata?.get("artworkUri")?.takeIf { it.isNotEmpty() }
        if (artworkUriString != null) {
            try {
                val artworkUri = android.net.Uri.parse(artworkUriString)
                metadataBuilder.setArtworkUri(artworkUri)
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Failed to parse artwork URI: $artworkUriString", e)
            }
        }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
                .build()

        val sourceFactory = dataSourceFactory.createDataSourceFactoryForUri(uri)
        return ProgressiveMediaSource
            .Factory(sourceFactory)
            .createMediaSource(mediaItem)
    }

    /**
     * DataSource factory for media playback with caching and network support.
     * Private inner class to avoid build duplication issues.
     */
    private inner class SimpleMediaDataSourceFactory : DataSource.Factory {
        // OkHttp client for network requests
        private val okHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        private val okHttpFactory by lazy {
            OkHttpDataSource.Factory(okHttpClient)
        }

        private val defaultFactory by lazy {
            DefaultDataSource.Factory(context)
        }

        private val cacheFactory by lazy {
            CacheDataSource
                .Factory()
                .setCache(mediaCache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, okHttpFactory))
                .setCacheWriteDataSinkFactory(
                    CacheDataSink
                        .Factory()
                        .setCache(mediaCache)
                        .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
                ).setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        override fun createDataSource(): DataSource = defaultFactory.createDataSource()

        fun createDataSourceFactoryForUri(uri: Uri): DataSource.Factory {
            val isNetworkUri = uri.scheme == "http" || uri.scheme == "https"
            val isLocalFile = uri.scheme == "file" || uri.scheme == null

            return when {
                isNetworkUri -> cacheFactory
                isLocalFile -> defaultFactory
                else -> defaultFactory
            }
        }
    }
}
