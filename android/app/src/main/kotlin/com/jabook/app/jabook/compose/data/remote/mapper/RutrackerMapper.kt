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

package com.jabook.app.jabook.compose.data.remote.mapper

import android.util.Log
import com.jabook.app.jabook.compose.data.remote.model.Comment
import com.jabook.app.jabook.compose.data.remote.model.RelatedBook
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.domain.model.RutrackerComment
import com.jabook.app.jabook.compose.domain.model.RutrackerRelatedBook
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails

/**
 * Maps DTO models (data layer) to Domain models (business logic layer).
 *
 * This separation allows:
 * - Domain models to be independent of data source
 * - Business logic to work with validated, normalized data
 * - Easy testing with mock data
 */

/**
 * Maps SearchResult DTO to RutrackerSearchResult domain model.
 *
 * @return Domain model with validated data
 */
public fun SearchResult.toDomain(): RutrackerSearchResult =
    RutrackerSearchResult(
        topicId = topicId.trim(),
        title = title.trim(),
        author = author.trim(),
        category = category.trim(),
        size = size.trim(),
        seeders = seeders.coerceAtLeast(0),
        leechers = leechers.coerceAtLeast(0),
        magnetUrl = magnetUrl?.takeIf { it.isNotBlank() },
        torrentUrl = torrentUrl.trim(),
        coverUrl = coverUrl?.takeIf { it.isNotBlank() },
        uploader = uploader?.takeIf { it.isNotBlank() }?.trim(),
    )

/**
 * Maps list of SearchResult DTOs to list of RutrackerSearchResult domain models.
 *
 * Filters out invalid results.
 *
 * @return List of valid domain models
 */
public fun List<SearchResult>.toDomain(): List<RutrackerSearchResult> {
    val totalCount = this.size
    val invalidResults = mutableListOf<Pair<Int, SearchResult>>()
    val results =
        mapIndexedNotNull { index, dto ->
            val domain = dto.toDomain()
            if (domain.isValid()) {
                domain
            } else {
                invalidResults.add(index to dto)
                null
            }
        }
    val filteredCount = invalidResults.size
    if (filteredCount > 0) {
        android.util.Log.w(
            "RutrackerMapper",
            "⚠️ Filtered out $filteredCount invalid results out of $totalCount total",
        )
        invalidResults.take(5).forEach { (index, dto) ->
            android.util.Log.w(
                "RutrackerMapper",
                "  Invalid[$index]: topicId='${dto.topicId.take(20)}', " +
                    "title='${dto.title.take(30)}', " +
                    "author='${dto.author.take(20)}', " +
                    "category='${dto.category.take(20)}', " +
                    "torrentUrl='${dto.torrentUrl.take(30)}'",
            )
        }
        if (filteredCount > 5) {
            android.util.Log.w("RutrackerMapper", "  ... and ${filteredCount - 5} more invalid results")
        }
    }
    return results
}

/**
 * Maps list of SearchResult DTOs from offline index to domain models.
 *
 * Uses lenient validation (isValidForIndex) since indexed topics:
 * - May have fallback category values
 * - Don't have torrentUrl (retrieved on-demand)
 *
 * @return List of valid domain models from index
 */
public fun List<SearchResult>.toDomainFromIndex(): List<RutrackerSearchResult> {
    val totalCount = this.size
    val invalidResults = mutableListOf<Pair<Int, SearchResult>>()
    val results =
        mapIndexedNotNull { index, dto ->
            val domain = dto.toDomain()
            if (domain.isValidForIndex()) {
                domain
            } else {
                invalidResults.add(index to dto)
                null
            }
        }
    val filteredCount = invalidResults.size
    if (filteredCount > 0) {
        android.util.Log.w(
            "RutrackerMapper",
            "⚠️ [INDEX] Filtered out $filteredCount invalid indexed results out of $totalCount total",
        )
        invalidResults.take(5).forEach { (index, dto) ->
            android.util.Log.w(
                "RutrackerMapper",
                "  Invalid[$index]: topicId='${dto.topicId.take(20)}', " +
                    "title='${dto.title.take(30)}', " +
                    "author='${dto.author.take(20)}'",
            )
        }
        if (filteredCount > 5) {
            android.util.Log.w("RutrackerMapper", "  ... and ${filteredCount - 5} more invalid results")
        }
    }
    return results
}

/**
 * Maps TopicDetails DTO to RutrackerTopicDetails domain model.
 *
 * @return Domain model with validated data
 */
public fun TopicDetails.toDomain(): RutrackerTopicDetails =
    RutrackerTopicDetails(
        topicId = topicId.trim(),
        title = title.trim(),
        author = author?.takeIf { it.isNotBlank() }?.trim(),
        performer = performer?.takeIf { it.isNotBlank() }?.trim(),
        category = category.trim(),
        size = size.trim(),
        seeders = seeders.coerceAtLeast(0),
        leechers = leechers.coerceAtLeast(0),
        magnetUrl = magnetUrl?.takeIf { it.isNotBlank() },
        torrentUrl = torrentUrl.trim(),
        coverUrl = coverUrl?.takeIf { it.isNotBlank() },
        genres = genres.map { it.trim() }.filter { it.isNotBlank() },
        addedDate = addedDate?.takeIf { it.isNotBlank() }?.trim(),
        duration = duration?.takeIf { it.isNotBlank() }?.trim(),
        bitrate = bitrate?.takeIf { it.isNotBlank() }?.trim(),
        audioCodec = audioCodec?.takeIf { it.isNotBlank() }?.trim(),
        description = description?.takeIf { it.isNotBlank() }?.trim(),
        descriptionHtml = descriptionHtml?.takeIf { it.isNotBlank() },
        mediaInfo = mediaInfo, // MediaInfo is already a DTO, can be used directly
        relatedBooks = relatedBooks.mapNotNull { it.toDomain() },
        series = series?.takeIf { it.isNotBlank() }?.trim(),
        comments = comments.mapNotNull { it.toDomain() },
        registeredDate = registeredDate,
        downloadsCount = downloadsCount,
        currentPage = currentPage,
        totalPages = totalPages,
        allMetadata = allMetadata,
    )

/**
 * Maps RelatedBook DTO to RutrackerRelatedBook domain model.
 *
 * @return Domain model or null if invalid
 */
public fun RelatedBook.toDomain(): RutrackerRelatedBook? {
    val domain =
        RutrackerRelatedBook(
            topicId = topicId.trim(),
            title = title.trim(),
        )
    return if (domain.isValid()) domain else null
}

/**
 * Maps Comment DTO to RutrackerComment domain model.
 *
 * @return Domain model or null if invalid
 */
public fun Comment.toDomain(): RutrackerComment? {
    val domain =
        RutrackerComment(
            id = id.trim(),
            author = author.trim(),
            date = date.trim(),
            text = text.trim(),
            html = html?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    return if (domain.isValid()) domain else null
}
