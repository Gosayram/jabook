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

package com.jabook.app.jabook.compose.core.util

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.asImage
import coil3.request.ImageRequest
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
object CoverUtils {
    /**
     * Gets the cover image file for a book.
     * Returns File pointing to cover.jpg in book's localPath, or null if book has no local path.
     *
     * @param book The book to get cover for
     * @return File pointing to cover.jpg, or null
     */
    fun getCoverFile(book: Book): File? =
        book.localPath?.let { path ->
            File(path, "cover.jpg")
        }

    /**
     * Gets the cover image model for use with Coil AsyncImage.
     *
     * Priority (most reliable first):
     * 1. cover.jpg in book folder (from torrent/user)
     * 2. {bookId}.jpg in app storage (extracted from metadata)
     * 3. coverUrl from DB (for online books)
     *
     * @param book The book to get cover for
     * @param context Android context (needed to access app storage)
     * @return File pointing to cover, or coverUrl, or null
     */
    fun getCoverModel(
        book: Book,
        context: android.content.Context,
    ): Any? {
        // Priority 1: cover.jpg in book folder (torrent/user provided)
        // Priority 1: Check for common cover files in the book folder
        book.localPath?.let { path ->
            val folder = File(path)
            if (folder.exists() && folder.isDirectory) {
                // Common names to check (case-insensitive approach below)
                val commonNames = setOf("cover", "folder", "album", "front", "art")
                val extensions = setOf("jpg", "jpeg", "png", "webp")

                // First, check exact matches (fastest)
                val candidates =
                    commonNames.flatMap { name ->
                        extensions.map { ext -> "$name.$ext" }
                    }

                // Check strict case first (Linux/Android is case sensitive)
                for (name in candidates) {
                    val file = File(folder, name)
                    if (file.exists()) return file
                }

                // Fallback: simple case-insensitive search if directory listing isn't too huge
                // This handles "Cover.jpg", "FOLDER.JPG", etc.
                val files = folder.listFiles()
                val imageFiles = mutableListOf<File>()

                if (files != null) {
                    for (file in files) {
                        if (file.isFile) {
                            val nameWithoutExt = file.nameWithoutExtension.lowercase()
                            val ext = file.extension.lowercase()

                            // Check common names
                            if (nameWithoutExt in commonNames && ext in extensions) {
                                return file
                            }

                            // Collect any valid image for fallback
                            if (ext in extensions) {
                                imageFiles.add(file)
                            }
                        }
                    }
                }

                // Final Fallback: Random filename?
                // Pick the largest image file (likely the high-res cover)
                if (imageFiles.isNotEmpty()) {
                    return imageFiles.maxByOrNull { it.length() }
                }
            }
        }

        // Priority 2: App storage (extracted from metadata during scan)
        val coversDir = File(context.filesDir, "covers")
        val appCover = File(coversDir, "${book.id}.jpg")
        if (appCover.exists()) {
            return appCover
        }

        // Priority 3: coverUrl for online books
        return book.coverUrl
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
     * @return ImageRequest.Builder ready to build
     */
    fun createCoverImageRequest(
        book: Book,
        context: Context,
        placeholderColor: Color = Color(0xFFE0E0E0), // Light gray
        errorColor: Color = Color(0xFFB00020), // Material error color
        fallbackColor: Color = Color(0xFFE0E0E0), // Light gray
        cornerRadius: Float? = 8f, // 8dp default
        circleCrop: Boolean = false,
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
                .placeholder(ColorDrawable(placeholderColor.toArgb()).asImage())
                .error(ColorDrawable(errorColor.toArgb()).asImage())
                .fallback(ColorDrawable(fallbackColor.toArgb()).asImage())

        if (transformations.isNotEmpty()) {
            builder.transformations(transformations)
        }

        return builder
    }
}
