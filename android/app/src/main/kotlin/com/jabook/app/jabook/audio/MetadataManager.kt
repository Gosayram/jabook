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
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages metadata operations (update, extract artwork, get info).
 */
internal class MetadataManager(
    private val context: Context,
    private val getActivePlayer: () -> ExoPlayer,
    private val getNotificationManager: () -> NotificationManager?,
    private val getEmbeddedArtworkPath: () -> String?,
    private val setEmbeddedArtworkPath: (String?) -> Unit,
    private val getCurrentMetadata: () -> Map<String, String>?,
    private val setCurrentMetadata: (Map<String, String>?) -> Unit,
) {
    /**
     * Updates metadata for current track.
     *
     * @param metadata Metadata map (title, artist, album)
     * Note: Artwork is automatically extracted from audio file metadata by ExoPlayer
     * We don't update MediaItem here to preserve embedded artwork extracted by ExoPlayer
     */
    fun updateMetadata(metadata: Map<String, String>) {
        setCurrentMetadata(metadata)
        // Just update notification - ExoPlayer already has the embedded artwork in MediaItem
        // Don't replace MediaItem to avoid losing embedded artwork
        // MediaSession automatically updates metadata from ExoPlayer, no manual update needed
        getNotificationManager()?.updateMetadata(metadata, getEmbeddedArtworkPath())
    }

    /**
     * Gets information about current media item.
     *
     * @return Map with current media item information, or empty map if no item
     */
    fun getCurrentMediaItemInfo(): Map<String, Any?> {
        val player = getActivePlayer()
        val currentItem = player.currentMediaItem ?: return emptyMap()
        val metadata = currentItem.mediaMetadata

        // Get artwork path - prefer embedded artwork if available
        val artworkPath =
            getEmbeddedArtworkPath()?.takeIf {
                val file = File(it)
                file.exists() && file.length() > 0
            } ?: metadata.artworkUri?.toString()

        return mapOf(
            "mediaId" to currentItem.mediaId,
            "uri" to currentItem.localConfiguration?.uri?.toString(),
            "title" to (metadata.title?.toString()),
            "artist" to (metadata.artist?.toString()),
            "albumTitle" to (metadata.albumTitle?.toString()),
            "hasArtwork" to (metadata.artworkUri != null || metadata.artworkData != null),
            "artworkPath" to artworkPath, // Path to artwork (embedded or external)
        )
    }

    /**
     * Extracts embedded artwork from audio file metadata using Android MediaMetadataRetriever.
     *
     * This method runs in a background thread to avoid blocking the main thread.
     *
     * @param filePath Path to the audio file
     * @return Path to saved artwork file, or null if no artwork found
     */
    fun extractArtworkFromFile(filePath: String): String? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        android.util.Log.w("AudioPlayerService", "File does not exist: $filePath")
                        return@withContext null
                    }

                    // Use Android MediaMetadataRetriever to extract embedded artwork
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(filePath)

                        // Try to get embedded picture (album art)
                        val picture = retriever.embeddedPicture
                        if (picture != null && picture.isNotEmpty()) {
                            // Save artwork to cache
                            val cacheDir = context.cacheDir
                            val artworkFile = File(cacheDir, "embedded_artwork_${filePath.hashCode()}.jpg")
                            artworkFile.outputStream().use { it.write(picture) }
                            android.util.Log.i(
                                "AudioPlayerService",
                                "Extracted and saved artwork from $filePath to ${artworkFile.absolutePath}",
                            )
                            val artworkPath = artworkFile.absolutePath
                            setEmbeddedArtworkPath(artworkPath)
                            return@withContext artworkPath
                        }

                        android.util.Log.d("AudioPlayerService", "No embedded artwork found in $filePath")
                        null
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to extract artwork from $filePath", e)
                    null
                }
            }
        }
    }
}
