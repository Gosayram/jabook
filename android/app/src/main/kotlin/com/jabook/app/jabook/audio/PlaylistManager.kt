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
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.jabook.app.jabook.audio.ErrorHandler
import com.jabook.app.jabook.audio.SavedPlaybackState
import com.jabook.app.jabook.compose.core.di.AppDispatchers
import com.jabook.app.jabook.compose.core.util.rethrowCancellation
import com.jabook.app.jabook.core.network.NetworkRuntimePolicy
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    // getNotificationManager callback removed - MediaSession handles notification updates automatically
    private val playerServiceScope: CoroutineScope,
    private val mediaItemDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val dispatchers: AppDispatchers,
    private val getFlavorSuffix: () -> String,
    private val setPendingTrackSwitchDeferred: ((CompletableDeferred<Int>) -> Unit)? = null, // Callback to set deferred in PlayerListener
    private val durationManager: DurationManager,
    private val playerPersistenceManager: PlayerPersistenceManager,
    private val playbackController: PlaybackController,
    private val getCurrentTrackIndex: () -> Int = { 0 }, // fallback
    // Shared Hilt client: brings DoH DNS + Brotli + browser-like UA/headers. Default keeps unit tests simple.
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) {
    private val preloadExecutor by lazy {
        PlaylistPreloadExecutor(mainDispatcher = dispatchers.main)
    }

    // DefaultDataSource keeps local files, content URIs and Android resources on Media3's native
    // sources. Its base factory is used for network media, where we add the shared playback cache.
    private val playbackDataSourceFactory: DataSource.Factory by lazy {
        val networkFactory =
            OkHttpDataSource.Factory(
                // Derive from the shared client (keeps DoH+Brotli+UA interceptors and the
                // connection pool/dispatcher) and only adjust the media timeouts.
                okHttpClient
                    .newBuilder()
                    // Media is cached by Media3 CacheDataSource (mediaCache); keep the shared
                    // client's HTTP cache out of the audio path (mirrors JabookApplication Coil fix).
                    .cache(null)
                    .connectTimeout(NetworkRuntimePolicy.AUDIO_MEDIA_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(NetworkRuntimePolicy.AUDIO_MEDIA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(NetworkRuntimePolicy.AUDIO_MEDIA_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )
        // FLAG_IGNORE_CACHE_ON_ERROR keeps streaming resilient: a failed cache read is bypassed
        // and re-fetched. CacheErrorHealer hooks CacheDataSource.EventListener so the suspect
        // entry is dropped instead of rotting in the cache; transient errors cost one re-download
        // and LRU eviction remains the size-based cleanup path.
        val cachedNetworkFactory =
            DataSource.Factory {
                val healer = CacheErrorHealer(mediaCache)
                ResolvingDataSource(
                    CacheDataSource(
                        mediaCache,
                        networkFactory.createDataSource(),
                        FileDataSource(),
                        CacheDataSink.Factory().setCache(mediaCache).createDataSink(),
                        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                        healer,
                        CacheKeyFactory.DEFAULT,
                    ),
                ) { dataSpec ->
                    // Runs before the wrapped open(), so the healer has the key of THIS request
                    // when onCacheIgnored fires from inside CacheDataSource.open().
                    healer.onOpen(dataSpec)
                    dataSpec
                }
            }

        DefaultDataSource.Factory(context, cachedNetworkFactory)
    }

    // State managed by PlaylistManager
    @Volatile var currentFilePaths: List<String>? = null
        internal set
    var currentPlaylistItems: List<PlaylistItem>? = null
        internal set
    var currentMetadata: Map<String, String>? = null
        internal set

    @Volatile var currentGroupPath: String? = null
        internal set
    internal var isPlaylistLoading = false
        internal set
    internal var currentLoadingPlaylist: List<String>? = null
        internal set
    var lastPlaylistLoadTime: Long = 0L
        private set

    @Volatile var lastCompletedTrackIndex: Int = -1

    @Volatile var isBookCompleted = false

    @Volatile var actualTrackIndex: Int = 0

    // Progress tracking for playlist loading
    data class PlaylistLoadProgress(
        val loaded: Int,
        val total: Int,
        val phase: Phase,
    ) {
        enum class Phase {
            IDLE,
            LOADING_FIRST,
            LOADING_CRITICAL,
            LOADING_BACKGROUND,
            DONE,
        }

        val fraction: Float get() = if (total > 0) loaded.toFloat() / total else 0f
    }

    private val _loadProgress = MutableStateFlow(PlaylistLoadProgress(0, 0, PlaylistLoadProgress.Phase.IDLE))
    val loadProgress: StateFlow<PlaylistLoadProgress> = _loadProgress

    // Saved state for restoration
    var savedPlaybackState: SavedPlaybackState? = null

    // Track active loading job to prevent duplicates
    @Volatile
    private var activeLoadingJob: Job? = null

    // Monotonic generation id for async playlist loading.
    // Used to prevent stale background jobs from mutating the player after a newer setPlaylist call.
    @Volatile
    private var playlistLoadGeneration: Long = 0L

    // Mutex to synchronize playlist loading operations and prevent race conditions
    private val playlistLoadMutex = Mutex()
    private val playlistLoadCoordinator by lazy {
        PlaylistLoadCoordinator(
            setLoading = { isPlaylistLoading = it },
            setCurrentLoadingPlaylist = { currentLoadingPlaylist = it },
            setLastLoadTimestampMs = { lastPlaylistLoadTime = it },
            cancelAndClearActiveLoadingJob = { cancelAndClearActiveLoadingJob() },
            nextGeneration = { ++playlistLoadGeneration },
        )
    }

    private fun cancelAndClearActiveLoadingJob() {
        activeLoadingJob?.cancel()
        activeLoadingJob = null
    }

    /**
     * Prevents an old incremental load from mutating a player after CrossFadePlayer swaps it.
     */
    public fun cancelAsyncLoadingForPlayerSwitch() {
        playlistLoadGeneration += 1L
        cancelAndClearActiveLoadingJob()
    }

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
     * @param callback Optional callback to notify when playlist is ready
     */
    public fun setPlaylist(
        filePaths: List<String>,
        metadata: Map<String, String>? = null,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        groupPath: String? = null,
        callback: ((Boolean, Exception?) -> Unit)? = null,
        playlistItems: List<PlaylistItem> = filePaths.map(::PlaylistItem),
    ) {
        // CRITICAL: Use mutex to prevent race conditions when multiple setPlaylist calls happen simultaneously
        // This ensures only one playlist loads at a time and prevents state corruption
        LogUtils.d(
            "AudioPlayerService",
            "setPlaylist called: ${filePaths.size} items, initialTrackIndex=$initialTrackIndex, initialPosition=$initialPosition",
        )
        playerServiceScope.launch {
            try {
                playlistLoadMutex.withLock {
                    LogUtils.d(
                        "AudioPlayerService",
                        "Acquired playlistLoadMutex lock for setPlaylist",
                    )
                    // The mutex serializes requests; a later explicit selection must replace
                    // the prior one rather than report a successful no-op.
                    val loadGeneration = playlistLoadCoordinator.begin(filePaths)

                    try {
                        setPlaylistInternal(
                            filePaths = filePaths,
                            playlistItems = playlistItems,
                            metadata = metadata,
                            initialTrackIndex = initialTrackIndex,
                            initialPosition = initialPosition,
                            groupPath = groupPath,
                            callback = callback,
                            loadGeneration = loadGeneration,
                        )
                        playlistLoadCoordinator.finish()
                        LogUtils.d(
                            "AudioPlayerService",
                            "Released playlistLoadMutex lock after setPlaylist",
                        )
                    } catch (e: CancellationException) {
                        playlistLoadCoordinator.finish()
                        throw e
                    } catch (e: Exception) {
                        playlistLoadCoordinator.fail()
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.e(
                    "AudioPlayerService",
                    "Error in setPlaylist mutex block: ${e.message}",
                    e,
                )
                callback?.invoke(false, e)
            }
        }
    }

    /**
     * Internal method to set playlist - called within mutex lock.
     * Separated to keep the mutex-protected entry point clean.
     */
    private suspend fun setPlaylistInternal(
        filePaths: List<String>,
        playlistItems: List<PlaylistItem>,
        metadata: Map<String, String>? = null,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        groupPath: String? = null,
        callback: ((Boolean, Exception?) -> Unit)? = null,
        loadGeneration: Long,
    ) {
        // Clear saved state to prevent restoration from interfering with new playlist
        savedPlaybackState = null

        // Reset book completion flag when setting new playlist
        isBookCompleted = false
        lastCompletedTrackIndex = -1 // Reset saved index for new book

        val sessionState =
            PlaylistSessionStatePolicy.buildSnapshot(
                filePaths = filePaths,
                initialTrackIndex = initialTrackIndex,
            )
        require(playlistItems.map(PlaylistItem::path) == sessionState.filePaths) {
            "playlistItems must match the playlist paths"
        }

        // Initialize actualTrackIndex from normalized initial track index.
        actualTrackIndex = sessionState.normalizedTrackIndex
        LogUtils.d(
            "AudioPlayerService",
            "Initialized actualTrackIndex to $actualTrackIndex (from initialTrackIndex=$initialTrackIndex)",
        )

        // Clear duration cache using DurationManager
        durationManager.clearCache()

        // Keep the persisted/scanner-provided chapter order; the UI uses the same order.
        val playlistPaths = sessionState.filePaths
        currentFilePaths = playlistPaths
        currentPlaylistItems = playlistItems

        LogUtils.i(
            "AudioPlayerService",
            "Loaded ${playlistPaths.size} files. " +
                "Initial track: $actualTrackIndex. " +
                "First: ${playlistPaths.firstOrNull()?.substringAfterLast('/')}",
        )
        currentMetadata = metadata
        currentGroupPath = groupPath

        // Log file paths order for debugging
        LogUtils.d(
            "AudioPlayerService",
            "Stored filePaths (first 5): ${playlistPaths.take(5).mapIndexed {
                i: Int,
                path: String,
                ->
                "$i=${path.substringAfterLast('/')}"
            }.joinToString(", ")}",
        )

        // Save groupPath to SharedPreferences for fallback position saving
        if (groupPath != null) {
            playerPersistenceManager.saveGroupPathToSharedPreferences(groupPath)
        }

        LogUtils.d(
            "AudioPlayerService",
            "Setting playlist with ${filePaths.size} items, initialTrackIndex=$initialTrackIndex, initialPosition=$initialPosition, groupPath=$groupPath",
        )

        try {
            preparePlaybackOptimizedInternal(
                filePaths = playlistPaths,
                playlistItems = playlistItems,
                metadata = metadata,
                initialTrackIndex = sessionState.normalizedTrackIndex,
                initialPosition = initialPosition,
                loadGeneration = loadGeneration,
            )
            LogUtils.d("AudioPlayerService", "Playlist prepared successfully")

            playerPersistenceManager.savePersistedPlayerState(
                PlayerPersistenceManager.PersistedPlayerState(
                    groupPath = groupPath.orEmpty(),
                    filePaths = playlistPaths,
                    playlistItems = playlistItems,
                    currentIndex = sessionState.normalizedTrackIndex,
                    currentPosition = (initialPosition ?: 0L).coerceAtLeast(0L),
                    metadata = metadata,
                    // Persist speed so onPlaybackResumption restores it after process death
                    // (audiobook users typically run >1.0x).
                    speed =
                        runCatching { playbackController.getSpeed() }
                            .getOrDefault(PlayerPersistenceManager.DEFAULT_PLAYBACK_SPEED),
                ),
            )

            // Call callback to notify caller
            withContext(dispatchers.main) {
                callback?.invoke(true, null)
            }

            // Apply initial position if needed (in background, non-blocking).
            val initialPositionDecision =
                PlaylistInitialPositionPolicy.decidePostPrepare(
                    requestedTrackIndex = sessionState.normalizedTrackIndex,
                    requestedPositionMs = initialPosition,
                    playlistSize = filePaths.size,
                )
            if (initialPositionDecision.shouldScheduleDeferredApply) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Target track ($initialTrackIndex) differs from first loaded track " +
                        "(${initialPositionDecision.normalizedTargetTrackIndex}), scheduling position application",
                )
                playerServiceScope.launch {
                    playbackController.applyInitialPosition(
                        sessionState.normalizedTrackIndex,
                        requireNotNull(initialPosition),
                        filePaths.size,
                    )
                }
            } else if (initialTrackIndex != null && initialPosition != null && initialPosition > 0) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Target track ($initialTrackIndex) is first loaded track, position already applied in preparePlaybackOptimized",
                )
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to prepare playback", e)
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "preparePlayback failed")
            throw e // Re-throw to let finally block handle cleanup
        }
    }

    /**
     * Prepares playback with optimized loading strategy.
     *
     * For small playlists (<50 tracks): Uses synchronous loading like Rhythm for simplicity and reliability.
     * For large playlists (>=50 tracks): Uses async lazy loading to avoid blocking startup.
     *
     * @param filePaths List of file paths or URLs
     * @param metadata Optional metadata
     * @param initialTrackIndex Optional track index to load first (for saved position). If null, loads first track (index 0).
     * @param initialPosition Optional position in milliseconds to seek to after loading
     */
    public suspend fun preparePlaybackOptimized(
        filePaths: List<String>,
        playlistItems: List<PlaylistItem> = filePaths.map(::PlaylistItem),
        metadata: Map<String, String>?,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        loadGeneration: Long = playlistLoadGeneration,
    ) {
        playlistLoadMutex.withLock {
            preparePlaybackOptimizedInternal(
                filePaths = filePaths,
                playlistItems = playlistItems,
                metadata = metadata,
                initialTrackIndex = initialTrackIndex,
                initialPosition = initialPosition,
                loadGeneration = loadGeneration,
            )
        }
    }

    private suspend fun preparePlaybackOptimizedInternal(
        filePaths: List<String>,
        playlistItems: List<PlaylistItem>,
        metadata: Map<String, String>?,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        loadGeneration: Long = playlistLoadGeneration,
    ) = withContext(dispatchers.io) {
        val playlistLoadStartTime = System.currentTimeMillis()
        val playlistSize = filePaths.size
        if (playlistSize == 0) {
            withContext(dispatchers.main) {
                val activePlayer = getActivePlayer()
                activePlayer.playWhenReady = false
                activePlayer.clearMediaItems()
            }
            LogUtils.w("AudioPlayerService", "Ignoring empty playlist request")
            return@withContext
        }
        val strategy = PlaylistLoadStrategyPolicy.select(totalTracks = playlistSize)

        LogUtils.i(
            "AudioPlayerService",
            "Starting playlist load: totalTracks=$playlistSize, targetTrack=$initialTrackIndex, " +
                "targetPosition=${initialPosition}ms, strategy=$strategy",
        )

        // Initialize progress
        _loadProgress.update { PlaylistLoadProgress(0, playlistSize, PlaylistLoadProgress.Phase.LOADING_FIRST) }

        try {
            when (strategy) {
                PlaylistLoadStrategy.SYNC -> {
                    // Use simplified synchronous loading for small playlists (like Rhythm)
                    preparePlaybackSynchronous(
                        filePaths,
                        playlistItems,
                        metadata,
                        initialTrackIndex,
                        initialPosition,
                        playlistLoadStartTime,
                    )
                }

                PlaylistLoadStrategy.ASYNC -> {
                    // Use optimized async loading for large playlists
                    preparePlaybackAsync(
                        filePaths = filePaths,
                        playlistItems = playlistItems,
                        metadata = metadata,
                        initialTrackIndex = initialTrackIndex,
                        initialPosition = initialPosition,
                        loadStartTime = playlistLoadStartTime,
                        loadGeneration = loadGeneration,
                    )
                }
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to prepare playback", e)
            _loadProgress.update { PlaylistLoadProgress(0, 0, PlaylistLoadProgress.Phase.IDLE) }
            throw e
        }
    }

    /**
     * Simplified synchronous playlist loading for small playlists (<50 tracks).
     * Inspired by Rhythm's simple approach: create all MediaItems and set them at once.
     *
     * This approach is simpler and more reliable for small playlists, avoiding
     * complex async coordination and race conditions.
     */
    private suspend fun preparePlaybackSynchronous(
        filePaths: List<String>,
        playlistItems: List<PlaylistItem>,
        metadata: Map<String, String>?,
        initialTrackIndex: Int?,
        initialPosition: Long?,
        loadStartTime: Long,
    ) = withContext(mediaItemDispatcher) {
        LogUtils.d("AudioPlayerService", "Using synchronous loading for small playlist (${filePaths.size} tracks)")
        // Create all MediaSources synchronously so the configured cache route is retained.
        val mediaSources =
            filePaths.mapIndexed { index, _ ->
                createMediaSourceForIndex(playlistItems, index, metadata, playbackDataSourceFactory)
            }

        // Apply to player on main thread
        withContext(dispatchers.main) {
            val activePlayer = getActivePlayer()
            activePlayer.playWhenReady = false

            // Clear any existing items
            activePlayer.clearMediaItems()

            // Use setMediaSources with startIndex and startPosition.
            // This is simpler and more reliable than async loading
            val startIndex = (initialTrackIndex ?: 0).coerceIn(0, mediaSources.size - 1)
            val startPosition = (initialPosition ?: 0).coerceAtLeast(0)

            LogUtils.d(
                "AudioPlayerService",
                "Setting ${mediaSources.size} MediaSources synchronously: startIndex=$startIndex, startPosition=${startPosition}ms",
            )

            activePlayer.setMediaSources(mediaSources, startIndex, startPosition)
            activePlayer.prepare()

            val loadDuration = System.currentTimeMillis() - loadStartTime
            LogUtils.i(
                "AudioPlayerService",
                "Synchronous playlist loaded: ${mediaSources.size} tracks in ${loadDuration}ms " +
                    "(startIndex=$startIndex, startPosition=${startPosition}ms)",
            )

            // Mark loading as complete for synchronous loading
            _loadProgress.update { PlaylistLoadProgress(mediaSources.size, mediaSources.size, PlaylistLoadProgress.Phase.DONE) }
        }
    }

    /**
     * Optimized asynchronous playlist loading for large playlists (>=50 tracks).
     *
     * CRITICAL OPTIMIZATION: Only creates the first MediaItem synchronously.
     * Remaining items are added asynchronously in background to avoid blocking startup.
     * This dramatically speeds up player initialization, especially for large playlists.
     */
    private suspend fun preparePlaybackAsync(
        filePaths: List<String>,
        playlistItems: List<PlaylistItem>,
        metadata: Map<String, String>?,
        initialTrackIndex: Int?,
        initialPosition: Long?,
        loadStartTime: Long,
        loadGeneration: Long,
    ) = withContext(dispatchers.io) {
        try {
            LogUtils.d("AudioPlayerService", "Using async loading for large playlist (${filePaths.size} tracks)")
            // Determine which track to load first
            // CRITICAL: Always load track 0 first, then switch to target track after all tracks are loaded
            // This ensures ExoPlayer has a valid playlist structure before switching tracks
            val firstTrackIndex = 0
            LogUtils.d(
                "AudioPlayerService",
                "Loading first track: index=$firstTrackIndex (target=$initialTrackIndex, total=${filePaths.size})",
            )

            // CRITICAL: Create only the first MediaSource synchronously for fast startup
            // This allows player to start immediately while other tracks load in background
            val firstMediaSource =
                createMediaSourceForIndex(
                    playlistItems,
                    firstTrackIndex,
                    metadata,
                    playbackDataSourceFactory,
                )

            // Set first MediaSource and prepare player immediately
            withContext(dispatchers.main) {
                val activePlayer = getActivePlayer()
                activePlayer.playWhenReady = false

                // Clear any existing items first
                activePlayer.clearMediaItems()

                // CRITICAL: Add first item at the correct index to ensure it's the current track
                // This prevents ExoPlayer from switching to a different track when other tracks load
                activePlayer.addMediaSource(firstTrackIndex, firstMediaSource)
                activePlayer.prepare()

                // Report initial progress after first track loaded
                _loadProgress.update { PlaylistLoadProgress(1, filePaths.size, PlaylistLoadProgress.Phase.LOADING_FIRST) }

                // CRITICAL: Position will be applied after all tracks are loaded
                // This prevents ExoPlayer from switching to another track when MediaItems are added
                if (initialTrackIndex != null && initialPosition != null && initialPosition > 0) {
                    LogUtils.d(
                        "AudioPlayerService",
                        "Target track is $initialTrackIndex (first loaded: $firstTrackIndex), " +
                            "will apply position ${initialPosition}ms after all tracks are loaded",
                    )
                    // Apply position after all tracks are loaded to prevent track switching
                    // This is handled in the async loading coroutine after all MediaItems are added
                }

                LogUtils.i(
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
                    var completed = false
                    try {
                        if (!isLoadGenerationActive(loadGeneration)) {
                            LogUtils.d(
                                "AudioPlayerService",
                                "Skipping stale async load job before start (generation=$loadGeneration, active=$playlistLoadGeneration)",
                            )
                            return@launch
                        }
                        LogUtils.d(
                            "AudioPlayerService",
                            "Starting async MediaItems loading (previous job cancelled if existed)",
                        )
                        val remainingIndices = filePaths.indices.filter { it != firstTrackIndex }
                        LogUtils.d(
                            "AudioPlayerService",
                            "Loading ${remainingIndices.size} remaining MediaItems in queue order",
                        )
                        var loadedCount = 1

                        // Sources are built off the main thread, but inserted serially. Inserting a
                        // later index before its predecessor can fail and silently drop a chapter.
                        for (index in remainingIndices) {
                            if (!isLoadGenerationActive(loadGeneration)) return@launch
                            val loadStartTime = System.currentTimeMillis()
                            val filePath = filePaths[index]
                            val fileName = filePath.substringAfterLast('/')
                            try {
                                LogUtils.d(
                                    "AudioPlayerService",
                                    "Loading track $index: $fileName",
                                )
                                val mediaSource =
                                    createMediaSourceForIndex(
                                        playlistItems,
                                        index,
                                        metadata,
                                        playbackDataSourceFactory,
                                    )

                                withContext(dispatchers.main) {
                                    if (!isLoadGenerationActive(loadGeneration)) return@withContext
                                    val activePlayer = getActivePlayer()
                                    activePlayer.addMediaSource(index, mediaSource)
                                    loadedCount++
                                    val loadDuration = System.currentTimeMillis() - loadStartTime
                                    LogUtils.i(
                                        "AudioPlayerService",
                                        "Loaded track $index: $fileName (${loadDuration}ms, playlist size: ${activePlayer.mediaItemCount})",
                                    )
                                    _loadProgress.update {
                                        PlaylistLoadProgress(
                                            loadedCount,
                                            filePaths.size,
                                            PlaylistLoadProgress.Phase.LOADING_CRITICAL,
                                        )
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val loadDuration = System.currentTimeMillis() - loadStartTime
                                LogUtils.e(
                                    "AudioPlayerService",
                                    "Failed to load track $index: $fileName (${loadDuration}ms): ${e.message}",
                                    e,
                                )
                                // Skip failed track and continue with remaining chapters instead of aborting
                                // the whole load and leaving a sparse timeline with holes.
                                continue
                            }
                        }
                        completed = true

                        LogUtils.i(
                            "AudioPlayerService",
                            "All ${filePaths.size} MediaItems loaded in queue order",
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        LogUtils.d("AudioPlayerService", "MediaItems loading job cancelled")
                        throw e
                    } catch (e: Exception) {
                        LogUtils.e("AudioPlayerService", "Error loading remaining MediaItems asynchronously", e)
                        // Don't throw - player is already working with first item
                    } finally {
                        val isCurrentGeneration = isLoadGenerationActive(loadGeneration)
                        val asyncInitialPositionDecision =
                            PlaylistAsyncInitialPositionPolicy.decide(
                                isCurrentGeneration = isCurrentGeneration && completed,
                                initialTrackIndex = initialTrackIndex,
                                initialPositionMs = initialPosition,
                            )
                        // Apply initial position after all tracks are loaded
                        // This prevents ExoPlayer from switching to another track when MediaItems are added
                        if (asyncInitialPositionDecision.shouldApply) {
                            withContext(dispatchers.main) {
                                applyInitialPositionAfterLoad(
                                    requireNotNull(initialTrackIndex),
                                    requireNotNull(initialPosition),
                                    filePaths.size,
                                    loadStartTime,
                                )
                            }
                        } else if (!isCurrentGeneration) {
                            LogUtils.d(
                                "AudioPlayerService",
                                "Skipping initial position apply for stale async load generation=$loadGeneration (active=$playlistLoadGeneration)",
                            )
                        }

// Clear job reference when done
                        if (activeLoadingJob === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                            activeLoadingJob = null
                        }
                        if (!completed) {
                            LogUtils.w(
                                "AudioPlayerService",
                                "All remaining tracks failed to load — playback may be incomplete",
                            )
                        }
                        // Mark loading as complete (only if still current generation)
                        if (isLoadGenerationActive(loadGeneration) && completed) {
                            _loadProgress.update { PlaylistLoadProgress(filePaths.size, filePaths.size, PlaylistLoadProgress.Phase.DONE) }
                        }
                    }
                }

            // Verify playlist order after all items are loaded (in separate coroutine to not block)
            playerServiceScope.launch {
                try {
                    val initialDecision =
                        PlaylistOrderVerificationPolicy.decide(
                            isCurrentGeneration = isLoadGenerationActive(loadGeneration),
                        )
                    if (!initialDecision.shouldProceed) {
                        return@launch
                    }
                    // Wait a bit for items to load
                    delay(initialDecision.waitBeforeVerificationMs)
                    val postDelayDecision =
                        PlaylistOrderVerificationPolicy.decide(
                            isCurrentGeneration = isLoadGenerationActive(loadGeneration),
                        )
                    if (!postDelayDecision.shouldProceed) {
                        return@launch
                    }
                    withContext(dispatchers.main) {
                        val activePlayer = getActivePlayer()
                        val actualPaths =
                            (0 until activePlayer.mediaItemCount).map { index ->
                                activePlayer
                                    .getMediaItemAt(index)
                                    .localConfiguration
                                    ?.uri
                                    ?.path
                            }
                        val verificationResult =
                            PlaylistOrderVerificationResultPolicy.evaluate(
                                expectedPaths = filePaths,
                                actualPaths = actualPaths,
                            )
                        if (!verificationResult.sizeMatches) {
                            LogUtils.w(
                                "AudioPlayerService",
                                "Playlist size mismatch: expected ${verificationResult.expectedSize}, got ${verificationResult.actualSize}",
                            )
                            return@withContext
                        }
                        for (mismatch in verificationResult.mismatches) {
                            LogUtils.w(
                                "AudioPlayerService",
                                "Playlist order mismatch at index ${mismatch.index}: expected ${mismatch.expectedPath}, got ${mismatch.actualPath}",
                            )
                        }
                        if (verificationResult.mismatchCount == 0) {
                            LogUtils.d(
                                "AudioPlayerService",
                                "Playlist order verified: all ${verificationResult.actualSize} items are in correct order",
                            )
                        } else {
                            LogUtils.w(
                                "AudioPlayerService",
                                "Playlist order verification found ${verificationResult.mismatchCount} mismatches out of ${verificationResult.actualSize} items",
                            )
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e("AudioPlayerService", "Error loading remaining MediaItems asynchronously", e)
                    // Don't throw - player is already working with first item
                }
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to prepare playback", e)
            throw e
        }
    }

    private fun isLoadGenerationActive(generation: Long): Boolean = playlistLoadGeneration == generation

    /**
     * Current async-load generation. Captured by crossfade book switches so the
     * completion callback can detect a racing setPlaylist and skip stale writes.
     */
    public fun currentGeneration(): Long = playlistLoadGeneration

    /** True when [generation] is still the active async-load generation. */
    public fun isGenerationCurrent(generation: Long): Boolean = isLoadGenerationActive(generation)

    /**
     * Applies initial position after all tracks are loaded.
     * Simplified and extracted from the complex inline logic.
     */
    private suspend fun applyInitialPositionAfterLoad(
        initialTrackIndex: Int,
        initialPosition: Long,
        expectedTrackCount: Int,
        playlistLoadStartTime: Long,
    ) {
        val activePlayer = getActivePlayer()

        LogUtils.d(
            "AudioPlayerService",
            "Applying initial position: track=$initialTrackIndex, position=${initialPosition}ms, expectedCount=$expectedTrackCount",
        )

        // Wait for all tracks to be loaded with stability check
        if (!waitForAllTracksLoaded(activePlayer, expectedTrackCount, playlistLoadStartTime)) {
            LogUtils.w(
                "AudioPlayerService",
                "Not all tracks loaded, skipping position application",
            )
            return // Exit early if tracks didn't load
        }

        // Wait for player to be ready
        waitForPlayerReady(activePlayer)

        // Apply position with validation
        try {
            val trackIndexValidation =
                PlaylistTrackIndexValidationPolicy.validate(
                    trackIndex = initialTrackIndex,
                    expectedCount = expectedTrackCount,
                    playerItemCount = activePlayer.mediaItemCount,
                )
            if (!trackIndexValidation.isValid) {
                when (trackIndexValidation.failure) {
                    PlaylistTrackIndexValidationFailure.OUT_OF_EXPECTED_BOUNDS -> {
                        LogUtils.e(
                            "AudioPlayerService",
                            "ERROR: Target track $initialTrackIndex is out of bounds (expected count=$expectedTrackCount)!",
                        )
                    }
                    PlaylistTrackIndexValidationFailure.OUT_OF_PLAYER_BOUNDS -> {
                        LogUtils.e(
                            "AudioPlayerService",
                            "ERROR: Target track $initialTrackIndex >= mediaItemCount=${activePlayer.mediaItemCount}!",
                        )
                    }
                    null -> {
                        LogUtils.e("AudioPlayerService", "Track index validation failed, skipping position application")
                    }
                }
                return
            }

            // Switch to target track if needed
            // Note: switchToTargetTrack handles null setPendingTrackSwitchDeferred gracefully
            switchToTargetTrack(activePlayer, initialTrackIndex)

            // Apply position within the track
            activePlayer.seekTo(initialTrackIndex, initialPosition)
            delay(PlaylistInitialSeekStabilizationPolicy.STABILIZATION_DELAY_MS)

            // Verify final state
            verifyPositionApplied(activePlayer, initialTrackIndex, initialPosition)
        } catch (e: Exception) {
            e.rethrowCancellation()
            LogUtils.w(
                "AudioPlayerService",
                "Failed to apply initial position after all tracks loaded: ${e.message}",
            )
        }
    }

    /**
     * Waits for all tracks to be loaded with stability verification.
     * Returns true if all tracks are loaded, false otherwise.
     *
     * Optimized for tests: early exit if mediaItemCount doesn't change (indicates mock in tests).
     */
    private suspend fun waitForAllTracksLoaded(
        player: ExoPlayer,
        expectedCount: Int,
        loadStartTime: Long,
    ): Boolean {
        var state = PlaylistLoadStabilityState()
        while (state.attempts < PlaylistLoadStabilityPolicy.MAX_WAIT_ATTEMPTS) {
            val currentCount = player.mediaItemCount
            val evaluation =
                PlaylistLoadStabilityPolicy.evaluate(
                    state = state,
                    currentCount = currentCount,
                    expectedCount = expectedCount,
                )
            val terminalResult = evaluation.terminalResult
            if (terminalResult != null) {
                if (terminalResult) {
                    val duration = System.currentTimeMillis() - loadStartTime
                    LogUtils.i(
                        "AudioPlayerService",
                        "Playlist loaded: $currentCount tracks (expected $expectedCount, ${duration}ms)",
                    )
                }
                return terminalResult
            }
            state = evaluation.nextState
            delay(PlaylistLoadStabilityPolicy.WAIT_POLL_DELAY_MS)
        }

        val finalCount = player.mediaItemCount
        if (finalCount < expectedCount) {
            LogUtils.w(
                "AudioPlayerService",
                "Not all tracks loaded: mediaItemCount=$finalCount (expected $expectedCount). Skipping position application.",
            )
            return false
        }

        val duration = System.currentTimeMillis() - loadStartTime
        LogUtils.i(
            "AudioPlayerService",
            "Playlist confirmed loaded: $finalCount tracks (expected $expectedCount, ${duration}ms)",
        )
        return true
    }

    /**
     * Waits for player to be in ready or buffering state.
     */
    private suspend fun waitForPlayerReady(player: ExoPlayer) {
        var attempts = 0
        while (PlaylistPlayerReadyWaitPolicy.shouldContinueWaiting(attempts, player.playbackState)) {
            delay(PlaylistPlayerReadyWaitPolicy.POLL_DELAY_MS)
            attempts++
        }
    }

    /**
     * Switches to target track if current track differs.
     * Uses CompletableDeferred for reliable event-based waiting.
     */
    private suspend fun switchToTargetTrack(
        player: ExoPlayer,
        targetIndex: Int,
    ) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == targetIndex) {
            return // Already on target track
        }

        LogUtils.d(
            "AudioPlayerService",
            "Switching from track $currentIndex to target track $targetIndex",
        )

        // Register deferred BEFORE seeking so onMediaItemTransition cannot be missed
        // ponytail: deferred must precede seek; fallback polling is sequential (withTimeout→cancel→poll), not parallel
        val useDeferred =
            PlaylistTrackSwitchDeferredPolicy.shouldUseDeferred(
                callbackAvailable = setPendingTrackSwitchDeferred != null,
            )
        if (!useDeferred) {
            LogUtils.d(
                "AudioPlayerService",
                "setPendingTrackSwitchDeferred is null, will use polling fallback for track switch",
            )
        }
        val trackSwitchDeferred =
            if (useDeferred) {
                CompletableDeferred<Int>().also { deferred ->
                    try {
                        requireNotNull(setPendingTrackSwitchDeferred).invoke(deferred)
                        LogUtils.d(
                            "AudioPlayerService",
                            "Set pendingTrackSwitchDeferred for track switch to $targetIndex",
                        )
                    } catch (e: Exception) {
                        LogUtils.w(
                            "AudioPlayerService",
                            "Failed to set pendingTrackSwitchDeferred: ${e.message}",
                        )
                    }
                }
            } else {
                null
            }

        // Switch tracks after deferred is registered
        val seekPlan = PlaylistTrackSeekFallbackPolicy.buildTrackSwitchSeekPlan()
        try {
            when (seekPlan.first()) {
                PlaylistTrackSeekStep.DEFAULT_POSITION -> player.seekToDefaultPosition(targetIndex)
                PlaylistTrackSeekStep.EXPLICIT_ZERO -> player.seekTo(targetIndex, 0)
            }
        } catch (e: Exception) {
            LogUtils.w("AudioPlayerService", "seekToDefaultPosition failed, trying seekTo: ${e.message}")
            when (seekPlan.getOrNull(1)) {
                PlaylistTrackSeekStep.EXPLICIT_ZERO -> player.seekTo(targetIndex, 0)
                PlaylistTrackSeekStep.DEFAULT_POSITION -> player.seekToDefaultPosition(targetIndex)
                null -> throw e
            }
        }
        val deferredToAwait =
            if (
                PlaylistTrackSwitchDeferredPolicy.canAwaitDeferred(
                    useDeferred = useDeferred,
                    deferredCreated = trackSwitchDeferred != null,
                )
            ) {
                trackSwitchDeferred
            } else {
                null
            }

        // Wait for track switch event with timeout
        if (deferredToAwait != null) {
            try {
                // Use withTimeout to prevent infinite waiting in tests
                val actualIndex =
                    withTimeout(PlaylistTrackSwitchDeferredPolicy.SWITCH_TIMEOUT_MS) {
                        deferredToAwait.await()
                    }
                LogUtils.d(
                    "AudioPlayerService",
                    "Successfully switched to track $actualIndex (expected $targetIndex) via deferred",
                )
                if (actualIndex != targetIndex) {
                    LogUtils.w(
                        "AudioPlayerService",
                        "Track switch returned index $actualIndex instead of expected $targetIndex",
                    )
                }
                return // Successfully switched via deferred
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                LogUtils.w(
                    "AudioPlayerService",
                    "Timeout waiting for track switch event (5s), falling back to polling",
                )
                // Cancel deferred to clean up
                deferredToAwait.cancel()
            } catch (e: Exception) {
                e.rethrowCancellation()
                // ponytail: cancel stale deferred so late complete() is ignored (coordinator checks isActive)
                if (deferredToAwait.isActive) deferredToAwait.cancel()
                LogUtils.w(
                    "AudioPlayerService",
                    "Failed to wait for track switch event: ${e.message}, falling back to polling",
                )
            }
        }

        // Fallback to polling (used when deferred is not available or failed)
        LogUtils.d(
            "AudioPlayerService",
            "Using polling fallback to verify track switch to $targetIndex",
        )
        var attempts = 0
        while (PlaylistTrackSwitchPollingPolicy.shouldContinuePolling(attempts)) {
            val newIndex = player.currentMediaItemIndex

            if (
                PlaylistTrackSwitchPollingPolicy.isSwitchCompleted(
                    newIndex = newIndex,
                    targetIndex = targetIndex,
                    playbackState = player.playbackState,
                )
            ) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Successfully switched to track $targetIndex after $attempts attempts (polling fallback)",
                )
                return
            }
            delay(PlaylistTrackSwitchPollingPolicy.POLLING_DELAY_MS)
            attempts++
        }

        // If we get here, track switch didn't complete in time
        val finalIndex = player.currentMediaItemIndex
        LogUtils.w(
            "AudioPlayerService",
            "Track switch to $targetIndex did not complete after ${PlaylistTrackSwitchPollingPolicy.MAX_POLLING_ATTEMPTS} attempts (current: $finalIndex)",
        )
    }

    /**
     * Verifies that position was applied correctly.
     * Retries if index doesn't match target.
     */
    private suspend fun verifyPositionApplied(
        player: ExoPlayer,
        targetIndex: Int,
        targetPosition: Long,
    ) {
        val finalIndex = player.currentMediaItemIndex
        val finalPosition = player.currentPosition

        LogUtils.i(
            "AudioPlayerService",
            "Initial position applied: targetTrack=$targetIndex, targetPosition=${targetPosition}ms, " +
                "finalIndex=$finalIndex, finalPosition=${finalPosition}ms",
        )

        if (PlaylistPositionRetryPolicy.shouldRetry(finalIndex = finalIndex, targetIndex = targetIndex)) {
            LogUtils.e(
                "AudioPlayerService",
                "ERROR: Final index ($finalIndex) differs from target ($targetIndex) after seekTo!",
            )
            val retryPlan = PlaylistPositionRetryPolicy.buildRetryPlan()
            // Retry as last resort
            try {
                player.seekToDefaultPosition(targetIndex)
                delay(retryPlan.seekDefaultDelayMs)
                player.seekTo(targetIndex, targetPosition)
                delay(retryPlan.seekTargetDelayMs)
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Retry failed: ${e.message}")
            }
            val lastIndex = player.currentMediaItemIndex
            LogUtils.w(
                "AudioPlayerService",
                "After retry: currentMediaItemIndex=$lastIndex (expected=$targetIndex)",
            )
        }
    }

    /**
     * Creates URI for a file path or URL.
     * Helper method to avoid code duplication.
     */
    private fun createUriForPath(path: String): Uri {
        val resolved =
            PlaylistUriResolutionPolicy.resolve(path = path) { localPath ->
                File(localPath).exists()
            }
        if (resolved.shouldWarnMissingLocalPath) {
            LogUtils.w("AudioPlayerService", "File does not exist: $path")
        }
        return resolved.uri
    }

    /**
     * Creates MediaMetadata for a file path.
     * Helper method to avoid code duplication.
     */
    private fun createMediaMetadata(
        path: String,
        index: Int,
        metadata: Map<String, String>?,
        totalChapters: Int,
    ): androidx.media3.common.MediaMetadata {
        val fileName = PlaylistTrackTitlePolicy.deriveFileName(path, index)
        val resolvedFields = PlaylistMetadataFieldPolicy.resolve(metadata)

        // Always add flavor suffix to title for quick settings player
        val baseTitle = PlaylistTrackTitlePolicy.resolveBaseTitle(resolvedFields.title, fileName, index)
        val titleWithFlavor =
            PlaylistTitleFlavorPolicy.appendFlavor(
                baseTitle = baseTitle,
                flavorSuffix = getFlavorSuffix(),
            )

        val metadataBuilder =
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(titleWithFlavor)
                .setSubtitle(
                    NotificationChapterSubtitlePolicy.resolveSubtitle(
                        path = path,
                        index = index,
                        metadata = metadata,
                        totalChapters = totalChapters,
                    ),
                ).setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)

        if (resolvedFields.artist != null) {
            metadataBuilder.setArtist(resolvedFields.artist)
        }

        if (resolvedFields.album != null) {
            metadataBuilder.setAlbumTitle(resolvedFields.album)
        }

        val artworkUriString = PlaylistArtworkUriPolicy.extractArtworkUriString(metadata)
        if (artworkUriString != null) {
            if (PlaylistArtworkUriPolicy.shouldAttemptParse(artworkUriString)) {
                try {
                    metadataBuilder.setArtworkUri(android.net.Uri.parse(artworkUriString))
                } catch (e: Exception) {
                    LogUtils.w("AudioPlayerService", "Failed to parse artwork URI: $artworkUriString", e)
                }
            }
        }

        return metadataBuilder.build()
    }

    /**
     * Creates a MediaSource for a specific file index.
     * Helper method to avoid code duplication.
     */
    private fun createMediaSourceForIndex(
        playlistItems: List<PlaylistItem>,
        index: Int,
        metadata: Map<String, String>?,
        dataSourceFactory: DataSource.Factory,
    ): MediaSource {
        val item = playlistItems[index]
        val path = item.path
        val uri = createUriForPath(path)
        val mediaMetadata = createMediaMetadata(path, index, metadata, playlistItems.size)

        LogUtils.d(
            "AudioPlayerService",
            "Creating MediaSource $index from ${if (path.startsWith(
                    "http",
                )
            ) {
                "URL"
            } else {
                "file"
            }}: ${path.substringAfterLast('/')}",
        )

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(uri)
                .setMediaId(item.mediaId)
                .apply {
                    com.jabook.app.jabook.audio.player.exoplayer
                        .mimeForUri(uri)
                        ?.let(::setMimeType)
                }.setMediaMetadata(mediaMetadata)
                .apply {
                    if (item.clipStartPositionMs != null || item.clipEndPositionMs != null) {
                        setClippingConfiguration(
                            MediaItem.ClippingConfiguration
                                .Builder()
                                .apply {
                                    item.clipStartPositionMs?.let(::setStartPositionMs)
                                    item.clipEndPositionMs?.let(::setEndPositionMs)
                                }.build(),
                        )
                    }
                }.build()

        return DefaultMediaSourceFactory(
            dataSourceFactory,
            DefaultExtractorsFactory().setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING,
            ),
        ).createMediaSource(mediaItem)
    }

    /**
     * Creates a MediaSource for a specific file path and index.
     * Used by CrossFadePlayer for book-switch crossfade pre-loading.
     */
    public fun createMediaSource(
        filePaths: List<String>,
        index: Int,
        metadata: Map<String, String>?,
    ): MediaSource? {
        if (index < 0 || index >= filePaths.size) return null
        // Crossfade callers supply the active queue paths. Reuse its richer items so embedded
        // chapter clips and stable media IDs survive instead of reconstructing them from paths.
        val playlistItems =
            currentPlaylistItems?.takeIf { it.map(PlaylistItem::path) == filePaths }
                ?: filePaths.map(::PlaylistItem)
        return createMediaSourceForItems(playlistItems, index, metadata)
    }

    public fun createMediaSourceForItems(
        playlistItems: List<PlaylistItem>,
        index: Int,
        metadata: Map<String, String>?,
    ): MediaSource? {
        if (index < 0 || index >= playlistItems.size) return null
        return createMediaSourceForIndex(playlistItems, index, metadata, playbackDataSourceFactory)
    }

    /**
     * Creates and returns the MediaSource for the next track.
     * Used for Crossfade pre-loading.
     */
    public fun getNextMediaSource(currentIndex: Int): MediaSource? {
        val items = currentPlaylistItems ?: currentFilePaths?.map(::PlaylistItem) ?: return null
        val nextIndex = currentIndex + 1
        if (nextIndex >= items.size) return null // End of playlist

        return createMediaSourceForIndex(
            items,
            nextIndex,
            currentMetadata,
            playbackDataSourceFactory,
        )
    }

    /**
     * Preloads next track for smooth transition (inspired by Easybook).
     *
     * This method ensures the next track is loaded and ready before it's needed,
     * preventing delays during track transitions.
     *
     * @param nextTrackIndex Index of the track to preload
     */
    public fun preloadNextTrack(nextTrackIndex: Int) {
        val playlistItems = this.currentPlaylistItems ?: this.currentFilePaths?.map(::PlaylistItem)
        val playlistSize = playlistItems?.size

        val player = getActivePlayer()
        val alreadyLoaded = TrackExistencePolicy.exists(player, nextTrackIndex)
        when (PlaylistPreloadPolicy.decide(playlistSize = playlistSize, targetIndex = nextTrackIndex, alreadyLoaded = alreadyLoaded)) {
            PreloadDecision.SKIP_NO_PATHS -> {
                LogUtils.w("AudioPlayerService", "Cannot preload track $nextTrackIndex: no file paths available")
                return
            }

            PreloadDecision.SKIP_OUT_OF_BOUNDS -> {
                LogUtils.w(
                    "AudioPlayerService",
                    "Cannot preload track $nextTrackIndex: index out of bounds (size=${playlistSize ?: 0})",
                )
                return
            }

            PreloadDecision.SKIP_ALREADY_LOADED -> {
                LogUtils.v("AudioPlayerService", "Track $nextTrackIndex already loaded, skipping preload")
                return
            }

            PreloadDecision.PRELOAD -> Unit
        }
        val nonNullItems = playlistItems ?: return

        // Preload in background to avoid blocking
        playerServiceScope.launch(mediaItemDispatcher) {
            LogUtils.d("AudioPlayerService", "Preloading next track: $nextTrackIndex")
            val metadataSnapshot = currentMetadata
            val executionResult =
                preloadExecutor.execute(
                    buildMediaSource = {
                        createMediaSourceForIndex(
                            playlistItems = nonNullItems,
                            index = nextTrackIndex,
                            metadata = metadataSnapshot,
                            dataSourceFactory = playbackDataSourceFactory,
                        )
                    },
                    shouldAttachOnMain = {
                        PlaylistPreloadPolicy.shouldAttachAfterBuild(
                            !TrackExistencePolicy.exists(player, nextTrackIndex),
                        )
                    },
                    attachOnMain = { mediaSource ->
                        player.addMediaSource(nextTrackIndex, mediaSource)
                    },
                )
            when (executionResult) {
                PlaylistPreloadExecutionResult.Attached -> {
                    LogUtils.i("AudioPlayerService", "Preloaded track $nextTrackIndex for smooth transition")
                }

                PlaylistPreloadExecutionResult.SkippedAlreadyAvailable -> {
                    LogUtils.v(
                        "AudioPlayerService",
                        "Track $nextTrackIndex was loaded by another process, skipping",
                    )
                }

                is PlaylistPreloadExecutionResult.Failed -> {
                    LogUtils.w(
                        "AudioPlayerService",
                        "Failed to preload track $nextTrackIndex",
                        executionResult.error,
                    )
                }
            }
        }
    }

    /**
     * Optimizes memory usage for large playlists by unloading distant tracks (inspired by Easybook).
     *
     * This method removes tracks that are far from the current playback position,
     * keeping only a window of tracks around the current position in memory.
     *
     * @param currentTrackIndex Current playing track index
     * @param keepWindow Number of tracks to keep before and after current track (default: 5)
     */
    public fun optimizeMemoryUsage(
        currentTrackIndex: Int,
        keepWindow: Int = 5,
    ) {
        playerServiceScope.launch(dispatchers.main) {
            val player = getActivePlayer()
            val totalTracks = player.mediaItemCount

            currentFilePaths ?: return@launch
            val plan =
                PlaylistMemoryOptimizationPolicy.buildPlan(
                    totalTracks = totalTracks,
                    currentTrackIndex = currentTrackIndex,
                    keepWindow = keepWindow,
                    trackExistsAt = { index -> TrackExistencePolicy.exists(player, index) },
                ) ?: return@launch

            LogUtils.d(
                "AudioPlayerService",
                "Memory optimization: removing ${plan.removalIndicesDescending.size} distant tracks " +
                    "(keeping window: ${plan.keepStartIndex}-${plan.keepEndIndex} around track $currentTrackIndex)",
            )

            val report =
                PlaylistMemoryOptimizer.applyPlan(
                    plan = plan,
                    removeByIndex = { index ->
                        player.removeMediaItem(index)
                        LogUtils.v("AudioPlayerService", "Removed track $index from memory")
                    },
                    onRemovalFailed = { index, error ->
                        LogUtils.w("AudioPlayerService", "Failed to remove track $index", error)
                    },
                )

            LogUtils.i(
                "AudioPlayerService",
                "Memory optimized: removed ${report.successfulRemovals}/${report.attemptedRemovals} tracks, " +
                    "keeping ${plan.keepEndIndex - plan.keepStartIndex + 1} tracks around current position",
            )
        }
    }
}
