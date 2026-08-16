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

package com.jabook.app.jabook.compose.feature.player

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.theme.DynamicThemeManager
import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Derives dynamic player theme colors from the book cover artwork.
 *
 * @param bookId Current book identifier
 * @param context Application context for image loading
 * @param getBookDetailsUseCase Use case for observing book details
 * @param viewModelScope Coroutine scope for collectors
 * @param loggerFactory Logger factory
 */
internal class PlayerThemeColorsHandler(
    private val bookId: String,
    private val context: Context,
    private val getBookDetailsUseCase: GetBookDetailsUseCase,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
) {
    private val logger: Logger = loggerFactory.get("PlayerThemeColorsHandler")

    private val _themeColors = MutableStateFlow<PlayerThemeColors?>(null)

    /** Extracted theme colors, or null until the first cover is processed. */
    val themeColors: StateFlow<PlayerThemeColors?> = _themeColors.asStateFlow()

    /** Load artwork and extract colors when book changes. */
    fun observe() {
        viewModelScope.launch {
            getBookDetailsUseCase(bookId).collect { book ->
                if (book?.coverUrl != null) {
                    extractColorsFromCover(book.coverUrl)
                }
            }
        }
    }

    private suspend fun extractColorsFromCover(coverUrl: String) {
        try {
            val loader = SingletonImageLoader.get(context)
            val request =
                ImageRequest
                    .Builder(context)
                    .data(coverUrl)
                    .allowHardware(false) // Software bitmap required for Palette
                    .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap()
                val colors =
                    DynamicThemeManager.extractColorsCached(
                        coverUrl = coverUrl,
                        bitmap = bitmap,
                    )
                _themeColors.value = colors
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignore errors, keep default theme
            logger.e({ "Failed to extract dynamic colors" }, e)
        }
    }
}
