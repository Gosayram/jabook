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
}
