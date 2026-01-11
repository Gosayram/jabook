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

/**
 * Unified actions provider for books.
 *
 * Encapsulates all interactive functionality for working with books,
 * providing a single point of control for actions across all display components.
 *
 * This class solves the problem of fragmented functionality where different
 * components (Grid, List, Search) have different sets of features.
 *
 * @property onBookClick Callback for book click (typically opens player)
 * @property onBookLongPress Callback for long press (shows context menu/properties)
 * @property onToggleFavorite Callback for toggling favorite status
 * @property favoriteIds Set of favorite book IDs for state checking
 * @property showProgress Whether to show listening progress indicator (default true)
 * @property showFavoriteButton Whether to show favorite button (default true)
 * @property showDownloadStatus Whether to show download status (default false, for future use)
 */
public data class BookActionsProvider(
    val onBookClick: (bookId: String) -> Unit,
    val onBookLongPress: (bookId: String) -> Unit,
    val onToggleFavorite: (bookId: String, isFavorite: Boolean) -> Unit,
    val favoriteIds: Set<String> = emptySet(),
    val showProgress: Boolean = true,
    val showFavoriteButton: Boolean = true,
    val showDownloadStatus: Boolean = false,
    // Optional contextual actions
    /** Share book details/link */
    val onShareBook: ((String) -> Unit)? = null,
    /** Delete book from library */
    val onDeleteBook: ((String) -> Unit)? = null,
    /** Add book to playlist */
    val onAddToPlaylist: ((String) -> Unit)? = null,
    /** Show detailed book information */
    val onShowBookInfo: ((String) -> Unit)? = null,
) {
    /**
     * Checks if a book is in favorites.
     */
    public fun isFavorite(bookId: String): Boolean = favoriteIds.contains(bookId)

    /**
     * Checks if any contextual actions are available.
     */
    public fun hasContextualActions(): Boolean =
        onShareBook != null ||
            onDeleteBook != null ||
            onAddToPlaylist != null ||
            onShowBookInfo != null
}
