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

/**
 * Domain model for RuTracker topic details.
 *
 * This is the business logic representation of topic details,
 * separated from the data layer DTO (TopicDetails in data.remote.model).
 *
 * Domain models are:
 * - Used in business logic and UI layers
 * - Independent of data source (network, database, cache)
 * - Validated and normalized
 */
public data class RutrackerTopicDetails(
    /** Topic ID */
    val topicId: String,
    /** Full title */
    val title: String,
    /** Author or narrator info */
    val author: String?,
    /** Book performer/reader */
    val performer: String?,
    /** Category */
    val category: String,
    /** File size */
    val size: String,
    /** Seeders count */
    val seeders: Int,
    /** Leechers count */
    val leechers: Int,
    /** Magnet link */
    val magnetUrl: String?,
    /** Torrent file URL */
    val torrentUrl: String,
    /** Cover image URL */
    val coverUrl: String?,
    /** List of genres */
    val genres: List<String>,
    /** Date added */
    val addedDate: String?,
    /** Duration (e.g., "08:05:13") */
    val duration: String?,
    /** Bitrate (e.g., "128 kbps") */
    val bitrate: String?,
    /** Audio codec (e.g., "MP3") */
    val audioCodec: String?,
    /** Description/content (plain text) */
    val description: String?,
    /** Description HTML (preserves links and formatting) */
    val descriptionHtml: String? = null,
    /** Structured MediaInfo data */
    val mediaInfo: com.jabook.app.jabook.compose.data.remote.model.MediaInfo? = null,
    /** Related audiobooks from same series */
    val relatedBooks: List<RutrackerRelatedBook>,
    /** Series/cycle name */
    val series: String? = null,
    /** Comments from topic */
    val comments: List<RutrackerComment> = emptyList(),
    /** Registration date */
    val registeredDate: String? = null,
    /** Download count */
    val downloadsCount: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    /** All extracted metadata fields */
    val allMetadata: Map<String, String> = emptyMap(),
) {
    /**
     * Validates that the topic details have required fields.
     *
     * @return true if valid, false otherwise
     */
    public fun isValid(): Boolean =
        topicId.isNotBlank() &&
            title.isNotBlank() &&
            category.isNotBlank() &&
            torrentUrl.isNotBlank()

    /**
     * Validates that the topic details have enough fields for a pagination page.
     * Pages > 1 might not have torrent info (magnet, size, etc.) but should have comments.
     */
    public fun isValidForPagination(): Boolean =
        topicId.isNotBlank() &&
            title.isNotBlank() &&
            comments.isNotEmpty()

    /**
     * Checks if download is available (has magnet or torrent URL).
     *
     * @return true if download is available
     */
    public fun hasDownloadUrl(): Boolean = !magnetUrl.isNullOrBlank() || torrentUrl.isNotBlank()

    /**
     * Checks if cover image is available.
     *
     * @return true if cover URL is available
     */
    public fun hasCover(): Boolean = !coverUrl.isNullOrBlank()
}

/**
 * Domain model for related audiobook.
 */
@Immutable
public data class RutrackerRelatedBook(
    /** Topic ID */
    val topicId: String,
    /** Book title */
    val title: String,
) {
    /**
     * Validates that the related book has required fields.
     *
     * @return true if valid, false otherwise
     */
    public fun isValid(): Boolean = topicId.isNotBlank() && title.isNotBlank()
}

/**
 * Domain model for comment.
 */
@Immutable
public data class RutrackerComment(
    /** Comment ID */
    val id: String,
    /** Author username */
    val author: String,
    /** Comment date/time */
    val date: String,
    /** Comment text (plain text, links removed) */
    val text: String,
    /** Comment HTML (preserves links and formatting) */
    val html: String? = null,
    /** Avatar image URL (optional) */
    val avatarUrl: String? = null,
) {
    /**
     * Validates that the comment has required fields.
     *
     * @return true if valid, false otherwise
     */
    public fun isValid(): Boolean = id.isNotBlank() && author.isNotBlank() && text.isNotBlank()
}
