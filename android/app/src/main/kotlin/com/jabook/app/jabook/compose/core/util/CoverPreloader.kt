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
import androidx.compose.runtime.snapshotFlow
import coil3.SingletonImageLoader
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.crash.CrashDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Logger for CoverPreloader.
 */
private val coverPreloaderLogger by lazy { LoggerFactoryImpl().get("CoverPreloader") }

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
        remember(context) {
            CoverPreloader(context)
        }

    LaunchedEffect(books, listState, preloadAhead) {
        snapshotFlow {
            val firstVisible = listState.firstVisibleItemIndex
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size
            (firstVisible + visibleCount + preloadAhead).coerceAtMost(books.size) to firstVisible
        }.distinctUntilChanged().collect { (preloadEnd, firstVisible) ->
            // Preload covers for visible items and items ahead
            val booksToPreload = books.subList(firstVisible.coerceAtLeast(0), preloadEnd)
            preloader.preloadCovers(booksToPreload)
        }
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
        remember(context) {
            CoverPreloader(context)
        }

    LaunchedEffect(books, gridState, preloadAhead) {
        snapshotFlow {
            val firstVisible = gridState.firstVisibleItemIndex
            val visibleCount = gridState.layoutInfo.visibleItemsInfo.size
            (firstVisible + visibleCount + preloadAhead).coerceAtMost(books.size) to firstVisible
        }.distinctUntilChanged().collect { (preloadEnd, firstVisible) ->
            // Preload covers for visible items and items ahead
            val booksToPreload = books.subList(firstVisible.coerceAtLeast(0), preloadEnd)
            preloader.preloadCovers(booksToPreload)
        }
    }
}

/**
 * Helper class for preloading cover images.
 */
private class CoverPreloader(
    private val context: Context,
) {
    private val imageLoader by lazy { SingletonImageLoader.get(context) }

    /**
     * Preloads covers for the given books.
     * Coil deduplicates identical requests internally.
     */
    public suspend fun preloadCovers(books: List<Book>) =
        withContext(Dispatchers.IO) {
            val booksToPreload =
                books.filter { book ->
                    CoverUtils.getCoverModel(book, context) != null
                }

            if (booksToPreload.isEmpty()) {
                return@withContext
            }

            booksToPreload.forEach { book ->
                try {
                    val coverModel = CoverUtils.getCoverModel(book, context) ?: return@forEach

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
                            ).size(200, 280)
                            .build()

                    imageLoader.enqueue(imageRequest)

                    coverPreloaderLogger.v { "Preloaded cover for: ${book.title}" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    coverPreloaderLogger.e({ "Failed to preload cover for ${book.title}" }, e)
                    CrashDiagnostics.reportNonFatal(
                        tag = "cover_preload_failed",
                        throwable = e,
                        attributes =
                            mapOf(
                                "book_id" to book.id,
                                "book_title" to book.title,
                            ),
                    )
                }
            }

            coverPreloaderLogger.d { "Preloaded ${booksToPreload.size} covers" }
        }

    public fun clearCache() {
        coverPreloaderLogger.d { "Cleared preload cache" }
    }
}
