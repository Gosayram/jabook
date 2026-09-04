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

package com.jabook.app.jabook.compose.data.local.parser

import android.media.MediaMetadataRetriever
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioMetadataParser using Android MediaMetadataRetriever.
 */
@Singleton
public class Media3MetadataParser
    @Inject
    constructor(
        private val encodingDetector: EncodingDetector,
        private val loggerFactory: LoggerFactory,
    ) : AudioMetadataParser {
        private val logger = loggerFactory.get("MetadataParser")

        override suspend fun parseMetadata(filePath: String): AudioMetadata? =
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) return@withContext null

                    parseWithMediaMetadataRetriever(filePath)
                } catch (e: Exception) {
                    logger.e({ "Failed to parse: $filePath" }, e)
                    null
                }
            }

        /**
         * Fix encoding issues in metadata strings.
         *
         * CRITICAL FIX (2025-12-20): MediaMetadataRetriever properly decodes
         * UTF-16LE/BE tags. Don't blindly apply fixGarbledText() to ALL text - this BREAKS
         * correct UTF-16 tags by corrupting them into CJK/Greek mojibake!
         *
         * ONLY apply fixGarbledText() if text shows ACTUAL mojibake indicators:
         * - Has Cyrillic (indicates Russian text)
         * - ALSO has CJK/Greek/Arabic (indicates corruption, e.g. "襞諛梭嬀", "Ρετψετ")
         *
         * Proper UTF-16 text like "Глава 12" should pass through unchanged.
         */
        private fun fixEncodingIfNeeded(text: String?): String? {
            if (text.isNullOrBlank()) return text

            val hasCyrillic = text.any { it in '\u0400'..'\u04FF' }
            val hasCJK = text.any { it in '\u4E00'..'\u9FFF' }
            val hasGreek = text.any { it in '\u0370'..'\u03FF' }
            val hasArabic = text.any { it in '\u0600'..'\u06FF' }
            val hasMojibake = hasCyrillic && (hasCJK || hasGreek || hasArabic)

            if (!hasMojibake) {
                return text.takeIf { it.isNotBlank() }
            }

            val (fixed, detectedEncoding) = encodingDetector.fixGarbledText(text)
            return fixed.takeIf { it.isNotBlank() }
        }

        private fun parseWithMediaMetadataRetriever(filePath: String): AudioMetadata? =
            try {
                val retriever = MediaMetadataRetriever()

                try {
                    retriever.setDataSource(filePath)

                    val title =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val artist =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val album =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val albumArtist =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    val durationStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val genre =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    val year =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    val trackStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)

                    val duration = durationStr?.toLongOrNull() ?: 0L
                    val trackNumber = trackStr?.toIntOrNull()
                    val coverArt = retriever.embeddedPicture

                    // Log if metadata is empty (indicates potential scanning issue)
                    if (title.isNullOrBlank() && album.isNullOrBlank() && artist.isNullOrBlank()) {
                        logger.w { "No metadata found in file: $filePath - may cause missing books" }
                    }

                    AudioMetadata(
                        title = fixEncodingIfNeeded(title),
                        artist = fixEncodingIfNeeded(artist),
                        album = fixEncodingIfNeeded(album),
                        albumArtist = fixEncodingIfNeeded(albumArtist),
                        duration = duration,
                        genre = fixEncodingIfNeeded(genre),
                        year = year?.takeIf { it.isNotBlank() },
                        trackNumber = trackNumber,
                        coverArt = coverArt,
                    )
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                logger.e({ "MediaMetadataRetriever failed" }, e)
                null
            }
    }
