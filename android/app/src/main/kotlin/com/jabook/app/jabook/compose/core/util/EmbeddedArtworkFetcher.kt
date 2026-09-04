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

package com.jabook.app.jabook.compose.core.util

import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.jabook.app.jabook.util.LogUtils
import okio.Buffer
import okio.FileSystem

/**
 * Coil Fetcher that decodes artwork embedded in local audio files on demand.
 *
 * Data model: a string with the marker scheme `audio-artwork://` followed by the
 * absolute file path of the audio file (e.g. `audio-artwork:///storage/emulated/0/Books/01.mp3`).
 * The string itself is Coil's cache key, so no [coil.key.Keyer] is needed.
 *
 * Extraction uses [MediaMetadataRetriever.embeddedPicture], which reads only the
 * tag header — the whole audio file is never loaded. Missing artwork returns null
 * so Coil falls through to the request's error/fallback drawable.
 *
 * Cancellation is handled by Coil cancelling the fetching coroutine; no extra
 * scope is spawned here.
 */
public class EmbeddedArtworkFetcher(
    private val audioFilePath: String,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = extractEmbeddedArtwork(audioFilePath) ?: return null
        val source =
            ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = FileSystem.SYSTEM,
            )
        return SourceFetchResult(
            source = source,
            mimeType = detectImageMimeType(bytes),
            dataSource = DataSource.DISK,
        )
    }

    private fun extractEmbeddedArtwork(path: String): ByteArray? =
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            LogUtils.w(TAG, "Embedded artwork extraction failed: $path", e)
            null
        }

    /**
     * Sniffs the image format from the byte signature.
     * Falls back to JPEG, the dominant format for embedded ID3/Vorbis artwork.
     */
    private fun detectImageMimeType(bytes: ByteArray): String =
        when {
            bytes.size >= JPEG_SIGNATURE.size && bytes.hasPrefix(JPEG_SIGNATURE) -> "image/jpeg"
            bytes.size >= PNG_SIGNATURE.size && bytes.hasPrefix(PNG_SIGNATURE) -> "image/png"
            else -> "image/jpeg"
        }

    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    public object Factory : Fetcher.Factory<String> {
        override fun create(
            data: String,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (!data.startsWith(SCHEME)) return null
            val path = data.removePrefix(SCHEME)
            if (path.isBlank()) return null
            return EmbeddedArtworkFetcher(path)
        }
    }

    private companion object {
        private const val TAG = "EmbeddedArtworkFetcher"

        /** Marker scheme prefix; the remainder is the absolute audio file path. */
        public const val SCHEME: String = "audio-artwork://"

        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    }
}
