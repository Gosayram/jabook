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

import androidx.compose.runtime.Immutable
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.model.DownloadStatus
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Domain model representing an audiobook.
 *
 * This is the business logic representation of a book, used throughout
 * the presentation and domain layers. It provides type-safe access to
 * book properties and computed values.
 *
 * @property id Unique identifier
 * @property title Book title
 * @property author Author name
 * @property coverUrl URL to cover image (nullable)
 * @property description Book description/synopsis
 * @property totalDuration Total duration of all chapters
 * @property currentPosition Current playback position
 * @property progress Playback progress as percentage (0.0 to 1.0)
 * @property currentChapterIndex Index of current chapter
 * @property downloadStatus Current download state
 * @property downloadProgress Download completion percentage (0.0 to 1.0)
 * @property localPath Local file system path if downloaded
 * @property addedDate When book was added to library
 * @property lastPlayedDate When book was last played (nullable)
 * @property isFavorite Whether user has favorited this book
 * @property sourceUrl Source URL where book was obtained
 */
@Immutable
public data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val description: String?,
    val totalDuration: Duration,
    val currentPosition: Duration,
    val progress: Float,
    val currentChapterIndex: Int,
    val downloadStatus: DownloadStatus,
    val downloadProgress: Float,
    val localPath: String?,
    val addedDate: Long,
    val lastPlayedDate: Long?,
    val isFavorite: Boolean,
    val sourceUrl: String?,
    val rewindDuration: Int? = null,
    val forwardDuration: Int? = null,
) {
    /**
     * Calculated remaining duration to complete the book.
     */
    val remainingDuration: Duration
        get() = totalDuration - currentPosition

    /**
     * Whether the book has been started (position > 0).
     */
    val isStarted: Boolean
        get() = currentPosition.inWholeMilliseconds > 0

    /**
     * Whether the book is currently downloading.
     */
    val isDownloading: Boolean
        get() = downloadStatus == DownloadStatus.DOWNLOADING

    /**
     * Whether the book is fully downloaded.
     */
    val isDownloaded: Boolean
        get() = downloadStatus == DownloadStatus.DOWNLOADED

    /**
     * Whether the book can be played offline.
     */
    val isAvailableOffline: Boolean
        get() = isDownloaded

    /**
     * Whether the book is completed (progress >= 0.98).
     * Uses 98% threshold to account for floating point precision.
     */
    val isCompleted: Boolean
        get() = progress >= 0.98f

    public companion object {
        /**
         * Creates an empty Book instance for preview/testing.
         */
        public fun preview(): Book =
            Book(
                id = "1",
                title = "Sample Audiobook",
                author = "John Doe",
                coverUrl = null,
                description = "A sample audiobook for testing",
                totalDuration = 3600.milliseconds,
                currentPosition = 1800.milliseconds,
                progress = 0.5f,
                currentChapterIndex = 0,
                downloadStatus = DownloadStatus.NOT_DOWNLOADED,
                downloadProgress = 0f,
                localPath = null,
                addedDate = System.currentTimeMillis(),
                lastPlayedDate = System.currentTimeMillis(),
                isFavorite = false,
                sourceUrl = null,
            )
    }
}

/**
 * Extension function to convert BookEntity to domain Book model.
 */
public fun BookEntity.toBook(): Book =
    Book(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        totalDuration = totalDuration.milliseconds,
        currentPosition = currentPosition.milliseconds,
        progress = totalProgress,
        currentChapterIndex = currentChapterIndex,
        downloadStatus = DownloadStatus.fromString(downloadStatus),
        downloadProgress = downloadProgress,
        localPath = localPath,
        addedDate = addedDate,
        lastPlayedDate = lastPlayedDate,
        isFavorite = isFavorite,
        sourceUrl = sourceUrl,
        rewindDuration = rewindDuration,
        forwardDuration = forwardDuration,
    )

/**
 * Extension function to convert domain Book to BookEntity.
 */
public fun Book.toEntity(): BookEntity =
    BookEntity(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        totalDuration = totalDuration.inWholeMilliseconds,
        currentPosition = currentPosition.inWholeMilliseconds,
        totalProgress = progress,
        currentChapterIndex = currentChapterIndex,
        downloadStatus = downloadStatus.name,
        downloadProgress = downloadProgress,
        localPath = localPath,
        addedDate = addedDate,
        lastPlayedDate = lastPlayedDate,
        isFavorite = isFavorite,
        sourceUrl = sourceUrl,
        rewindDuration = rewindDuration,
        forwardDuration = forwardDuration,
        isDownloaded = downloadStatus == DownloadStatus.DOWNLOADED,
    )

/**
 * Extension function to convert list of BookEntities to list of Books.
 */
public fun List<BookEntity>.toBooks(): List<Book> = map { it.toBook() }

/**
 * Extension function to convert domain Book to FavoriteItem.
 * Used for synchronizing local library favorites with favorites repository.
 */
public fun Book.toFavoriteItem(
    addedToFavorites: String =
        java.time.Instant
            .now()
            .toString(),
): FavoriteItem? {
    // Trim and validate sourceUrl as a proper URI with magnet scheme
    val validatedSourceUrl =
        sourceUrl
            ?.trim() // Remove leading/trailing whitespace
            ?.takeIf { it.isNotEmpty() } // Reject empty strings
            ?.takeIf { rawUrl ->
                // Parse as URI and validate scheme and structure
                runCatching {
                    val parsed = URI(rawUrl)
                    // Ensure scheme is "magnet" and schemeSpecificPart is not blank
                    parsed.scheme?.equals("magnet", ignoreCase = true) == true &&
                        !parsed.schemeSpecificPart.isNullOrBlank()
                }.getOrDefault(false)
            } ?: return null // Return null if validation fails

    return FavoriteItem(
        topicId = id,
        title = title,
        author = author,
        category = "Audiobooks", // Default category for local books
        size = "", // Size not available for local books
        seeders = 0,
        leechers = 0,
        magnetUrl = validatedSourceUrl,
        coverUrl = coverUrl,
        performer = null,
        genres = null,
        addedDate =
            java.time.Instant
                .ofEpochMilli(addedDate)
                .toString(),
        addedToFavorites = addedToFavorites,
        duration = null,
        bitrate = null,
        audioCodec = null,
    )
}