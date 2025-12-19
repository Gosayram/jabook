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

package com.jabook.app.jabook.compose.data.local.parser

import android.media.MediaMetadataRetriever
import com.simplecityapps.ktaglib.KTagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioMetadataParser using Android MediaMetadataRetriever + KTagLib.
 *
 * Uses MediaMetadataRetriever as primary source, falls back to KTagLib for advanced tags.
 */
@Singleton
class Media3MetadataParser
    @Inject
    constructor(
        private val encodingDetector: EncodingDetector,
    ) : AudioMetadataParser {
        private val kTagLib by lazy { KTagLib() }

        override suspend fun parseMetadata(filePath: String): AudioMetadata? =
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) return@withContext null

                    // Try KTagLib first (better tag support)
                    parseWithKTagLib(file) ?: parseWithMediaMetadataRetriever(filePath)
                } catch (e: Exception) {
                    android.util.Log.e("MetadataParser", "Failed to parse: $filePath", e)
                    null
                }
            }

        private fun parseWithKTagLib(file: File): AudioMetadata? {
            // CRITICAL: Disable KTagLib on Android 11+ (API 30) due to FDSAN incompatibility.
            // Starting from Android 11, fdsan (file descriptor sanitizer) considers ownership
            // mismatches fatal errors and will abort the app.
            // KTagLib's native code (libktaglib.so) uses fdopen() which violates this ownership tracking.
            // MediaMetadataRetriever is a reliable fallback for all Android versions where KTagLib crashes.
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.util.Log.d("MetadataParser", "Skipping KTagLib on Android 11+ (FDSAN incompatibility)")
                return null
            }

            return try {
                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                pfd.use {
                    val metadata = kTagLib.getMetadata(it.fd, file.name)
                    val artwork = kTagLib.getArtwork(it.fd, file.name)

                    if (metadata == null) return@use null

                    val props = metadata.propertyMap
                    val audioProps = metadata.audioProperties

                    AudioMetadata(
                        title = fixEncodingIfNeeded(props["TITLE"]?.firstOrNull()),
                        artist = fixEncodingIfNeeded(props["ARTIST"]?.firstOrNull()),
                        album = fixEncodingIfNeeded(props["ALBUM"]?.firstOrNull()),
                        albumArtist = fixEncodingIfNeeded(props["ALBUMARTIST"]?.firstOrNull()),
                        duration = (audioProps?.duration ?: 0).toLong(),
                        genre = fixEncodingIfNeeded(props["GENRE"]?.firstOrNull()),
                        year = props["DATE"]?.firstOrNull(),
                        trackNumber = props["TRACKNUMBER"]?.firstOrNull()?.toIntOrNull(),
                        coverArt = artwork,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("MetadataParser", "KTagLib parsing failed", e)
                null
            }
        }

        /**
         * Fix encoding issues in metadata strings.
         * Attempts to fix garbled Russian text that was incorrectly decoded.
         */
        private fun fixEncodingIfNeeded(text: String?): String? {
            if (text.isNullOrBlank()) return text

            val (fixed, detectedEncoding) = encodingDetector.fixGarbledText(text)

            if (detectedEncoding != null) {
                android.util.Log.e(
                    "MetadataParser",
                    "📝 ENCODING FIX APPLIED: '$text' -> '$fixed' (encoding: $detectedEncoding)",
                )
            }

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
                        android.util.Log.w(
                            "MetadataParser",
                            "No metadata found in file: $filePath - may cause missing books",
                        )
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
                android.util.Log.e("MetadataParser", "MediaMetadataRetriever failed", e)
                null
            }
    }
