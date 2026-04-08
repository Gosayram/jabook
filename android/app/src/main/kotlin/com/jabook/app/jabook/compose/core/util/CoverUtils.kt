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
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.asImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import coil3.transform.Transformation
import com.jabook.app.jabook.compose.domain.model.Book
import java.io.File

/**
 * Utility object for book cover operations.
 */
public object CoverUtils {
    /**
     * Gets the cover image file for a book.
     * Returns File pointing to cover.jpg in book's localPath, or null if book has no local path.
     *
     * @param book The book to get cover for
     * @return File pointing to cover.jpg, or null
     */
    public fun getCoverFile(book: Book): File? =
        book.localPath?.let { path ->
            File(path, "cover.jpg")
        }

    /**
     * Gets the cover image model for use with Coil AsyncImage.
     *
     * Delegates to [CoverWaterfallPolicy] for the full waterfall resolution:
     * 1. Embedded covers (ID3 tags extracted during scan)
     * 2. Folder images (cover.jpg, folder.jpg, etc.)
     * 3. Online URL from database
     * 4. Deterministic placeholder (always returns a non-null model)
     *
     * @param book The book to get cover for
     * @param context Android context (needed to access app storage)
     * @return File, URL string, or placeholder key (never null when using policy)
     */
    public fun getCoverModel(
        book: Book,
        context: android.content.Context,
    ): Any? {
        val coversDir = File(context.filesDir, "covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = book.id,
                localPath = book.localPath,
                coverUrl = book.coverUrl,
                coversDir = coversDir,
            )

        // Placeholder source returns a key string — Coil can't load it directly,
        // so we return null to let the caller use fallback/error drawables.
        // The placeholder key is available via result.data if the caller wants custom handling.
        return when (result.source) {
            CoverWaterfallPolicy.CoverSource.PLACEHOLDER -> null
            else -> result.data
        }
    }

    /**
     * Gets the full waterfall result including source metadata.
     *
     * Use this when the caller needs to know which source matched,
     * or wants access to the deterministic placeholder key.
     *
     * @param book The book to get cover for
     * @param context Android context
     * @return [CoverWaterfallPolicy.CoverWaterfallResult] with source and data
     */
    public fun getCoverWaterfallResult(
        book: Book,
        context: android.content.Context,
    ): CoverWaterfallPolicy.CoverWaterfallResult {
        val coversDir = File(context.filesDir, "covers")
        return CoverWaterfallPolicy.resolveCover(
            bookId = book.id,
            localPath = book.localPath,
            coverUrl = book.coverUrl,
            coversDir = coversDir,
        )
    }

    /**
     * Creates an ImageRequest for a book cover with placeholder, error, fallback, and optional transformations.
     *
     * @param book The book to get cover for
     * @param context Android context
     * @param placeholderColor Color for placeholder (default: surfaceVariant)
     * @param errorColor Color for error state (default: error)
     * @param fallbackColor Color for fallback when no image available (default: surfaceVariant)
     * @param cornerRadius Radius for rounded corners in dp (0 = no rounding, null = use default 8.dp)
     * @param circleCrop Whether to apply circle crop transformation (overrides cornerRadius)
     * @param allowHardware Whether to allow hardware bitmaps for better performance (default: true)
     * @return ImageRequest.Builder ready to build
     */
    public fun createCoverImageRequest(
        book: Book,
        context: Context,
        placeholderColor: Color = Color(0xFFE0E0E0), // Light gray
        errorColor: Color = Color(0xFFB00020), // Material error color
        fallbackColor: Color = Color(0xFFE0E0E0), // Light gray
        cornerRadius: Float? = 8f, // 8dp default
        circleCrop: Boolean = false,
        allowHardware: Boolean = true,
    ): ImageRequest.Builder {
        val data = getCoverModel(book, context)
        val transformations = mutableListOf<Transformation>()

        // Add transformations
        when {
            circleCrop -> transformations.add(CircleCropTransformation())
            cornerRadius != null && cornerRadius > 0 -> {
                // Convert dp to pixels using device density
                val density = context.resources.displayMetrics.density
                val radiusPx = cornerRadius * density
                transformations.add(RoundedCornersTransformation(radiusPx))
            }
        }

        val builder =
            ImageRequest
                .Builder(context)
                .data(data)
                .crossfade(true)
                .allowHardware(allowHardware)
                .placeholder(ColorDrawable(placeholderColor.toArgb()).asImage())
                .error(ColorDrawable(errorColor.toArgb()).asImage())
                .fallback(ColorDrawable(fallbackColor.toArgb()).asImage())

        // Note: size parameter is kept for API compatibility but not used in Coil3
        // Coil3 automatically optimizes image sizes based on display requirements

        if (transformations.isNotEmpty()) {
            builder.transformations(transformations)
        }

        return builder
    }
}
