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
import android.os.ParcelFileDescriptor
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
    constructor() : AudioMetadataParser {
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
            return try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd.use {
                    val metadata = kTagLib.getMetadata(pfd.fd, file.name)
                    val artwork = kTagLib.getArtwork(pfd.fd, file.name)

                    if (metadata == null) return@use null

                    val props = metadata.propertyMap
                    val audioProps = metadata.audioProperties

                    AudioMetadata(
                        title = props["TITLE"]?.firstOrNull(),
                        artist = props["ARTIST"]?.firstOrNull(),
                        album = props["ALBUM"]?.firstOrNull(),
                        albumArtist = props["ALBUMARTIST"]?.firstOrNull(),
                        duration = (audioProps?.duration ?: 0).toLong(),
                        genre = props["GENRE"]?.firstOrNull(),
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

                    AudioMetadata(
                        title = title?.takeIf { it.isNotBlank() },
                        artist = artist?.takeIf { it.isNotBlank() },
                        album = album?.takeIf { it.isNotBlank() },
                        albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                        duration = duration,
                        genre = genre?.takeIf { it.isNotBlank() },
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
