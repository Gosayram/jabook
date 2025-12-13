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

/**
 * Audio metadata extracted from an audio file.
 *
 * @property title Track title
 * @property artist Artist name
 * @property album Album name
 * @property albumArtist Album artist (may differ from track artist)
 * @property duration Duration in milliseconds
 * @property genre Genre
 * @property year Release year
 * @property trackNumber Track number in album
 * @property coverArt Cover art image data (JPEG/PNG)
 */
data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val duration: Long, // milliseconds
    val genre: String?,
    val year: String?,
    val trackNumber: Int?,
    val coverArt: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioMetadata

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (albumArtist != other.albumArtist) return false
        if (duration != other.duration) return false
        if (genre != other.genre) return false
        if (year != other.year) return false
        if (trackNumber != other.trackNumber) return false
        if (coverArt != null) {
            if (other.coverArt == null) return false
            if (!coverArt.contentEquals(other.coverArt)) return false
        } else if (other.coverArt != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (albumArtist?.hashCode() ?: 0)
        result = 31 * result + duration.hashCode()
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (coverArt?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Parser for extracting metadata from audio files.
 *
 * Uses Media3 MetadataRetriever as primary source,
 * with TagLib as fallback for unsupported formats.
 */
interface AudioMetadataParser {
    /**
     * Extract metadata from audio file.
     *
     * @param filePath Absolute path to audio file
     * @return Parsed metadata or null if parsing failed
     */
    suspend fun parseMetadata(filePath: String): AudioMetadata?
}
