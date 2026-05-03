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
import androidx.media3.common.Metadata
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import com.jabook.app.jabook.util.LogUtils
import java.io.File

/**
 * Handles metadata-related player events: ReplayGain extraction and embedded artwork management.
 *
 * Extracted from PlayerListener as part of TASK-VERM-03 (PlayerListener decomposition).
 * Responsible for:
 * - Parsing ReplayGain tags from ID3 metadata
 * - Extracting and caching embedded artwork from audio files
 */
internal class PlayerMetadataHandler(
    private val context: Context,
    private val setEmbeddedArtworkPath: (String?) -> Unit,
) {
    /**
     * Active LoudnessNormalizer for ReplayGain application.
     * Injected by PlayerConfigurator when processor chain is created.
     */
    var loudnessNormalizer: LoudnessNormalizer? = null

    /**
     * Handles metadata updates to extract ReplayGain info from ID3 TXXX frames.
     */
    fun onMetadata(metadata: Metadata) {
        val normalizer = loudnessNormalizer ?: return

        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            try {
                val entryString = entry.toString()
                if (entryString.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
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
            val text = entry.toString()
            val valueMatch =
                Regex("value=([\\-\\+\\d\\.]+)\\s*dB?", RegexOption.IGNORE_CASE).find(text)

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
     * Handles media metadata changes including embedded artwork extraction.
     * Media3: artworkData and artworkUri are NOT deprecated - use them directly.
     */
    fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        val title = mediaMetadata.title?.toString() ?: "Unknown"
        val artist = mediaMetadata.artist?.toString() ?: "Unknown"
        val album = mediaMetadata.albumTitle?.toString()

        LogUtils.d("AudioPlayerService", "Metadata changed: title=$title, artist=$artist, album=$album")

        val artworkUri = mediaMetadata.artworkUri
        val artworkData = mediaMetadata.artworkData
        val hasArtworkData = artworkData != null && artworkData.isNotEmpty()
        val hasArtworkUri = artworkUri != null

        if (artworkUri != null) {
            LogUtils.d("AudioPlayerService", "Artwork URI available: $artworkUri")
            setEmbeddedArtworkPath(null)
        } else if (hasArtworkData) {
            LogUtils.d("AudioPlayerService", "Embedded artwork data available: ${artworkData.size} bytes")
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
    }
}
