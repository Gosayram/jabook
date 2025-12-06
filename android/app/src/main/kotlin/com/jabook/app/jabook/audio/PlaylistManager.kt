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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

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
) {
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
    ) = withContext(Dispatchers.IO) {
        try {
            val dataSourceFactory = MediaDataSourceFactory(context, mediaCache)

            // Determine which track to load first (saved position or first track)
            val firstTrackIndex = initialTrackIndex?.coerceIn(0, filePaths.size - 1) ?: 0
            android.util.Log.d("AudioPlayerService", "Loading first track: index=$firstTrackIndex (total=${filePaths.size})")

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

                // Add first item and prepare - this allows immediate playback
                activePlayer.addMediaSource(firstMediaSource)
                activePlayer.prepare()

                // Seek to saved position if needed (will be done after prepare completes)
                if (initialTrackIndex != null && initialTrackIndex != 0) {
                    // Seek will be handled by caller after player is ready
                    android.util.Log.d("AudioPlayerService", "First track loaded, will seek to index $initialTrackIndex after ready")
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
            // OPTIMIZATION: Use limited dispatcher to control parallel MediaItem creation (max 4)
            playerServiceScope.launch(mediaItemDispatcher) {
                try {
                    val remainingIndices = filePaths.indices.filter { it != firstTrackIndex }
                    val totalItems = filePaths.size
                    val isLargePlaylist = totalItems > 100
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Loading ${remainingIndices.size} remaining MediaItems asynchronously (large playlist: $isLargePlaylist)",
                    )

                    // OPTIMIZATION: If initialTrackIndex > 0, prioritize loading tracks before it
                    // This ensures smooth navigation if user wants to go back
                    val priorityIndices =
                        if (firstTrackIndex > 0) {
                            (0 until firstTrackIndex).toList()
                        } else {
                            emptyList()
                        }
                    val otherIndices =
                        remainingIndices
                            .filter { it !in priorityIndices }
                            .sorted() // Ensure ascending order to maintain correct track sequence

                    // Load priority indices first (tracks before initialTrackIndex)
                    for ((priorityIndex, index) in priorityIndices.withIndex()) {
                        try {
                            val mediaSource =
                                createMediaSourceForIndex(
                                    filePaths,
                                    index,
                                    metadata,
                                    dataSourceFactory,
                                )

                            // Add to player asynchronously (non-blocking)
                            withContext(Dispatchers.Main) {
                                val activePlayer = getActivePlayer()
                                // Insert at correct position to maintain order
                                activePlayer.addMediaSource(index, mediaSource)
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Added priority MediaItem at index $index (playlist size: ${activePlayer.mediaItemCount})",
                                )
                            }

                            // Yield for large playlists to prevent blocking
                            if (isLargePlaylist && priorityIndex % 10 == 0) {
                                yield()
                            }

                            // Small delay to avoid overwhelming the system
                            delay(5) // 5ms delay for priority items
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to create MediaItem $index, skipping: ${e.message}")
                            // Continue with other items - one failure shouldn't stop the rest
                        }
                    }

                    // Load other indices (tracks after initialTrackIndex)
                    for ((otherIndex, index) in otherIndices.withIndex()) {
                        try {
                            val mediaSource =
                                createMediaSourceForIndex(
                                    filePaths,
                                    index,
                                    metadata,
                                    dataSourceFactory,
                                )

                            // Add to player asynchronously (non-blocking)
                            withContext(Dispatchers.Main) {
                                val activePlayer = getActivePlayer()
                                // CRITICAL: Use index parameter to insert at correct position
                                // This ensures tracks are added in the correct order, not at the end
                                activePlayer.addMediaSource(index, mediaSource)
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Added MediaItem at index $index (playlist size: ${activePlayer.mediaItemCount})",
                                )
                            }

                            // Yield for large playlists to prevent blocking (every 10 items)
                            if (isLargePlaylist && otherIndex % 10 == 0) {
                                yield()
                            }

                            // Small delay to avoid overwhelming the system
                            if (otherIndex % 10 == 0) {
                                delay(10) // 10ms delay every 10 items
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to create MediaItem $index, skipping: ${e.message}")
                            // Continue with other items - one failure shouldn't stop the rest
                        }
                    }

                    android.util.Log.i(
                        "AudioPlayerService",
                        "All ${filePaths.size} MediaItems loaded asynchronously (priority: ${priorityIndices.size}, other: ${otherIndices.size})",
                    )

                    // Verify playlist order after all items are loaded
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
    fun createMediaSourceForIndex(
        filePaths: List<String>,
        index: Int,
        metadata: Map<String, String>?,
        dataSourceFactory: MediaDataSourceFactory,
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
}
