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

package com.jabook.app.jabook.compose.data.remote.parser

import android.util.Log
import com.jabook.app.jabook.compose.data.remote.model.RelatedBook
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Rutracker HTML pages.
 *
 * Uses Jsoup to extract audiobook information from search results
 * and topic details pages.
 */
@Singleton
class RutrackerParser
    @Inject
    constructor() {
        companion object {
            private const val TAG = "RutrackerParser"

            // CSS Selectors for search results
            private const val ROW_SELECTOR = "tr.hl-tr"
            private const val TITLE_SELECTOR = "a.torTopic, a[href*=\"viewtopic.php?t=\"]"
            private const val AUTHOR_SELECTOR = "a.pmed, .topicAuthor a"
            private const val SIZE_SELECTOR = "a.f-dl.dl-stub"
            private const val SEEDERS_SELECTOR = "span.seed, span.seedmed"
            private const val LEECHERS_SELECTOR = "span.leech, span.leechmed"
            private const val DOWNLOAD_HREF_SELECTOR = "a[href^=\"dl.php?t=\"]"
            private const val MAGNET_LINK_SELECTOR = "a.magnet-link, a[href^=\"magnet:\"]"

            // CSS Selectors for topic details
            private const val POST_BODY_SELECTOR = ".post_body, .post-body"
            private const val MAIN_TITLE_SELECTOR = "h1.maintitle a, h1.maintitle"
            private const val TOR_SIZE_SELECTOR = "#tor-size-humn"

            private const val BASE_URL = "https://rutracker.org/forum/"
        }

        /**
         * Parse search results from HTML.
         *
         * @param html HTML content from search results page
         * @return List of search results
         */
        fun parseSearchResults(html: String): List<SearchResult> {
            try {
                val document = Jsoup.parse(html)
                val results = mutableListOf<SearchResult>()

                val rows = document.select(ROW_SELECTOR)
                Log.d(TAG, "Found ${rows.size} topic rows")

                for (row in rows) {
                    try {
                        val result = parseSearchResultRow(row)
                        if (result != null) {
                            results.add(result)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse row", e)
                    }
                }

                return results
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse search results", e)
                return emptyList()
            }
        }

        private fun parseSearchResultRow(row: Element): SearchResult? {
            // Extract topic ID
            val topicId =
                row.attr("data-topic_id").ifEmpty {
                    row
                        .select(TITLE_SELECTOR)
                        .attr("href")
                        .substringAfter("t=")
                        .substringBefore("&")
                        .ifEmpty { return null }
                }

            // Extract title
            val titleElement = row.selectFirst(TITLE_SELECTOR) ?: return null
            val title = titleElement.text().ifEmpty { return null }

            // Extract author
            val authorElement = row.selectFirst(AUTHOR_SELECTOR)
            val author = authorElement?.text() ?: "Unknown"

            // Extract category (from parent table or data attribute)
            val category = row.attr("data-forum_id").ifEmpty { "Audiobooks" }

            // Extract size
            val sizeElement = row.selectFirst(SIZE_SELECTOR)
            val size = sizeElement?.text() ?: "Unknown"

            // Extract seeders
            val seedersElement = row.selectFirst(SEEDERS_SELECTOR)
            val seeders = seedersElement?.text()?.toIntOrNull() ?: 0

            // Extract leechers
            val leechersElement = row.selectFirst(LEECHERS_SELECTOR)
            val leechers = leechersElement?.text()?.toIntOrNull() ?: 0

            // Extract magnet link
            val magnetElement = row.selectFirst(MAGNET_LINK_SELECTOR)
            val magnetUrl = magnetElement?.attr("href")

            // Extract torrent download URL
            val torrentElement = row.selectFirst(DOWNLOAD_HREF_SELECTOR)
            val torrentUrl = torrentElement?.attr("href")?.let { BASE_URL + it } ?: ""

            return SearchResult(
                topicId = topicId,
                title = title,
                author = author,
                category = category,
                size = size,
                seeders = seeders,
                leechers = leechers,
                magnetUrl = magnetUrl,
                torrentUrl = torrentUrl,
            )
        }

        /**
         * Parse topic details from HTML.
         *
         * @param html HTML content from topic details page
         * @return Topic details or null if parsing fails
         */
        fun parseTopicDetails(
            html: String,
            topicId: String,
        ): TopicDetails? {
            try {
                val document = Jsoup.parse(html)

                // Extract title
                val titleElement = document.selectFirst(MAIN_TITLE_SELECTOR) ?: return null
                val title = titleElement.text()

                // Extract post body for metadata
                val postBody = document.selectFirst(POST_BODY_SELECTOR)

                // Extract size
                val sizeElement = document.selectFirst(TOR_SIZE_SELECTOR)
                val size = sizeElement?.text() ?: "Unknown"

                // Extract magnet link
                val magnetElement = document.selectFirst(MAGNET_LINK_SELECTOR)
                val magnetUrl = magnetElement?.attr("href")

                // Extract torrent URL
                val downloadElement = document.selectFirst(DOWNLOAD_HREF_SELECTOR)
                val torrentUrl = downloadElement?.attr("href")?.let { BASE_URL + it } ?: ""

                // Extract metadata from post body
                val metadata = extractMetadata(postBody)

                return TopicDetails(
                    topicId = topicId,
                    title = title,
                    author = metadata["author"],
                    performer = metadata["performer"],
                    category = "Audiobooks",
                    size = size,
                    seeders = metadata["seeders"]?.toIntOrNull() ?: 0,
                    leechers = metadata["leechers"]?.toIntOrNull() ?: 0,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    coverUrl = extractCoverUrl(postBody),
                    genres = extractGenres(postBody),
                    addedDate = metadata["addedDate"],
                    duration = metadata["duration"],
                    bitrate = metadata["bitrate"],
                    audioCodec = metadata["codec"],
                    description = postBody?.text(),
                    relatedBooks = extractRelatedBooks(postBody),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse topic details", e)
                return null
            }
        }

        private fun extractMetadata(postBody: Element?): Map<String, String> {
            val metadata = mutableMapOf<String, String>()
            if (postBody == null) return metadata

            val text = postBody.text()

            // Extract common patterns
            // Author: Pattern like "Автор:" or "Author:"
            "Автор[:\\s]+(.+?)(?=\\n|Исполнитель|Год|$)"
                .toRegex()
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.let {
                    metadata["author"] = it
                }

            // Performer: Pattern like "Исполнитель:" or "Narrator:"
            "Исполнитель[:\\s]+(.+?)(?=\\n|Год|Жанр|$)"
                .toRegex()
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.let {
                    metadata["performer"] = it
                }

            // Duration: Pattern like "Время звучания:"
            "Время звучания[:\\s]+(.+?)(?=\\n|$)"
                .toRegex()
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.let {
                    metadata["duration"] = it
                }

            // Bitrate: Pattern like "Битрейт:" or "kbps"
            "Битрейт[:\\s]+(.+?)(?=\\n|$)"
                .toRegex()
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.let {
                    metadata["bitrate"] = it
                }

            return metadata
        }

        private fun extractCoverUrl(postBody: Element?): String? {
            if (postBody == null) return null

            // Look for first image in post
            val img = postBody.selectFirst("img[src]")
            return img?.attr("abs:src")
        }

        private fun extractGenres(postBody: Element?): List<String> {
            if (postBody == null) return emptyList()

            val text = postBody.text()
            val genrePattern = "Жанр[:\\s]+(.+?)(?=\\n|$)".toRegex()
            val match = genrePattern.find(text) ?: return emptyList()

            return match.groupValues[1]
                .split(",", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        private fun extractRelatedBooks(postBody: Element?): List<RelatedBook> {
            if (postBody == null) return emptyList()

            val related = mutableListOf<RelatedBook>()

            // Look for links to other topics
            val links = postBody.select("a[href*=\"viewtopic.php?t=\"]")
            for (link in links) {
                val href = link.attr("href")
                val topicId = href.substringAfter("t=").substringBefore("&")
                val title = link.text()

                if (topicId.isNotEmpty() && title.isNotEmpty()) {
                    related.add(RelatedBook(topicId, title))
                }
            }

            return related.take(10) // Limit to 10 related books
        }
    }
