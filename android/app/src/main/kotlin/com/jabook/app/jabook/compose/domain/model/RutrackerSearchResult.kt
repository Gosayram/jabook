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
 * Domain model for RuTracker search result.
 *
 * This is the business logic representation of a search result,
 * separated from the data layer DTO (SearchResult in data.remote.model).
 *
 * Domain models are:
 * - Used in business logic and UI layers
 * - Independent of data source (network, database, cache)
 * - Validated and normalized
 */
@Immutable
public data class RutrackerSearchResult(
    /** Unique topic ID */
    val topicId: String,
    /** Book title */
    val title: String,
    /** Author/narrator */
    val author: String,
    /** Category/genre */
    val category: String,
    /** File size (formatted, e.g., "1.5 GB") */
    val size: String,
    /** Number of seeders */
    val seeders: Int,
    /** Number of leechers */
    val leechers: Int,
    /** Magnet link for direct download (nullable - may not be available in search results) */
    val magnetUrl: String?,
    /** Torrent download URL */
    val torrentUrl: String,
    /** Cover image URL (optional, may not be available in search results) */
    val coverUrl: String? = null,
    /** Uploader nickname (optional) */
    val uploader: String? = null,
) {
    /**
     * Validates that the search result has required fields.
     *
     * @return true if valid, false otherwise
     */
    public fun isValid(): Boolean =
        topicId.isNotBlank() &&
            title.isNotBlank() &&
            author.isNotBlank() &&
            category.isNotBlank() &&
            torrentUrl.isNotBlank()

    /**
     * Validates search result from offline index (more lenient).
     *
     * For indexed topics:
     * - category is optional (will have fallback value)
     * - torrentUrl is optional (retrieved on-demand via getTopicDetails())
     *
     * @return true if valid for index, false otherwise
     */
    public fun isValidForIndex(): Boolean =
        topicId.isNotBlank() &&
            title.isNotBlank() &&
            author.isNotBlank()
    // category and torrentUrl are optional for indexed results

    /**
     * Validates search result from network (strict).
     *
     * For network results, all fields must be present.
     *
     * @return true if valid for network, false otherwise
     */
    public fun isValidForNetwork(): Boolean =
        topicId.isNotBlank() &&
            title.isNotBlank() &&
            author.isNotBlank() &&
            category.isNotBlank() &&
            torrentUrl.isNotBlank()

    /**
     * Checks if download is available (has magnet or torrent URL).
     *
     * @return true if download is available
     */
    public fun hasDownloadUrl(): Boolean = !magnetUrl.isNullOrBlank() || torrentUrl.isNotBlank()
}
