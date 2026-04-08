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

import java.io.File
import java.security.MessageDigest

/**
 * Policy for resolving book cover art through a prioritized waterfall strategy.
 *
 * Resolution chain (most reliable first):
 * 1. **Embedded covers** — `{bookId}.jpg` extracted from ID3/Vorbis tags during scan, stored in app-local `covers/` directory.
 * 2. **Folder images** — common cover image files (`cover.jpg`, `folder.jpg`, `album.jpg`, etc.) in the book's local directory.
 *    Falls back to the largest image file in the directory if no known name matches.
 * 3. **Online URL** — `coverUrl` from the database (normalized absolute or protocol-relative URL).
 * 4. **Deterministic placeholder** — material design–style placeholder derived from book identity,
 *    ensuring every book always has a visually distinct cover even when no image is available.
 *
 * Each level is independently testable. The policy produces a [CoverWaterfallResult] describing
 * which level matched and what data to load.
 */
public object CoverWaterfallPolicy {
    /** Common image file basenames to search for in book directories (lowercase). */
    public val COMMON_COVER_NAMES: Set<String> = setOf("cover", "folder", "album", "front", "art")

    /** Supported image extensions (lowercase). */
    public val COVER_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Result of the waterfall cover resolution.
     *
     * @property source which level in the waterfall produced the result
     * @property data the data to pass to the image loader (File, String URL, or placeholder key)
     */
    public data class CoverWaterfallResult(
        val source: CoverSource,
        val data: Any,
    )

    /**
     * Enum representing each level of the waterfall.
     * Order defines priority (first match wins).
     */
    public enum class CoverSource {
        /** Cover extracted from audio file metadata tags (ID3/Vorbis). */
        EMBEDDED,

        /** Cover image file found in the book's local directory. */
        FOLDER_IMAGE,

        /** Remote cover URL from database / online source. */
        ONLINE_URL,

        /** Deterministic placeholder based on book identity. */
        PLACEHOLDER,
    }

    /**
     * Resolves the best available cover for a book using the full waterfall chain.
     *
     * @param bookId unique book identifier (String)
     * @param localPath local directory path for the book (nullable for online-only books)
     * @param coverUrl remote cover URL from database (nullable)
     * @param coversDir app-local directory where embedded covers are stored
     * @return [CoverWaterfallResult] with the best available source and data
     */
    public fun resolveCover(
        bookId: String,
        localPath: String?,
        coverUrl: String?,
        coversDir: File,
    ): CoverWaterfallResult {
        // Level 1: Embedded cover from ID3 tags (most reliable)
        resolveEmbedded(bookId, coversDir)?.let { return it }

        // Level 2: Folder image in book's local directory
        localPath?.let { path ->
            resolveFolderImage(path)?.let { return it }
        }

        // Level 3: Online URL from database
        resolveOnlineUrl(coverUrl)?.let { return it }

        // Level 4: Deterministic placeholder
        return resolvePlaceholder(bookId)
    }

    /**
     * Level 1: Checks for an embedded cover extracted from audio file metadata.
     *
     * @param bookId unique book identifier (String)
     * @param coversDir app-local covers directory
     * @return result with [CoverSource.EMBEDDED] if found, null otherwise
     */
    public fun resolveEmbedded(
        bookId: String,
        coversDir: File,
    ): CoverWaterfallResult? {
        val coverFile = File(coversDir, "$bookId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return CoverWaterfallResult(CoverSource.EMBEDDED, coverFile)
        }
        return null
    }

    /**
     * Level 2: Searches for cover images in the book's local directory.
     *
     * Resolution order within this level:
     * 1. Exact case match against common names + extensions
     * 2. Case-insensitive match against common names
     * 3. Largest image file in the directory (heuristic for unnamed covers)
     *
     * @param directoryPath path to the book's local directory
     * @return result with [CoverSource.FOLDER_IMAGE] if found, null otherwise
     */
    public fun resolveFolderImage(directoryPath: String): CoverWaterfallResult? {
        val folder = File(directoryPath)
        if (!folder.exists() || !folder.isDirectory) return null

        // Step 1: Exact case match (fastest path on case-sensitive filesystems)
        val exactCandidates =
            COMMON_COVER_NAMES.flatMap { name ->
                COVER_EXTENSIONS.map { ext -> "$name.$ext" }
            }
        for (candidate in exactCandidates) {
            val file = File(folder, candidate)
            if (file.exists() && file.isFile) {
                return CoverWaterfallResult(CoverSource.FOLDER_IMAGE, file)
            }
        }

        // Step 2 & 3: Scan directory for case-insensitive matches + collect image files
        val files = folder.listFiles() ?: return null
        var caseInsensitiveMatch: File? = null
        var largestImage: File? = null
        var largestImageSize = 0L

        for (file in files) {
            if (!file.isFile) continue
            val nameWithoutExt = file.nameWithoutExtension.lowercase()
            val ext = file.extension.lowercase()

            if (ext !in COVER_EXTENSIONS) continue

            // Case-insensitive match against common names
            if (caseInsensitiveMatch == null && nameWithoutExt in COMMON_COVER_NAMES) {
                caseInsensitiveMatch = file
            }

            // Track largest image as heuristic fallback
            if (file.length() > largestImageSize) {
                largestImage = file
                largestImageSize = file.length()
            }
        }

        // Prefer case-insensitive common name match, then largest image
        val match = caseInsensitiveMatch ?: largestImage
        return match?.let { CoverWaterfallResult(CoverSource.FOLDER_IMAGE, it) }
    }

    /**
     * Level 3: Resolves a normalized online cover URL.
     *
     * Handles:
     * - Absolute URLs (`http://`, `https://`)
     * - Protocol-relative URLs (`//example.com/cover.jpg`)
     * - Already-normalized relative URLs (passed through)
     *
     * @param coverUrl raw cover URL from database
     * @return result with [CoverSource.ONLINE_URL] if valid, null otherwise
     */
    public fun resolveOnlineUrl(coverUrl: String?): CoverWaterfallResult? {
        if (coverUrl.isNullOrBlank()) return null

        val normalized =
            when {
                coverUrl.startsWith("http://") || coverUrl.startsWith("https://") -> coverUrl
                coverUrl.startsWith("//") -> "https:$coverUrl"
                else -> coverUrl // already normalized during parsing
            }

        return CoverWaterfallResult(CoverSource.ONLINE_URL, normalized)
    }

    /**
     * Level 4: Generates a deterministic placeholder key for books without any cover image.
     *
     * The placeholder key encodes the book identity so that each book gets a visually
     * distinct placeholder. The UI layer can use this to generate a colored or
     * initials-based placeholder.
     *
     * @param bookId unique book identifier (String)
     * @return result with [CoverSource.PLACEHOLDER] containing the placeholder key
     */
    public fun resolvePlaceholder(bookId: String): CoverWaterfallResult {
        val key = generatePlaceholderKey(bookId)
        return CoverWaterfallResult(CoverSource.PLACEHOLDER, key)
    }

    /**
     * Generates a deterministic placeholder key for a book.
     * The key includes a hash component for stable color selection.
     *
     * @param bookId unique book identifier (String)
     * @return placeholder key string (e.g., "placeholder:abc123:af3b")
     */
    public fun generatePlaceholderKey(bookId: String): String {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bookId.toByteArray())
                .take(2)
                .joinToString("") { "%02x".format(it) }
        return "placeholder:$bookId:$hash"
    }

    /**
     * Extracts a color index (0..15) from a placeholder key for consistent color assignment.
     *
     * @param placeholderKey the key from [generatePlaceholderKey]
     * @return color index in range 0..15
     */
    public fun placeholderColorIndex(placeholderKey: String): Int {
        val parts = placeholderKey.split(":")
        if (parts.size >= 3) {
            val hashPart = parts[2]
            return hashPart.toIntOrNull(16)?.rem(16) ?: 0
        }
        return 0
    }
}
