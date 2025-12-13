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

/**
 * Represents a scanned audiobook with all its chapters.
 *
 * @property directory Parent directory path
 * @property title Book title (from album tag)
 * @property author Book author (from artist/albumArtist tag)
 * @property chapters List of audio files as chapters
 * @property totalDuration Total duration of all chapters
 * @property coverArt Cover art image data
 */
data class ScannedBook(
    val directory: String,
    val title: String,
    val author: String,
    val chapters: List<ScannedChapter>,
    val totalDuration: Long,
    val coverArt: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScannedBook

        if (directory != other.directory) return false
        if (title != other.title) return false
        if (author != other.author) return false
        if (chapters != other.chapters) return false
        if (totalDuration != other.totalDuration) return false
        if (coverArt != null) {
            if (other.coverArt == null) return false
            if (!coverArt.contentEquals(other.coverArt)) return false
        } else if (other.coverArt != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = directory.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + chapters.hashCode()
        result = 31 * result + totalDuration.hashCode()
        result = 31 * result + (coverArt?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Represents a single chapter (audio file) in an audiobook.
 *
 * @property filePath Absolute path to audio file
 * @property title Chapter title
 * @property index Chapter index (0-based)
 * @property duration Duration in milliseconds
 */
data class ScannedChapter(
    val filePath: String,
    val title: String,
    val index: Int,
    val duration: Long,
)

/**
 * Scanner for discovering audiobooks in local storage.
 */
interface LocalBookScanner {
    /**
     * Scan local storage for audiobooks.
     *
     * @return Result containing list of scanned books or error
     */
    suspend fun scanAudiobooks(): com.jabook.app.jabook.compose.domain.model.Result<List<ScannedBook>>
}
