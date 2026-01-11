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

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import coil3.SingletonImageLoader
import com.jabook.app.jabook.compose.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preloads cover images for books that are about to become visible in the list.
 *
 * This improves UX by loading covers in advance, so they appear instantly when
 * the user scrolls to them. Uses Coil's ImageLoader.enqueue() for efficient
 * background preloading.
 *
 * @param books List of all books in the list
 * @param listState LazyListState for tracking visible items
 * @param context Android context for creating ImageRequests
 * @param preloadAhead Number of items ahead of visible area to preload (default: 5)
 */
@Composable
public fun rememberCoverPreloader(
    books: List<Book>,
    listState: LazyListState,
    context: Context,
    preloadAhead: Int = 5,
) {
    val preloader =
        remember(context, preloadAhead) {
            CoverPreloader(context, preloadAhead)
        }

    LaunchedEffect(books, listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size) {
        val firstVisible = listState.firstVisibleItemIndex
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size
        val lastVisible = firstVisible + visibleCount

        // Preload covers for visible items and items ahead
        val preloadEnd = (lastVisible + preloadAhead).coerceAtMost(books.size)
        val booksToPreload = books.subList(firstVisible.coerceAtLeast(0), preloadEnd)

        preloader.preloadCovers(booksToPreload)
    }
}

/**
 * Preloads cover images for books in a grid layout.
 *
 * @param books List of all books in the grid
 * @param gridState LazyGridState for tracking visible items
 * @param context Android context for creating ImageRequests
 * @param preloadAhead Number of items ahead of visible area to preload (default: 10 for grid)
 */
@Composable
public fun rememberCoverPreloaderForGrid(
    books: List<Book>,
    gridState: LazyGridState,
    context: Context,
    preloadAhead: Int = 10,
) {
    val preloader =
        remember(context, preloadAhead) {
            CoverPreloader(context, preloadAhead)
        }

    LaunchedEffect(books, gridState.firstVisibleItemIndex, gridState.layoutInfo.visibleItemsInfo.size) {
        val firstVisible = gridState.firstVisibleItemIndex
        val visibleCount = gridState.layoutInfo.visibleItemsInfo.size
        val lastVisible = firstVisible + visibleCount

        // Preload covers for visible items and items ahead
        val preloadEnd = (lastVisible + preloadAhead).coerceAtMost(books.size)
        val booksToPreload = books.subList(firstVisible.coerceAtLeast(0), preloadEnd)

        preloader.preloadCovers(booksToPreload)
    }
}

/**
 * Helper class for preloading cover images.
 */
private class CoverPreloader(
    private val context: Context,
    private val preloadAhead: Int,
) {
    private val imageLoader = SingletonImageLoader.get(context)
    private val preloadedIds = mutableSetOf<String>()

    /**
     * Preloads covers for the given books.
     * Skips books that have already been preloaded to avoid duplicate requests.
     */
    suspend fun preloadCovers(books: List<Book>) =
        withContext(Dispatchers.IO) {
            val newBooks =
                books.filter { book ->
                    val coverModel = CoverUtils.getCoverModel(book, context)
                    coverModel != null && book.id !in preloadedIds
                }

            if (newBooks.isEmpty()) {
                return@withContext
            }

            newBooks.forEach { book ->
                try {
                    val coverModel = CoverUtils.getCoverModel(book, context) ?: return@forEach

                    // Create ImageRequest for preloading
                    val imageRequest =
                        CoverUtils
                            .createCoverImageRequest(
                                book = book,
                                context = context,
                                placeholderColor =
                                    androidx.compose.ui.graphics
                                        .Color(0xFFE0E0E0),
                                errorColor =
                                    androidx.compose.ui.graphics
                                        .Color(0xFFB00020),
                                fallbackColor =
                                    androidx.compose.ui.graphics
                                        .Color(0xFFE0E0E0),
                                cornerRadius = 8f,
                            ).build()

                    // Enqueue for background loading
                    imageLoader.enqueue(imageRequest)

                    // Mark as preloaded
                    preloadedIds.add(book.id)

                    android.util.Log.v(
                        "CoverPreloader",
                        "Preloaded cover for: ${book.title}",
                    )
                } catch (e: Exception) {
                    // Silently fail - covers will load on demand
                    android.util.Log.d("CoverPreloader", "Failed to preload cover for ${book.title}", e)
                }
            }

            android.util.Log.d(
                "CoverPreloader",
                "Preloaded ${newBooks.size} covers (total preloaded: ${preloadedIds.size})",
            )
        }

    /**
     * Clears the preloaded IDs cache.
     * Useful when the book list changes significantly.
     */
    public fun clearCache() {
        preloadedIds.clear()
        android.util.Log.d("CoverPreloader", "Cleared preload cache")
    }
}
