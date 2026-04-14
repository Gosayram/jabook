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

package com.jabook.app.jabook.compose.domain.model

import com.jabook.app.jabook.compose.data.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class BookFavoriteMappingTest {
    @Test
    fun `toFavoriteItem returns null when sourceUrl is missing`() {
        val book = sampleBook(sourceUrl = null)

        val result = book.toFavoriteItem(addedToFavorites = "2026-04-11T00:00:00Z")

        assertNull(result)
    }

    @Test
    fun `toFavoriteItem returns null for malformed sourceUrl`() {
        val book = sampleBook(sourceUrl = "not-a-uri")

        val result = book.toFavoriteItem(addedToFavorites = "2026-04-11T00:00:00Z")

        assertNull(result)
    }

    @Test
    fun `toFavoriteItem returns null for non magnet sourceUrl`() {
        val book = sampleBook(sourceUrl = "https://example.com/book")

        val result = book.toFavoriteItem(addedToFavorites = "2026-04-11T00:00:00Z")

        assertNull(result)
    }

    @Test
    fun `toFavoriteItem uses injected timestamp and source url`() {
        val book = sampleBook(sourceUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567")

        val result = book.toFavoriteItem(addedToFavorites = "2026-04-11T00:00:00Z")

        requireNotNull(result)
        assertEquals("2026-04-11T00:00:00Z", result.addedToFavorites)
        assertEquals(book.sourceUrl, result.magnetUrl)
        assertEquals(book.id, result.topicId)
    }

    @Test
    fun `toFavoriteItem trims whitespace around valid magnet url`() {
        val book = sampleBook(sourceUrl = "  magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567  ")

        val result = book.toFavoriteItem(addedToFavorites = "2026-04-11T00:00:00Z")

        requireNotNull(result)
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", result.magnetUrl)
    }

    private fun sampleBook(sourceUrl: String?): Book =
        Book(
            id = "book-id",
            title = "Book title",
            author = "Book author",
            description = null,
            coverUrl = null,
            totalDuration = 120.minutes,
            currentPosition = 10.minutes,
            progress = 0.1f,
            currentChapterIndex = 1,
            downloadStatus = DownloadStatus.DOWNLOADED,
            downloadProgress = 1f,
            localPath = "/books/test.mp3",
            addedDate = 1_700_000_000_000L,
            lastPlayedDate = null,
            isFavorite = false,
            sourceUrl = sourceUrl,
            rewindDuration = 10,
            forwardDuration = 30,
        )
}
