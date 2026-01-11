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
    public val topicId: String,
    /** Full title */
    public val title: String,
    /** Author or narrator info */
    public val author: String?,
    /** Book performer/reader */
    public val performer: String?,
    /** Category */
    public val category: String,
    /** File size */
    public val size: String,
    /** Seeders count */
    public val seeders: Int,
    /** Leechers count */
    public val leechers: Int,
    /** Magnet link */
    public val magnetUrl: String?,
    /** Torrent file URL */
    public val torrentUrl: String,
    /** Cover image URL */
    public val coverUrl: String?,
    /** List of genres */
    public val genres: List<String>,
    /** Date added */
    public val addedDate: String?,
    /** Duration (e.g., "08:05:13") */
    public val duration: String?,
    /** Bitrate (e.g., "128 kbps") */
    public val bitrate: String?,
    /** Audio codec (e.g., "MP3") */
    public val audioCodec: String?,
    /** Description/content (plain text) */
    public val description: String?,
    /** Description HTML (preserves links and formatting) */
    public val descriptionHtml: String? = null,
    /** Structured MediaInfo data */
    public val mediaInfo: com.jabook.app.jabook.compose.data.remote.model.MediaInfo? = null,
    /** Related audiobooks from same series */
    public val relatedBooks: List<RutrackerRelatedBook>,
    /** Series/cycle name */
    public val series: String? = null,
    /** Comments from topic */
    public val comments: List<RutrackerComment> = emptyList(),
    /** Registration date */
    public val registeredDate: String? = null,
    /** Download count */
    public val downloadsCount: String? = null,
    public val currentPage: Int = 1,
    public val totalPages: Int = 1,
    /** All extracted metadata fields */
    public val allMetadata: Map<String, String> = emptyMap(),
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
public data class RutrackerRelatedBook(
    /** Topic ID */
    public val topicId: String,
    /** Book title */
    public val title: String,
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
public data class RutrackerComment(
    /** Comment ID */
    public val id: String,
    /** Author username */
    public val author: String,
    /** Comment date/time */
    public val date: String,
    /** Comment text (plain text, links removed) */
    public val text: String,
    /** Comment HTML (preserves links and formatting) */
    public val html: String? = null,
    /** Avatar image URL (optional) */
    public val avatarUrl: String? = null,
) {
    /**
     * Validates that the comment has required fields.
     *
     * @return true if valid, false otherwise
     */
    public fun isValid(): Boolean = id.isNotBlank() && author.isNotBlank() && text.isNotBlank()
}
