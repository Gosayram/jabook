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

package com.jabook.app.jabook.compose.data.local.scanner

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for generating unique book IDs based on directory, album, and artist.
 *
 * This helps prevent duplicate books in the library by generating consistent
 * IDs for the same logical book, even if scanned from different locations.
 */
@Singleton
class BookIdentifier
    @Inject
    constructor() {
        /**
         * Generate a unique book ID from directory, album, and artist.
         *
         * The ID is generated using MD5 hash of normalized, combined metadata.
         *
         * @param directory Book directory path
         * @param album Album name (nullable)
         * @param artist Artist/author name (nullable)
         * @return Unique book ID
         */
        fun generateBookId(
            directory: String,
            album: String?,
            artist: String?,
        ): String {
            // Normalize inputs
            val normalizedDir = normalizeString(directory)
            val normalizedAlbum = normalizeString(album ?: "")
            val normalizedArtist = normalizeString(artist ?: "")

            // Combine into a composite key
            val composite = "$normalizedDir|$normalizedAlbum|$normalizedArtist"

            // Generate MD5 hash
            return md5Hash(composite)
        }

        /**
         * Normalize string for consistent comparison.
         *
         * - Trim whitespace
         * - Convert to lowercase
         * - Remove multiple spaces
         */
        private fun normalizeString(str: String): String =
            str
                .trim()
                .lowercase()
                .replace(Regex("\\s+"), " ")

        /**
         * Generate MD5 hash of the input string.
         */
        private fun md5Hash(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Generate a composite grouping key for books.
         *
         * This is used for grouping audio files into books.
         * Uses directory as fallback when album is null/empty.
         *
         * @param directory Book directory path
         * @param album Album name (nullable)
         * @param artist Artist/author name (nullable)
         * @return Composite grouping key
         */
        fun generateGroupingKey(
            directory: String,
            album: String?,
            artist: String?,
        ): String {
            val normalizedDir = normalizeString(directory)
            val normalizedAlbum = normalizeString(album ?: "")
            val normalizedArtist = normalizeString(artist ?: "")

            // Use album if available, otherwise use directory
            val primaryKey = if (normalizedAlbum.isNotBlank()) normalizedAlbum else normalizedDir

            // Include artist for additional uniqueness
            return if (normalizedArtist.isNotBlank()) {
                "$primaryKey|$normalizedArtist"
            } else {
                primaryKey
            }
        }
    }
