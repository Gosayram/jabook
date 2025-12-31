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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BookActionsProvider.
 *
 * Tests the functionality of the unified actions provider including:
 * - Favorite state checking
 * - Contextual actions availability
 * - Default values
 */
class BookActionsProviderTest {
    @Test
    fun `isFavorite returns true when book ID is in favorites`() {
        val favoriteIds = setOf("book1", "book2", "book3")
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                favoriteIds = favoriteIds,
            )

        assertTrue(provider.isFavorite("book1"))
        assertTrue(provider.isFavorite("book2"))
        assertTrue(provider.isFavorite("book3"))
    }

    @Test
    fun `isFavorite returns false when book ID is not in favorites`() {
        val favoriteIds = setOf("book1", "book2")
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                favoriteIds = favoriteIds,
            )

        assertFalse(provider.isFavorite("book3"))
        assertFalse(provider.isFavorite("nonexistent"))
    }

    @Test
    fun `isFavorite returns false when favorites set is empty`() {
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                favoriteIds = emptySet(),
            )

        assertFalse(provider.isFavorite("book1"))
    }

    @Test
    fun `hasContextualActions returns true when at least one action is provided`() {
        val providerWithShare =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                onShareBook = {},
            )

        assertTrue(providerWithShare.hasContextualActions())
    }

    @Test
    fun `hasContextualActions returns false when no actions are provided`() {
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
            )

        assertFalse(provider.hasContextualActions())
    }

    @Test
    fun `hasContextualActions returns true with multiple actions`() {
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                onShareBook = {},
                onDeleteBook = {},
                onAddToPlaylist = {},
                onShowBookInfo = {},
            )

        assertTrue(provider.hasContextualActions())
    }

    @Test
    fun `default values are set correctly`() {
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
            )

        assertTrue(provider.showProgress)
        assertTrue(provider.showFavoriteButton)
        assertFalse(provider.showDownloadStatus)
        assertEquals(emptySet<String>(), provider.favoriteIds)
    }

    @Test
    fun `custom flag values override defaults`() {
        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                showProgress = false,
                showFavoriteButton = false,
                showDownloadStatus = true,
            )

        assertFalse(provider.showProgress)
        assertFalse(provider.showFavoriteButton)
        assertTrue(provider.showDownloadStatus)
    }

    @Test
    fun `callbacks can be invoked without errors`() {
        var clickedBookId: String? = null
        var longPressedBookId: String? = null
        var favoriteToggled: Pair<String, Boolean>? = null

        val provider =
            BookActionsProvider(
                onBookClick = { clickedBookId = it },
                onBookLongPress = { longPressedBookId = it },
                onToggleFavorite = { id, isFav -> favoriteToggled = Pair(id, isFav) },
            )

        provider.onBookClick("book1")
        assertEquals("book1", clickedBookId)

        provider.onBookLongPress("book2")
        assertEquals("book2", longPressedBookId)

        provider.onToggleFavorite("book3", true)
        assertEquals(Pair("book3", true), favoriteToggled)
    }

    @Test
    fun `contextual action callbacks can be invoked when provided`() {
        var sharedBookId: String? = null
        var deletedBookId: String? = null
        var addedToPlaylistBookId: String? = null
        var infoBookId: String? = null

        val provider =
            BookActionsProvider(
                onBookClick = {},
                onBookLongPress = {},
                onToggleFavorite = { _, _ -> },
                onShareBook = { sharedBookId = it },
                onDeleteBook = { deletedBookId = it },
                onAddToPlaylist = { addedToPlaylistBookId = it },
                onShowBookInfo = { infoBookId = it },
            )

        provider.onShareBook?.invoke("book1")
        assertEquals("book1", sharedBookId)

        provider.onDeleteBook?.invoke("book2")
        assertEquals("book2", deletedBookId)

        provider.onAddToPlaylist?.invoke("book3")
        assertEquals("book3", addedToPlaylistBookId)

        provider.onShowBookInfo?.invoke("book4")
        assertEquals("book4", infoBookId)
    }
}
