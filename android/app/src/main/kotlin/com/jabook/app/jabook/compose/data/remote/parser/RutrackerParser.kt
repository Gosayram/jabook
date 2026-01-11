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

package com.jabook.app.jabook.compose.data.remote.parser

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import com.jabook.app.jabook.compose.data.remote.model.RelatedBook
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

// Safe parsing extensions are in the same package, no import needed

/**
 * Parser for Rutracker HTML pages.
 *
 * Uses Jsoup to extract audiobook information from search results
 * and topic details pages.
 *
 * Best practices applied:
 * - Always use baseUri in Jsoup.parse() for proper absolute URL resolution
 * - Use absUrl() instead of manual URL concatenation
 * - Use selectFirst() instead of select().firstOrNull() for better performance
 * - Use selectStream() for lazy evaluation of large element lists (jsoup 1.19.1+)
 */
@Singleton
public class RutrackerParser
    @Inject
    constructor(
        private val mediaInfoParser: MediaInfoParser,
        private val decoder: com.jabook.app.jabook.compose.data.remote.encoding.RutrackerSimpleDecoder,
        private val fieldExtractor: DefensiveFieldExtractor,
        private val coverExtractor: CoverUrlExtractor,
        private val mirrorManager: com.jabook.app.jabook.compose.data.network.MirrorManager,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("RutrackerParser")
        public companion object {

            // CSS Selectors for search results - UPDATED for 2025 based on robust Dart implementation
            // Primary and fallback selectors for rows
            // Note: Some forums may have td.vf-col-icon, so we need to handle parent tr
            private val ROW_SELECTORS =
                listOf(
                    "tr.hl-tr",
                    "tr[data-topic_id]",
                    "table.forumline tr.hl-tr", // More specific
                    "table.forumline tr[id^='t-']", // ID-based
                    "tbody#TORRENT_LIST_TBODY tr", // Structure-based
                    "table.forumline tr", // Generic forum table rows
                    "tr:has(td.vf-col-icon)", // Rows containing vf-col-icon cells
                    "tr:has(td[class*='vf-col'])", // Rows with vf-col cells
                )

            private const val TITLE_SELECTOR = "a[id^='tt-'], a.tt-text, a.torTopic:not(.t-is-unread)"
            private const val AUTHOR_SELECTOR = "a.topicAuthor, a.pmed, a[href*='profile.php']"
            private const val SIZE_SELECTOR = "a.f-dl, a.dl-stub, span.small, td.small, div.small"

            // Seeders/Leechers: include both with and without 'b' tag, and generic classes
            private const val SEEDERS_SELECTOR = "span.seedmed b, b.seedmed, span.seed b, .seed b, .seedmed, .seed"
            private const val LEECHERS_SELECTOR = "span.leechmed b, b.leechmed, span.leech b, .leech b, .leechmed, .leech"

            // Additional selectors
            private const val TOPIC_ID_ATTR = "data-topic_id"
            private const val MAGNET_LINK_SELECTOR = "a.magnet-link, a[href^='magnet:']"
            private const val DOWNLOADS_SELECTOR = "td.vf-col-replies b"
            private const val DOWNLOAD_HREF_SELECTOR = "a[href^=\"dl.php?t=\"]"

            // CSS Selectors for topic details
            private const val POST_BODY_SELECTOR = ".post_body, .post-body"
            private const val MAIN_TITLE_SELECTOR = "h1.maintitle a, h1.maintitle"
            private const val TOR_SIZE_SELECTOR = "#tor-size-humn"
        }

        /**
         * Get base URL for current mirror (for Jsoup parsing).
         */
        private fun getBaseUrl(): String = "${mirrorManager.getBaseUrl()}/forum/"

        /**
         * Parse search results from raw bytes with encoding detection.
         *
         * This method uses RutrackerSimpleDecoder to properly decode
         * RuTracker's windows-1251 or mixed encoding content.
         *
         * @param bytes Raw response bytes
         * @param contentType Optional Content-Type header
         * @return ParsingResult with search results
         */
        public fun parseSearchResultsWithEncoding(
            bytes: ByteArray,
            contentType: String? = null,
        ): ParsingResult<List<SearchResult>> {
            // Decode with simple decoder (matching Flutter implementation)
            val decodedHtml = decoder.decode(bytes, contentType)

            // Parse the decoded HTML
            return parseSearchResultsDefensive(decodedHtml, bytes)
        }

        /**
         * Parse search results with graceful error handling.
         *
         * Returns ParsingResult instead of crashing on errors.
         *
         * @param html Decoded HTML content
         * @param rawBytes Raw bytes for error reporting
         * @return ParsingResult with results, warnings, or errors
         */
        private fun parseSearchResultsDefensive(
            html: String,
            rawBytes: ByteArray,
        ): ParsingResult<List<SearchResult>> {
            val errors = mutableListOf<ParsingError>()
            val results = mutableListOf<SearchResult>()

            try {
                // Parse with baseUri for proper absolute URL resolution (using current mirror)
                val document = Jsoup.parse(html, getBaseUrl())

                // Check if it's a valid search page first (before strict validation)
                // This allows empty results to pass through
                val isSearchPage = document.select("form#quick-search, input[name=nm]").isNotEmpty()
                val isIndexPage = document.select("#forums_list_wrap").isNotEmpty()

                // Only validate if it's NOT a search/index page (to avoid blocking valid empty results)
                // For search pages, we'll validate after checking for rows
                if (!isSearchPage && !isIndexPage) {
                    val validationError = ParsingValidators.validateSearchResults(html)
                    if (validationError != null) {
                        errors.add(
                            ParsingError(
                                field = "document",
                                reason = "Content validation failed: ${validationError.message}",
                                severity = ErrorSeverity.CRITICAL,
                                htmlSnippet = html.take(500),
                            ),
                        )
                        return ParsingResult.Failure(errors, emptyList())
                    }
                }

                // Try to find rows using multiple selectors strategy
                var rows = org.jsoup.select.Elements()
                var successfulSelector: String = ""
                for (selector in ROW_SELECTORS) {
                    val found = document.select(selector)
                    if (found.isNotEmpty()) {
                        // Filter out header/ad rows if generic selector used
                        val validRows =
                            found.filter { row ->
                                // Basic validation: must have some content/structure
                                // Use selectFirst() for better performance (returns null if not found)
                                !row.hasClass("vf-col-header-row") &&
                                    row.selectFirst(TITLE_SELECTOR) != null
                            }

                        if (validRows.isNotEmpty()) {
                            rows = org.jsoup.select.Elements(validRows)
                            successfulSelector = selector
                            logger.d { "Found ${rows.size} rows using validation-checked selector: $selector" }
                            break
                        }

                        // If selected rows were all invalid, try next selector
                    }
                }

                if (rows.isEmpty()) {
                    // Enhanced Debug Logging
                    val title = document.title()
                    val bodySnippet = document.body().text().take(500)
                    logger.w { "NO ROWS FOUND. Details:" }
                    logger.w { "Title: '$title'" }
                    logger.w { "IsSearchPage: $isSearchPage, IsIndexPage: $isIndexPage" }
                    logger.w { "Body Text (first 500): $bodySnippet" }
                    logger.w { "HTML (first 2000): ${document.outerHtml().take(2000)}" }

                    if (isSearchPage || isIndexPage) {
                        logger.i { "No rows found, but page looks like valid search/index page (empty results)" }
                        // For valid search pages with no results, validate now to catch real errors
                        // but still return empty list if it's just empty results
                        val validationError = ParsingValidators.validateSearchResults(html)
                        if (validationError != null && validationError !is RuTrackerError.NoData) {
                            // Only log warning if it's a real error (not just empty data)
                            logger.w { "Validation error detected on empty search page: ${validationError.message}" }
                            // Still return empty list to avoid blocking valid empty results
                        }
                        return ParsingResult.Success(emptyList())
                    }

                    // Not a search/index page and no rows - validate to check for errors
                    val validationError = ParsingValidators.validateSearchResults(html)
                    if (validationError != null) {
                        errors.add(
                            ParsingError(
                                field = "document",
                                reason = "Content validation failed: ${validationError.message}",
                                severity = ErrorSeverity.CRITICAL,
                                htmlSnippet = html.take(500),
                            ),
                        )
                        return ParsingResult.Failure(errors, emptyList())
                    }

                    errors.add(
                        ParsingError(
                            field = "rows",
                            reason = "No topic rows found with any selector. Tried: $ROW_SELECTORS",
                            severity = ErrorSeverity.CRITICAL,
                            htmlSnippet = html.take(500),
                        ),
                    )
                    return ParsingResult.Failure(errors, emptyList())
                }

                for ((index, row) in rows.withIndex()) {
                    try {
                        // Log row structure for debugging
                        if (index < 3) {
                            logger.d {
                                "Parsing row $index: tag=${row.tagName()}, " +
                                    "classes='${row.className()}', " +
                                    "hasTitle=${row.selectFirst(TITLE_SELECTOR) != null}, " +
                                    "textLength=${row.text().length}"
                            }
                        }

                        val result = parseSearchResultRow(row)
                        if (result != null) {
                            results.add(result)
                            if (index < 3) {
                                logger.d { "✅ Row $index parsed: topicId=${result.topicId}, title='${result.title.take(40)}'" }
                            }
                        } else {
                            // Only warn if we failed to parse a row that we thought was valid
                            // But skip clogging logs if it's just a spacer/ad row that slipped through
                            if (row.text().length > 50) {
                                val rowTag = row.tagName()
                                val rowClasses = row.className()

                                val hasTitle: Boolean = row.selectFirst(TITLE_SELECTOR) != null
                                val topicIdAttr = row.attr(TOPIC_ID_ATTR)
                                val rowId = row.attr("id")
                                val topicId = topicIdAttr.ifEmpty { rowId.removePrefix("tr-") }

                                // Try to extract topicId from title link as fallback
                                val topicIdFromLink =
                                    row
                                        .selectFirst(TITLE_SELECTOR)
                                        ?.absUrl("href")
                                        ?.substringAfter("t=")
                                        ?.substringBefore("&")
                                        ?: ""

                                val finalTopicId = topicId.ifEmpty { topicIdFromLink }

                                // Get title element for detailed logging
                                val titleElement = row.selectFirst(TITLE_SELECTOR)
                                val titleText = titleElement?.text()?.take(50) ?: ""
                                val titleHtml = titleElement?.html()?.take(100) ?: ""

                                logger.w {
                                    "⚠️ Row $index failed to parse: tag=$rowTag, " +
                                        "classes='$rowClasses', hasTitle=$hasTitle, " +
                                        "topicId='$finalTopicId', titleText='$titleText', " +
                                        "titleHtml='$titleHtml'"
                                }

                                errors.add(
                                    ParsingError(
                                        field = "row_$index",
                                        reason = "Failed to extract required fields (tag=$rowTag, hasTitle=$hasTitle)",
                                        severity = ErrorSeverity.WARNING,
                                        htmlSnippet = row.html().take(200),
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        val errorDetails =
                            when {
                                e.message != null -> "${e.javaClass.simpleName}: ${e.message}"
                                else -> e.javaClass.simpleName
                            }
                        logger.e(e) { "❌ Error parsing row $index: $errorDetails" }
                        errors.add(
                            ParsingError(
                                field = "row_$index",
                                reason = errorDetails,
                                severity = ErrorSeverity.ERROR,
                                htmlSnippet = row.html().take(300),
                            ),
                        )
                    }
                }

                // Determine result type based on success/error ratio
                return when {
                    errors.isEmpty() -> ParsingResult.Success(results)
                    results.isNotEmpty() -> ParsingResult.PartialSuccess(results, errors)
                    else -> ParsingResult.Failure(errors, emptyList())
                }
            } catch (e: Exception) {
                val errorDetails =
                    when {
                        e.message != null -> "${e.javaClass.simpleName}: ${e.message}"
                        else -> e.javaClass.simpleName
                    }
                Log.e(
                    TAG,
                    "❌ Failed to parse search results: $errorDetails (HTML size: ${rawBytes.size} bytes)",
                    e,
                )
                errors.add(
                    ParsingError(
                        field = "document",
                        reason = "Failed to parse HTML: $errorDetails",
                        severity = ErrorSeverity.CRITICAL,
                        htmlSnippet = String(rawBytes, Charsets.UTF_8).take(500),
                    ),
                )
                return ParsingResult.Failure(errors, emptyList())
            }
        }

        /**
         * Parse forum page with topics.
         *
         * @param body ResponseBody from forum page
         * @param forumId Forum ID for context
         * @return List of search results (topics from forum)
         */

        /**
         * Parse forum page and return topics along with pagination info.
         */
        public data class ForumPageResult(
            val topics: List<SearchResult>,
            val hasMorePages: Boolean,
        )

        public fun parseForumPage(
            body: okhttp3.ResponseBody,
            forumId: String,
        ): List<SearchResult> {
            val result = parseForumPageWithPagination(body, forumId)
            return result.topics
        }

        /**
         * Parse forum page with encoding detection.
         * Uses forum-specific validation that is less strict than search results.
         *
         * @param bytes Raw response bytes
         * @param contentType Optional Content-Type header
         * @return ParsingResult with search results
         */
        public fun parseForumPageWithEncoding(
            bytes: ByteArray,
            contentType: String? = null,
        ): ParsingResult<List<SearchResult>> {
            // Decode with simple decoder (matching Flutter implementation)
            val decodedHtml = decoder.decode(bytes, contentType)

            // Parse the decoded HTML with forum-specific validation
            return parseForumPageDefensive(decodedHtml, bytes)
        }

        /**
         * Parse forum page with graceful error handling and forum-specific validation.
         *
         * @param html Decoded HTML content
         * @param rawBytes Raw bytes for error reporting
         * @return ParsingResult with results, warnings, or errors
         */
        private fun parseForumPageDefensive(
            html: String,
            rawBytes: ByteArray,
        ): ParsingResult<List<SearchResult>> {
            val errors = mutableListOf<ParsingError>()
            val results = mutableListOf<SearchResult>()

            try {
                // Validate HTML content before parsing - use forum-specific validation
                val validationError = ParsingValidators.validateForumPage(html)
                if (validationError != null) {
                    errors.add(
                        ParsingError(
                            field = "document",
                            reason = "Content validation failed: ${validationError.message}",
                            severity = ErrorSeverity.CRITICAL,
                            htmlSnippet = html.take(500),
                        ),
                    )
                    return ParsingResult.Failure(errors, emptyList())
                }

                // Parse with baseUri for proper absolute URL resolution (using current mirror)
                val document = Jsoup.parse(html, getBaseUrl())

                // Check if it's a valid search/index page
                val isSearchPage = document.select("form#quick-search, input[name=nm]").isNotEmpty()
                val isIndexPage = document.select("#forums_list_wrap").isNotEmpty()

                // Try to find rows using multiple selectors strategy
                var rows = org.jsoup.select.Elements()
                var successfulSelector: String = ""
                for (selector in ROW_SELECTORS) {
                    val found = document.select(selector)
                    logger.d { "Trying selector '$selector': found ${found.size} elements" }

                    if (found.isNotEmpty()) {
                        // Check if we found tr elements or something else
                        val firstElement = found.firstOrNull()
                        if (firstElement == null) {
                            logger.w { "  ⚠️ Found elements but first() returned null" }
                            continue
                        }
                        val elementTag = firstElement.tagName()
                        logger.d { "  First element tag: $elementTag, classes: '${firstElement.className()}'" }

                        // If we found td instead of tr, we need to find parent tr
                        val actualRows =
                            if (elementTag == "td") {
                                logger.w { "  ⚠️ Selector found <td> instead of <tr>, looking for parent <tr>" }
                                found
                                    .mapNotNull { td ->
                                        td.parent()?.takeIf { parent -> parent.tagName() == "tr" }
                                    }.distinct()
                            } else {
                                found
                            }

                        logger.d { "  After processing: ${actualRows.size} rows" }

                        // Filter out header/ad rows if generic selector used
                        val validRows =
                            actualRows.filter { row ->
                                // Basic validation: must have some content/structure
                                // Use selectFirst() for better performance (returns null if not found)

                                val hasTitle: Boolean = row.selectFirst(TITLE_SELECTOR) != null
                                val isHeader = row.hasClass("vf-col-header-row")
                                val isValid = !isHeader && hasTitle

                                if (!isValid && row.text().length > 50) {
                                    logger.d {
                                        "  Row filtered out: isHeader=$isHeader, hasTitle=$hasTitle, " +
                                            "tag=${row.tagName()}, classes='${row.className()}'"
                                    }
                                }

                                isValid
                            }

                        if (validRows.isNotEmpty()) {
                            rows = org.jsoup.select.Elements(validRows)
                            successfulSelector = selector
                            logger.d { "✅ Found ${rows.size} valid rows using selector: $selector" }
                            break
                        } else {
                            logger.w { "  ⚠️ Selector '$selector' found ${actualRows.size} rows but none are valid" }
                        }

                        // If selected rows were all invalid, try next selector
                    }
                }

                if (rows.isEmpty()) {
                    // Enhanced Debug Logging
                    val title = document.title()
                    val bodySnippet = document.body().text().take(500)
                    logger.w { "NO ROWS FOUND. Details:" }
                    logger.w { "Title: '$title'" }
                    logger.w { "IsSearchPage: $isSearchPage, IsIndexPage: $isIndexPage" }
                    logger.w { "Body Text (first 500): $bodySnippet" }
                    logger.w { "HTML (first 2000): ${document.outerHtml().take(2000)}" }

                    if (isSearchPage || isIndexPage) {
                        logger.i { "No rows found, but page looks like valid search/index page (empty results)" }
                        // For valid search pages with no results, validate now to catch real errors
                        // but still return empty list if it's just empty results
                        val validationError = ParsingValidators.validateForumPage(html)
                        if (validationError != null && validationError !is RuTrackerError.NoData) {
                            // Only log warning if it's a real error (not just empty data)
                            logger.w { "Validation error detected on empty forum page: ${validationError.message}" }
                            // Still return empty list to avoid blocking valid empty results
                        }
                        return ParsingResult.Success(emptyList())
                    }

                    // Not a search/index page and no rows - validate to check for errors
                    val validationError = ParsingValidators.validateForumPage(html)
                    if (validationError != null) {
                        errors.add(
                            ParsingError(
                                field = "document",
                                reason = "Content validation failed: ${validationError.message}",
                                severity = ErrorSeverity.CRITICAL,
                                htmlSnippet = html.take(500),
                            ),
                        )
                        return ParsingResult.Failure(errors, emptyList())
                    }

                    errors.add(
                        ParsingError(
                            field = "rows",
                            reason = "No topic rows found with any selector. Tried: $ROW_SELECTORS",
                            severity = ErrorSeverity.CRITICAL,
                            htmlSnippet = html.take(500),
                        ),
                    )
                    return ParsingResult.Failure(errors, emptyList())
                }

                for ((index, row) in rows.withIndex()) {
                    try {
                        val result = parseSearchResultRow(row)
                        if (result != null) {
                            results.add(result)
                        } else {
                            // Only warn if we failed to parse a row that we thought was valid
                            // But skip clogging logs if it's just a spacer/ad row that slipped through
                            if (row.text().length > 50) {
                                val rowTag = row.tagName()
                                val rowClasses = row.className()

                                val hasTitle: Boolean = row.selectFirst(TITLE_SELECTOR) != null
                                val topicId = row.attr(TOPIC_ID_ATTR).ifEmpty { row.attr("id") }

                                Log.w(
                                    TAG,
                                    "⚠️ Row $index failed to parse: tag=$rowTag, " +
                                        "classes='$rowClasses', hasTitle=$hasTitle, topicId='$topicId'",
                                )

                                errors.add(
                                    ParsingError(
                                        field = "row_$index",
                                        reason = "Failed to extract required fields (tag=$rowTag, hasTitle=$hasTitle)",
                                        severity = ErrorSeverity.WARNING,
                                        htmlSnippet = row.html().take(200),
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        val errorDetails =
                            when {
                                e.message != null -> "${e.javaClass.simpleName}: ${e.message}"
                                else -> e.javaClass.simpleName
                            }
                        logger.e(e) { "❌ Error parsing row $index: $errorDetails" }
                        errors.add(
                            ParsingError(
                                field = "row_$index",
                                reason = errorDetails,
                                severity = ErrorSeverity.ERROR,
                                htmlSnippet = row.html().take(300),
                            ),
                        )
                    }
                }

                // Determine result type based on success/error ratio
                return when {
                    errors.isEmpty() -> ParsingResult.Success(results)
                    results.isNotEmpty() -> ParsingResult.PartialSuccess(results, errors)
                    else -> ParsingResult.Failure(errors, emptyList())
                }
            } catch (e: Exception) {
                val errorDetails =
                    when {
                        e.message != null -> "${e.javaClass.simpleName}: ${e.message}"
                        else -> e.javaClass.simpleName
                    }
                logger.e(e) {
                    "❌ Failed to parse forum page: $errorDetails (HTML size: ${rawBytes.size} bytes)"
                }
                errors.add(
                    ParsingError(
                        field = "document",
                        reason = "Failed to parse HTML: $errorDetails",
                        severity = ErrorSeverity.CRITICAL,
                        htmlSnippet = String(rawBytes, Charsets.UTF_8).take(500),
                    ),
                )
                return ParsingResult.Failure(errors, emptyList())
            }
        }

        /**
         * Parse forum page with pagination detection.
         * Checks for "След." (Next) link or pagination info to determine if more pages exist.
         */
        public fun parseForumPageWithPagination(
            body: okhttp3.ResponseBody,
            forumId: String,
        ): ForumPageResult {
            // Convert ResponseBody to ByteArray for encoding-aware parsing
            val rawBytes = body.bytes()
            val contentType = body.contentType()?.toString()
            logger.d { "Parsing forum $forumId page: ${rawBytes.size} bytes, content-type: $contentType" }
            val result = parseForumPageWithEncoding(rawBytes, contentType)

            val topics =
                when (result) {
                    is ParsingResult.Success -> {
                        logger.d { "Forum $forumId: successfully parsed ${result.data.size} topics" }
                        result.data
                    }
                    is ParsingResult.PartialSuccess -> {
                        // Log only critical errors, not warnings
                        val criticalErrors = result.errors.filter { it.severity == ErrorSeverity.CRITICAL }
                        if (criticalErrors.isNotEmpty()) {
                            logger.w {
                                "Forum $forumId: partial parse - ${result.data.size} topics, " +
                                    "${criticalErrors.size} critical errors (${result.errors.size} total)"
                            }
                            criticalErrors.take(5).forEach { error ->
                                logger.w { "  - ${error.field}: ${error.reason}" }
                            }
                        } else {
                            logger.d {
                                "Forum $forumId: partial parse - ${result.data.size} topics, " +
                                    "${result.errors.size} non-critical warnings"
                            }
                        }
                        result.data
                    }
                    is ParsingResult.Failure -> {
                        logger.e { "❌ Forum $forumId: parsing failed - ${result.errors.size} errors (${rawBytes.size} bytes)" }
                        result.errors.take(10).forEach { error ->
                            // Limit to first 10 errors to avoid log spam
                            logger.e {
                                "  - ${error.field}: ${error.reason}${if (error.htmlSnippet != null) {
                                    " [HTML: ${error.htmlSnippet.take(
                                        100,
                                    )}...]"
                                } else {
                                    ""
                                }}"
                            }
                        }
                        if (result.errors.size > 10) {
                            logger.e { "  ... and ${result.errors.size - 10} more errors" }
                        }
                        emptyList()
                    }
                }

            // Extract forum name from HTML for category assignment
            val forumName =
                try {
                    val decodedHtml = decoder.decode(rawBytes, contentType)
                    extractForumNameFromHTML(decodedHtml)
                } catch (e: Exception) {
                    logger.w(e) { "Failed to extract forum name for forum $forumId" }
                    "Аудиокниги" // Fallback
                }

            logger.d { "Forum $forumId: extracted forum name = '$forumName'" }

            // Assign category to all topics if not already set
            val topicsWithCategory =
                topics.map { topic ->
                    if (topic.category.isBlank()) {
                        topic.copy(category = forumName)
                    } else {
                        topic
                    }
                }

            // Check pagination to determine if there are more pages
            // Use the same decoder as for parsing topics to ensure correct encoding (Windows-1251)
            val hasMorePages =
                try {
                    // Decode HTML with proper encoding (same as parseSearchResultsWithEncoding)
                    val decodedHtml = decoder.decode(rawBytes, contentType)
                    val document = org.jsoup.Jsoup.parse(decodedHtml, getBaseUrl())

                    // Method 1: Check for "След." (Next) link with class "pg"
                    val nextLink =
                        document.select("a.pg").firstOrNull { link ->
                            val text = link.toStr().trim()
                            text.contains("След", ignoreCase = true) || text.contains("Next", ignoreCase = true)
                        }

                    if (nextLink != null) {
                        logger.d { "Forum $forumId: found 'След.' link, has more pages" }
                        true
                    } else {
                        // Method 2: Check pagination text "Страница X из Y"
                        val paginationText = document.select("#pagination, .nav").toStr()
                        val pageMatch = Regex("Страница\\s+\\d+\\s+из\\s+(\\d+)", RegexOption.IGNORE_CASE).find(paginationText)
                        if (pageMatch != null) {
                            val currentPage =
                                Regex("Страница\\s+(\\d+)", RegexOption.IGNORE_CASE)
                                    .find(paginationText)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.toIntOrNull()
                                    ?: 1
                            val totalPages = pageMatch.groupValues[1].toIntOrNull() ?: 1
                            val hasMore = currentPage < totalPages
                            logger.d { "Forum $forumId: pagination shows page $currentPage of $totalPages, hasMore=$hasMore" }
                            hasMore
                        } else {
                            // Method 3: If we got topics and count matches TOPICS_PER_PAGE, likely more pages
                            val likelyHasMore = topics.size >= 50 // TOPICS_PER_PAGE
                            logger.d { "Forum $forumId: no pagination found, assuming hasMore=$likelyHasMore (topics: ${topics.size})" }
                            likelyHasMore
                        }
                    }
                } catch (e: Exception) {
                    logger.w(e) { "Failed to check pagination for forum $forumId" }
                    // Fallback: if we got topics and count matches TOPICS_PER_PAGE, likely more pages
                    topics.size >= 50
                }

            return ForumPageResult(topics = topicsWithCategory, hasMorePages = hasMorePages)
        }

        /**
         * Parse search results from HTML.
         *
         * @param html HTML content from search results page
         * @return List of search results
         */
        public fun parseSearchResults(html: String): List<SearchResult> {
            logger.d { "=== PARSING SEARCH RESULTS ===" }
            // internal implementation delegates to parseSearchResultsDefensive logic equivalent
            // For backward compatibility / simple calls

            try {
                // Parse with baseUri for proper absolute URL resolution (using current mirror)
                val document = Jsoup.parse(html, getBaseUrl())

                var rows = org.jsoup.select.Elements()
                for (selector in ROW_SELECTORS) {
                    val found = document.select(selector)
                    if (found.isNotEmpty()) {
                        // Use selectFirst() for better performance (returns null if not found)
                        val validRows = found.filter { it.selectFirst(TITLE_SELECTOR) != null }
                        if (validRows.isNotEmpty()) {
                            rows = org.jsoup.select.Elements(validRows)
                            logger.d { "Using selector '$selector': ${rows.size} rows" }
                            break
                        }
                    }
                }

                if (rows.isEmpty()) {
                    logger.w { "⚠️ NO ROWS FOUND with any selector! Running diagnostics..." }

                    // === DIAGNOSTIC LOGGING ===

                    // 1. Detect page type
                    val isLoginPage =
                        document.select("form[action*='login.php']").isNotEmpty() ||
                            document.select("input[name='login_username']").isNotEmpty() ||
                            document.select("input[name='login']").isNotEmpty()
                    val isCaptchaPage =
                        document.select("img[alt*='captcha']").isNotEmpty() ||
                            document.select("img[src*='captcha']").isNotEmpty() ||
                            document.select("form[action*='captcha']").isNotEmpty()
                    val isErrorPage =
                        document
                            .select(".message, .error, .warning")
                            .text()
                            .contains("ошибка", ignoreCase = true)

                    if (isLoginPage) logger.w { "❌ LOGIN PAGE DETECTED!" }
                    if (isCaptchaPage) logger.w { "❌ CAPTCHA PAGE DETECTED!" }
                    if (isErrorPage) logger.w { "❌ ERROR PAGE DETECTED!" }

                    // 2. Log HTML structure
                    val tables = document.select("table")
                    logger.w { "📊 Found ${tables.size} table(s)" }
                    tables.take(5).forEachIndexed { i, table ->
                        logger.w { "  Table $i: class='${table.className()}' id='${table.id()}'" }
                    }

                    val allRows = document.select("tr")
                    logger.w { "📋 Total tr elements: ${allRows.size}" }

                    // Check each selector individually
                    ROW_SELECTORS.forEach { selector ->
                        val found = document.select(selector)
                        logger.w { "  Selector '$selector': ${found.size} matches" }
                        if (found.isNotEmpty()) {
                            found.take(3).forEachIndexed { i, el ->
                                val hasTitle = el.select(TITLE_SELECTOR).isNotEmpty()
                                logger.w { "    Element $i: hasTitle=$hasTitle, class='${el.className()}'" }
                            }
                        }
                    }

                    // 3. Page metadata
                    val pageTitle = document.selectFirst("title")?.toStr() ?: "No title"
                    logger.w { "📝 Page Title: $pageTitle" }

                    // 4. HTML preview
                    val htmlPreview = html.take(500).replace(Regex("\\s+"), " ")
                    logger.w { "📄 HTML Preview: $htmlPreview..." }

                    // 5. Check for common page elements
                    val hasMainContent = document.select("#main_content, #page_content").isNotEmpty()
                    val hasForumTable = document.select(".forumline, .vf-table").isNotEmpty()
                    logger.w { "🔍 Page elements: mainContent=$hasMainContent, forumTable=$hasForumTable" }

                    return emptyList()
                }

                val results = mutableListOf<SearchResult>()

                rows.forEachIndexed { idx, row ->
                    try {
                        val result = parseSearchResultRow(row)
                        if (result != null) {
                            results.add(result)
                            // Log first 3 successful results
                            if (idx < 3) {
                                logger.d { "✓ Result $idx: ${result.title} by ${result.author}" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.w(e) { "✗ Failed to parse row $idx" }
                    }
                }

                logger.d { "✅ Successfully parsed ${results.size}/${rows.size} results" }
                return results
            } catch (e: Exception) {
                logger.e(e) { "❌ Failed to parse search results" }
                return emptyList()
            }
        }

        private fun parseSearchResultRow(row: Element): SearchResult? {
            // Extract topic ID from data attribute (preferred) or from link href
            val topicId =
                row.attr(TOPIC_ID_ATTR).ifEmpty {
                    // Fallback: extract from row id attribute
                    row.attr("id").removePrefix("tr-").ifEmpty {
                        // Last resort: extract from title link href
                        // Use absUrl() for proper absolute URL resolution
                        row
                            .selectFirst(TITLE_SELECTOR)
                            ?.absUrl("href")
                            ?.substringAfter("t=")
                            ?.substringBefore("&")
                            ?: run {
                                logger.d { "⚠️ No topicId found in row" }
                                return null
                            }
                    }
                }

            if (topicId.isEmpty()) {
                // Common for header rows or ads, detailed logging usually not needed unless debugging structure
                logger.d { "⚠️ Empty topicId in row" }
                return null
            }

            // Extract title - use updated selector
            val titleElement = row.selectFirst(TITLE_SELECTOR)
            if (titleElement == null) {
                Log.w(
                    TAG,
                    "⚠️ No title element found for topic $topicId. " +
                        "Row HTML: ${row.html().take(200)}",
                )
                return null
            }
            val title = titleElement.toStr()
            if (title.isEmpty()) {
                // Try to get title from href or other attributes
                val titleFromHref = titleElement.attr("title").takeIf { it.isNotBlank() }
                val titleFromText = titleElement.text().takeIf { it.isNotBlank() }
                val titleFromOwnText = titleElement.ownText().takeIf { it.isNotBlank() }

                val finalTitle = titleFromHref ?: titleFromText ?: titleFromOwnText
                if (finalTitle == null) {
                    Log.w(
                        TAG,
                        "⚠️ Empty title for topic $topicId: " +
                            "href='${titleElement.attr("href")}', " +
                            "html='${titleElement.html().take(100)}', " +
                            "outerHtml='${titleElement.outerHtml().take(150)}'",
                    )
                    return null
                }
                // Use the found title
                val cleanedTitle = cleanTitle(finalTitle)
                // Continue with parsing using the found title
                return createSearchResult(
                    topicId = topicId,
                    title = cleanedTitle,
                    row = row,
                )
            }

            // Clean the title to remove technical details
            val cleanedTitle = cleanTitle(title)

            return createSearchResult(
                topicId = topicId,
                title = cleanedTitle,
                row = row,
            )
        }

        /**
         * Create SearchResult from extracted fields.
         * Extracted to avoid code duplication.
         */
        private fun createSearchResult(
            topicId: String,
            title: String,
            row: Element,
        ): SearchResult {
            // Extract author
            // Priority 1: Extract from title (e.g. "Author - Book Name")
            // Priority 2: Use uploader/author column
            val titleAuthor = extractAuthorFromTitle(title)

            val authorElement = row.selectFirst(AUTHOR_SELECTOR)
            val uploaderName = authorElement?.toStr()?.trim()?.ifEmpty { null }

            val author = titleAuthor ?: uploaderName ?: "Unknown"

            // Extract category (from data attribute or default)
            val category = row.attr("data-forum_id").ifEmpty { "Audiobooks" }

            // Use DefensiveFieldExtractor for robust extraction
            val size = fieldExtractor.extractSize(row, topicId)
            val seeders = fieldExtractor.extractSeeders(row, topicId)
            val leechers = fieldExtractor.extractLeechers(row, topicId)

            // Extract magnet link
            // Use absUrl() for proper absolute URL resolution (magnet: links are already absolute)
            val magnetElement = row.selectFirst(MAGNET_LINK_SELECTOR)
            val magnetUrl = magnetElement?.absUrl("href") ?: magnetElement?.attr("href")

            // Extract torrent download URL (using DOWNLOAD_HREF_SELECTOR)
            // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
            val torrentElement = row.selectFirst(DOWNLOAD_HREF_SELECTOR)
            val torrentUrl =
                torrentElement?.absUrl("href") ?: run {
                    // Fallback: construct torrent URL from topicId if download link not found in row
                    // This is common on forum pages where download link is on topic details page
                    if (topicId.isNotEmpty()) {
                        "${getBaseUrl()}dl.php?t=$topicId"
                    } else {
                        ""
                    }
                }

            // Extract cover URL using CoverUrlExtractor for consistent extraction
            // This uses the same logic as topic details page
            // Note: selectFirst() already searches recursively, but we add explicit search
            // in all cells to ensure we find var.postImg even if it's deeply nested
            val coverUrl =
                run {
                    // First try: extract from entire row (selectFirst searches recursively)
                    val fromRow = coverExtractor.extract(row)
                    if (fromRow != null) {
                        logger.d { "Cover found in row for topic $topicId: $fromRow" }
                        fromRow
                    } else {
                        // Second try: explicitly search in all table cells
                        // Sometimes var.postImg is in a specific cell
                        val fromCells =
                            row.select("td").firstNotNullOfOrNull { cell ->
                                coverExtractor.extract(cell)
                            }
                        if (fromCells != null) {
                            logger.d { "Cover found in table cell for topic $topicId: $fromCells" }
                            fromCells
                        } else {
                            // Third try: search in all nested elements (div, span, p)
                            // var.postImg might be in a nested container
                            val fromNested =
                                row.select("div, span, p").firstNotNullOfOrNull { element ->
                                    coverExtractor.extract(element)
                                }
                            if (fromNested != null) {
                                logger.d { "Cover found in nested element for topic $topicId: $fromNested" }
                                fromNested
                            } else {
                                // Debug: check if var.postImg exists at all in the row
                                val hasPostImg = row.select("var.postImg, var.postImgAligned, var[class*='postImg']").isNotEmpty()
                                if (hasPostImg) {
                                    logger.w {
                                        "var.postImg found in row for topic $topicId but extract() returned null - checking attributes..."
                                    }
                                    // Try to extract directly from var.postImg if it exists
                                    row
                                        .select(
                                            "var.postImg[title], var.postImgAligned[title], var[class*='postImg'][title]",
                                        ).firstOrNull()
                                        ?.let { varElement ->
                                            val url = varElement.attr("title")
                                            if (url.isNotBlank()) {
                                                val normalized = coverExtractor.normalizeUrl(url)
                                                logger.d { "Cover extracted directly from var.postImg for topic $topicId: $normalized" }
                                                normalized
                                            } else {
                                                logger.w { "var.postImg found but title attribute is blank for topic $topicId" }
                                                null
                                            }
                                        }
                                } else {
                                    logger.d { "No var.postImg found in row for topic $topicId" }
                                    null
                                }
                            }
                        }
                    }
                }

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
                coverUrl = coverUrl,
                uploader = uploaderName,
            )
        }

        /**
         * Extract author from title string.
         * Assumes format "Author - Title" or "Author / Title".
         */
        private fun extractAuthorFromTitle(title: String): String? {
            // Split by common separators
            val separators = listOf(" - ", " / ", " – ", " — ") // including ndash, mdash

            for (separator in separators) {
                if (title.contains(separator)) {
                    val parts = title.split(separator, limit = 2)
                    if (parts.isNotEmpty()) {
                        val potentialAuthor = parts[0].trim()
                        // Basic validation: Author name shouldn't be too long or contain weird chars
                        // Allow letters, dots, spaces, hyphens
                        if (potentialAuthor.length in 2..60 &&
                            !potentialAuthor.contains(Regex("[0-9]{3,}"))
                        ) { // simple heuristic: no long numbers
                            return potentialAuthor
                        }
                    }
                }
            }
            return null
        }

        /**
         * Parse topic details from HTML.
         *
         * @param html HTML content from topic details page
         * @return Topic details or null if parsing fails
         */
        public fun parseTopicDetails(
            html: String,
            topicId: String,
        ): TopicDetails? {
            try {
                // Validate HTML content before parsing
                val validationError = ParsingValidators.validateTopicDetails(html)
                if (validationError != null) {
                    logger.w { "Topic $topicId validation failed: ${validationError.message}" }
                    return null
                }

                // Parse with baseUri for proper absolute URL resolution (using current mirror)
                val document = Jsoup.parse(html, getBaseUrl())

                // Extract title
                val titleElement = document.selectFirst(MAIN_TITLE_SELECTOR) ?: return null
                val title = titleElement.toStr()

                // Try to extract author from detailed title as well
                val titleAuthor = extractAuthorFromTitle(cleanTitle(title))

                // Extract post body for metadata
                val postBody = document.selectFirst(POST_BODY_SELECTOR)

                // Extract size
                val sizeElement = document.selectFirst(TOR_SIZE_SELECTOR)
                val size = sizeElement?.toStr() ?: "Unknown"

                // Extract magnet link
                // Use absUrl() for proper absolute URL resolution (magnet: links are already absolute)
                val magnetElement = document.selectFirst(MAGNET_LINK_SELECTOR)
                val magnetUrl = magnetElement?.absUrl("href") ?: magnetElement?.attr("href")

                // Extract torrent URL
                // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
                val downloadElement = document.selectFirst(DOWNLOAD_HREF_SELECTOR)
                val torrentUrl = downloadElement?.absUrl("href") ?: ""

                // Extract seeders and leechers from document (not just post body)
                val seeders = extractSeeders(document)
                val leechers = extractLeechers(document)

                // Extract additional stats (Registered date, Downloads count)
                val (registeredDate, downloadsCount) = extractTopicStats(document)

                // Extract metadata from post body
                val metadata = extractMetadata(postBody)

                // Clean the title
                val cleanedTitle = cleanTitle(title)

                val descriptionHtml =
                    postBody?.html()?.let { html ->
                        // Clean HTML: using DOM manipulation
                        val cleaned = cleanDescriptionHtml(html, metadata)
                        // Ensure all links have absolute URLs (now handled in cleanDescriptionHtml)
                        cleaned
                    }

                // Extract description - clean text from cleaned HTML
                val rawDescriptionText = postBody?.text() ?: "" // fallback
                val descriptionText = cleanDescription(rawDescriptionText, metadata, descriptionHtml)
                val parsedMediaInfo = mediaInfoParser.parse(rawDescriptionText)

                // Extract series/cycle
                val series = metadata["series"] ?: extractSeries(postBody)

                // Extract comments (skip first post_body which is the main post)
                val comments = extractComments(document, topicId)

                val (currentPage, totalPages) = extractTopicPagination(document)

                return TopicDetails(
                    topicId = topicId,
                    title = cleanedTitle,
                    author = metadata["author"],
                    performer = metadata["performer"],
                    category = "Audiobooks",
                    size = size,
                    seeders = seeders,
                    leechers = leechers,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    coverUrl = postBody?.let { coverExtractor.extract(it) },
                    genres = extractGenres(postBody),
                    addedDate = metadata["addedDate"],
                    duration = metadata["duration"],
                    bitrate = metadata["bitrate"],
                    audioCodec = metadata["codec"],
                    description = descriptionText,
                    descriptionHtml = descriptionHtml,
                    mediaInfo = parsedMediaInfo,
                    relatedBooks = extractRelatedBooks(postBody),
                    series = series,
                    comments = comments,
                    registeredDate = registeredDate,
                    downloadsCount = downloadsCount,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    allMetadata = metadata,
                )
            } catch (e: Exception) {
                logger.e(e) { "Failed to parse topic details" }
                return null
            }
        }

        private fun extractTopicStats(doc: org.jsoup.nodes.Document): Pair<String?, String?> {
            // Find the cell with "Зарегистрирован:" label
            // Usually in tr.row1 > td
            val labelTd = doc.select("td").firstOrNull { it.text().contains("Зарегистрирован") }
            if (labelTd == null) return null to null

            val valueTd = labelTd.nextElementSibling() ?: return null to null
            val lis = valueTd.select("ul li")

            // First LI is usually the date: "21-Май-19 15:42"
            val date = lis.getOrNull(0)?.text()?.trim()

            // Second LI is the count: "Скачан: 11,783 раза"
            val countRaw = lis.getOrNull(1)?.text()?.trim() ?: ""
            val count =
                if (countRaw.contains("Скачан", ignoreCase = true)) {
                    countRaw.substringAfter(":").replace("раза", "").trim()
                } else {
                    null
                }

            return date to count
        }

        private fun extractTopicPagination(doc: org.jsoup.nodes.Document): Pair<Int, Int> {
            val paginationText = doc.select("#pagination, .nav").text()
            if (paginationText.isBlank()) return 1 to 1

            // Regex for "Страница X из Y" (Page X of Y)
            val regex = Regex("Страница\\s+(\\d+)\\s+из\\s+(\\d+)", RegexOption.IGNORE_CASE)
            val match = regex.find(paginationText)

            return if (match != null) {
                val current = match.groupValues[1].toIntOrNull() ?: 1
                val total = match.groupValues[2].toIntOrNull() ?: 1
                current to total
            } else {
                1 to 1
            }
        }

        /**
         * Extract seeders count from document.
         * Looks for: <span class="seed">Сиды:&nbsp; <b>71</b></span>
         */
        private fun extractSeeders(document: org.jsoup.nodes.Document): Int {
            // Try multiple selectors
            val selectors =
                listOf(
                    "span.seed b",
                    "span.seedmed b",
                    "b.seedmed",
                    ".seed b",
                    "span.seed",
                )

            for (selector in selectors) {
                val element = document.selectFirst(selector)
                val text = element?.toStr()?.trim() ?: ""
                val number = text.toIntOrNull()
                if (number != null && number >= 0) {
                    return number
                }
            }

            // Fallback: try to extract from text
            val seedText = document.select("span.seed, .seed").toStr()
            val regex = "Сиды?:\\s*<b>?(\\d+)</b>?".toRegex(RegexOption.IGNORE_CASE)
            regex
                .find(seedText)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.let { return it }

            return 0
        }

        /**
         * Extract leechers count from document.
         * Looks for: <span class="leech">Личи:&nbsp; <b>2</b></span>
         */
        private fun extractLeechers(document: org.jsoup.nodes.Document): Int {
            // Try multiple selectors
            val selectors =
                listOf(
                    "span.leech b",
                    "span.leechmed b",
                    "b.leechmed",
                    ".leech b",
                    "span.leech",
                )

            for (selector in selectors) {
                val element = document.selectFirst(selector)
                val text = element?.toStr()?.trim() ?: ""
                val number = text.toIntOrNull()
                if (number != null && number >= 0) {
                    return number
                }
            }

            // Fallback: try to extract from text
            val leechText = document.selectFirst("span.leech, .leech")?.toStr() ?: ""
            val regex = "Личи?:\\s*<b>?(\\d+)</b>?".toRegex(RegexOption.IGNORE_CASE)
            regex
                .find(leechText)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.let { return it }

            return 0
        }

        private fun extractMetadata(postBody: Element?): Map<String, String> {
            val metadata = mutableMapOf<String, String>()
            if (postBody == null) return metadata

            // Parse using HTML structure: <span class="post-b">Label</span>: Value
            postBody.select("span.post-b").forEach { span ->
                val label = span.text().trim().removeSuffix(":")
                // Get the text node immediately following the span
                val value =
                    span
                        .nextSibling()
                        ?.toString()
                        ?.trim()
                        ?.removePrefix(":")
                        ?.trim()
                        ?: ""

                if (value.isNotEmpty() && label.isNotEmpty()) {
                    // Keep original label and value
                    metadata[label] = value

                    // Map to standard internal keys for UI components
                    when {
                        // Author
                        label.contains("Автор", ignoreCase = true) || label.contains("Author", ignoreCase = true) -> {
                            val current = metadata["author"] ?: ""
                            metadata["author"] = if (current.isEmpty()) value else "$current $value"
                        }
                        // Performer
                        label.contains("Исполнитель", ignoreCase = true) || label.contains("Narrator", ignoreCase = true) -> {
                            metadata["performer"] = value
                        }
                        // Duration
                        label.contains("Время звучания", ignoreCase = true) || label.contains("Duration", ignoreCase = true) -> {
                            metadata["duration"] = value
                        }
                        // Audio Codec
                        label.contains("Аудиокодек", ignoreCase = true) ||
                            label.contains("Аудио кодек", ignoreCase = true) ||
                            label.contains("Codec", ignoreCase = true) -> {
                            metadata["codec"] = value
                        }
                        // Bitrate
                        label.contains("Битрейт", ignoreCase = true) || label.contains("Bitrate", ignoreCase = true) -> {
                            metadata["bitrate"] = value
                        }
                        // Year/Date
                        label.contains("Год выпуска", ignoreCase = true) || label.contains("Year", ignoreCase = true) -> {
                            metadata["addedDate"] = value
                        }
                        // Series/Cycle
                        label.contains("Цикл", ignoreCase = true) || label.contains("Серия", ignoreCase = true) -> {
                            metadata["series"] = value
                        }
                        // Genre
                        label.contains("Жанр", ignoreCase = true) || label.contains("Genre", ignoreCase = true) -> {
                            metadata["genre"] = value
                        }
                        // Publisher
                        label.contains("Издательство", ignoreCase = true) || label.contains("Publisher", ignoreCase = true) -> {
                            metadata["publisher"] = value
                        }
                        // Correction (Корректор)
                        label.contains("Корректор", ignoreCase = true) || label.contains("Correction", ignoreCase = true) -> {
                            metadata["correction"] = value
                        }
                        // Poster Author (Авторский постер)
                        // Handle tricky cases like "Авторский постер: :"
                        label.contains("Авторский постер", ignoreCase = true) || label.contains("Poster", ignoreCase = true) -> {
                            val cleanValue = value.removePrefix(":").trim()
                            metadata["poster_author"] = cleanValue
                        }
                        // Book Type (Тип аудиокниги)
                        label.contains("Тип аудиокниги", ignoreCase = true) || label.contains("Type", ignoreCase = true) -> {
                            metadata["book_type"] = value
                        }
                        // Music (Музыка)
                        label.contains("Музыка", ignoreCase = true) || label.contains("Music", ignoreCase = true) -> {
                            metadata["music"] = value
                        }
                    }
                }
            }

            if (metadata.isEmpty()) {
                val text = postBody?.wholeText() ?: ""
                // Author (fallback)
                "Автор[:\\s]+(.+?)(?=\\n|Исполнитель|Год|$)".toRegex().find(text)?.groupValues?.get(1)?.trim()?.let {
                    metadata["author"] = it
                }
                // Performer
                "Исполнитель[:\\s]+(.+?)(?=\\n|Год|Жанр|$)".toRegex().find(text)?.groupValues?.get(1)?.trim()?.let {
                    metadata["performer"] = it
                }
                // Duration
                "Время звучания[:\\s]+(.+?)(?=\\n|$)".toRegex().find(text)?.groupValues?.get(1)?.trim()?.let {
                    metadata["duration"] = it
                }
                // Bitrate
                "Битрейт[:\\s]+(.+?)(?=\\n|$)".toRegex().find(text)?.groupValues?.get(1)?.trim()?.let {
                    metadata["bitrate"] = it
                }
            }

            return metadata
        }

        private fun extractCoverUrl(postBody: Element?): String? {
            if (postBody == null) return null

            // Look for first image in post
            // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
            val img = postBody.selectFirst("img[src]")
            return img?.absUrl("src")
        }

        private fun extractGenres(postBody: Element?): List<String> {
            if (postBody == null) return emptyList()

            // Strategy 1: Structural parsing (span.post-b)
            val genreSpan =
                postBody.select("span.post-b").firstOrNull {
                    it.text().contains("Жанр", ignoreCase = true) ||
                        it.text().contains("Genre", ignoreCase = true)
                }

            var genreText: String? = null
            if (genreSpan != null) {
                genreText =
                    genreSpan
                        .nextSibling()
                        ?.toString()
                        ?.trim()
                        ?.removePrefix(":")
                        ?.trim()
            }

            // Strategy 2: Regex fallback (using wholeText to preserve newlines)
            if (genreText == null) {
                val text = postBody.wholeText()
                val genrePattern = "Жанр[:\\s]+(.+?)(?=\\n|$)".toRegex()
                genreText = genrePattern.find(text)?.groupValues?.get(1)
            }

            return genreText
                ?.split(",", ";")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

        /**
         * Extract series/cycle name from post body.
         * Looks for: <span class="post-b">Цикл/серия</span>: Будни
         */
        private fun extractSeries(postBody: Element?): String? {
            if (postBody == null) return null

            val text = postBody.toStr()
            // Try multiple patterns
            val patterns =
                listOf(
                    "Цикл/серия[:\\s]+(.+?)(?=\\n|Номер|Жанр|$)".toRegex(RegexOption.IGNORE_CASE),
                    "Цикл[:\\s]+[\"']?(.+?)[\"']?(?=\\n|$)".toRegex(RegexOption.IGNORE_CASE),
                    "Серия[:\\s]+(.+?)(?=\\n|$)".toRegex(RegexOption.IGNORE_CASE),
                )

            for (pattern in patterns) {
                pattern
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.let { return it }
            }

            // Try HTML structure
            postBody.select("span.post-b").forEach { span ->
                val label = span.toStr().trim()
                if (label.contains("Цикл", ignoreCase = true) || label.contains("Серия", ignoreCase = true)) {
                    val nextText = span.nextSibling()?.toString() ?: ""
                    val match = ":\\s*(.+?)(?=\\n|<|$)".toRegex().find(nextText)
                    match
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                        ?.let { return it }
                }
            }

            return null
        }

        /**
         * Extracts forum name from HTML page for use as category during indexing.
         *
         * Strategies (in priority order):
         * 1. From breadcrumbs navigation (td.nav or td.nav-top)
         * 2. From page title
         * 3. From h1.maintitle
         * 4. Fallback: "Аудиокниги"
         *
         * @param html HTML content of forum page
         * @return Forum name to use as category
         */
        private fun extractForumNameFromHTML(html: String): String {
            try {
                val document = Jsoup.parse(html, getBaseUrl())

                // Strategy 1: Breadcrumbs
                // <td class="nav">Аудиокниги » Российская фантастика...</td>
                val breadcrumbs = document.selectFirst("td.nav, td.nav-top")
                if (breadcrumbs != null) {
                    val links = breadcrumbs.select("a")
                    if (links.size >= 2) {
                        // Second element is usually the forum name
                        val forumName = links[1].text().trim()
                        if (forumName.isNotBlank()) {
                            logger.d { "Extracted forum name from breadcrumbs: '$forumName'" }
                            return forumName
                        }
                    }
                }

                // Strategy 2: Page title
                // <title>Фантастика, фэнтези... :: Аудиокниги :: RuTracker.org</title>
                val title = document.title()
                val titleParts = title.split("::")
                if (titleParts.isNotEmpty()) {
                    val forumName = titleParts[0].trim()
                    if (forumName.isNotBlank() && !forumName.contains("RuTracker", ignoreCase = true)) {
                        logger.d { "Extracted forum name from title: '$forumName'" }
                        return forumName
                    }
                }

                // Strategy 3: H1 maintitle
                // <h1 class="maintitle"><a href="...">Фантастика...</a></h1>
                val h1 = document.selectFirst("h1.maintitle a, h1.maintitle")
                if (h1 != null) {
                    val forumName = h1.text().trim()
                    if (forumName.isNotBlank()) {
                        logger.d { "Extracted forum name from h1: '$forumName'" }
                        return forumName
                    }
                }

                // Fallback
                logger.d { "No forum name found, using fallback: 'Аудиокниги'" }
                return "Аудиокниги"
            } catch (e: Exception) {
                logger.w(e) { "Failed to extract forum name from HTML" }
                return "Аудиокниги"
            }
        }

        /**
         * Clean description text by removing metadata fields that are already extracted separately.
         * This prevents duplication of information like author, performer, year, etc.
         */

        /**
         * Clean description text by parsing the cleaned HTML.
         */
        private fun cleanDescription(
            rawText: String, // Kept for signature compatibility, but we might mostly rely on html
            metadata: Map<String, String>,
            cleanedHtml: String? = null,
        ): String {
            // If we have cleaned HTML, use it to generate the text
            if (cleanedHtml != null) {
                return org.jsoup.Jsoup
                    .parse(cleanedHtml)
                    .body()
                    .text()
                    .trim()
            }

            // Fallback to previous logic if no HTML (shouldn't happen with new flow)
            return rawText
        }

        /**
         * Clean description HTML by removing metadata fields from the DOM.
         * Preserves all other content and links.
         */
        private fun cleanDescriptionHtml(
            rawHtml: String,
            metadata: Map<String, String>,
        ): String {
            val doc = org.jsoup.Jsoup.parse(rawHtml, getBaseUrl())
            val body = doc.body()

            // 1. Remove metadata blocks
            // Iterate over span.post-b to find metadata labels
            val metadataLabels =
                setOf(
                    "Год выпуска",
                    "Year",
                    "Автор",
                    "Author",
                    "Авторы",
                    "Authors",
                    "Исполнитель",
                    "Narrator",
                    "Жанр",
                    "Genre",
                    "Издательство",
                    "Publisher",
                    "Тип аудиокниги",
                    "Type",
                    "Audiobook type",
                    "Аудио кодек",
                    "Аудиокодек",
                    "Audio codec",
                    "Codec",
                    "Битрейт",
                    "Битрейт аудио",
                    "Bitrate",
                    "Время звучания",
                    "Duration",
                    "Корректор",
                    "Correction",
                    "Авторский постер",
                    "Poster",
                    "Музыка",
                    "Music",
                    "Цикл",
                    "Серия",
                    "Series",
                    "Cycle",
                    "Номер книги",
                    "Book number",
                )

            // Find all potential metadata labels
            // We convert to list to avoid concurrent modification exceptions when removing
            val spans = body.select("span.post-b").toList()

            for (span in spans) {
                val text =
                    span
                        .text()
                        .trim()
                        .removeSuffix(":")
                        .trim()

                // Check if this span is a metadata label
                val isMetadata =
                    metadataLabels.any { label ->
                        text.equals(label, ignoreCase = true) || text.startsWith(label, ignoreCase = true)
                    }

                if (isMetadata) {
                    // This is a metadata label. We need to remove it and its value.
                    // The value usually follows in a TextNode or another Element, up to the next <br>

                    var next = span.nextSibling()
                    while (next != null) {
                        val current = next
                        next = next.nextSibling() // Advance before removing

                        // Stop if we hit a <br> (end of line)
                        if (current is org.jsoup.nodes.Element && current.tagName() == "br") {
                            current.remove()
                            break
                        }

                        // Stop if we hit another metadata label (safety check, though usually separated by br)
                        if (current is org.jsoup.nodes.Element && current.hasClass("post-b")) {
                            // Oops, we went too far (maybe missing br).
                            // But wait, our loop will handle this next span.
                            // We should probably stop removing *values* if we see a new label.
                            break
                        }

                        // Remove the value node (text or element)
                        current.remove()
                    }
                    // Finally remove the label span itself
                    span.remove()
                }
            }

            // 2. Remove other clutter
            // Remove MediaInfo section
            body.select("div.sp-wrap:has(div.sp-head:contains(MediaInfo))").remove()
            // Remove "General", "Audio", "Video" headers if floating
            body.select("div.sp-body").forEach {
                if (it.text().contains("MediaInfo", ignoreCase = true)) it.remove()
            }

            // Remove var.postImg elements (cover images) - they're handled separately/extracted
            body.select("var.postImg, var.postImgAligned").remove()

            // Remove advertising blocks
            body.select("span.post-align[style*='text-align: center']").remove()
            body.select("a[href*='/go/']").remove()

            // Remove "clear" divs
            body.select("div.clear").remove()

            // 3. Process Links
            // Rewrite local links to absolute, and ensure forum/topic links are correct
            body.select("a[href]").forEach { link ->
                val href = link.attr("href")
                if (href.isNotEmpty()) {
                    if (href.startsWith("viewtopic.php") || href.startsWith("tracker.php")) {
                        // Make absolute using current mirror
                        link.attr("href", doc.baseUri() + href)
                    } else if (href.startsWith("/")) {
                        link.attr("href", doc.baseUri() + href.removePrefix("/"))
                    }
                }
            }

            return body.html()
        }

        /**
         * Extract comments from topic page.
         * Skips the first post_body (main post) and extracts all other comments.
         * Structure: <tbody id="post_XXXXX"> contains <div class="post_body" id="p-XXXXX">
         */
        private fun extractComments(
            document: org.jsoup.nodes.Document,
            topicId: String,
        ): List<com.jabook.app.jabook.compose.data.remote.model.Comment> {
            val comments = mutableListOf<com.jabook.app.jabook.compose.data.remote.model.Comment>()

            try {
                // Find all tbody elements with post IDs (comments are in tbody with id="post_XXXXX")
                val postRows = document.select("tbody[id^='post_']")
                if (postRows.isEmpty()) {
                    // Fallback: try finding post_body elements directly
                    val postBodies = document.select(".post_body")
                    if (postBodies.size <= 1) return emptyList()

                    // Skip first post (index 0) and process comments
                    for (i in 1 until postBodies.size) {
                        val postBody = postBodies[i]
                        val postId = postBody.attr("id")?.removePrefix("p-") ?: continue

                        // Find parent row to get author and date
                        val parentRow = postBody.parents().firstOrNull { it.tagName() == "tbody" }
                        if (parentRow == null) continue

                        // Extract author - try multiple selectors (same logic as main path)
                        val authorElement =
                            parentRow.selectFirst("p.nick a")
                                ?: parentRow.selectFirst(".nick a")
                                ?: parentRow.selectFirst("a[onclick*='bbcode.onclickPoster']")

                        val author =
                            authorElement?.toStr()?.trim()?.takeIf { it.isNotEmpty() }
                                ?: run {
                                    // Fallback 1: Try show-for-print span (for bots and special cases)
                                    parentRow.selectFirst("span.show-for-print.bold")?.let { span ->
                                        val text = span.toStr()
                                        // Extract name before &middot; or ·
                                        text
                                            .split("·", "&middot;", "&nbsp;")
                                            .firstOrNull()
                                            ?.trim()
                                            ?.takeIf { it.isNotEmpty() && it.length > 1 }
                                    }
                                } ?: run {
                                // Fallback 2: Try profile link text
                                parentRow
                                    .selectFirst("a[href*='profile.php?mode=viewprofile']")
                                    ?.toStr()
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                            } ?: "Unknown"

                        // Extract date - try multiple selectors
                        val dateElement =
                            parentRow.selectFirst("a.p-link.small")
                                ?: parentRow.selectFirst(".post-time a")
                                ?: parentRow.selectFirst(".p-link")
                        val date = dateElement?.toStr()?.trim() ?: ""

                        // Extract avatar URL and normalize CDN domain
                        val avatarElement = parentRow.selectFirst("p.avatar img")
                        var avatarUrl =
                            avatarElement
                                ?.attr("src")
                                ?.takeIf { it.isNotEmpty() }

                        // Root relative URLs
                        if (avatarUrl != null && avatarUrl.startsWith("/")) {
                            avatarUrl = "https://rutracker.net$avatarUrl"
                        }

                        val normalizedAvatarUrl = avatarUrl?.let { coverExtractor.normalizeUrl(it) }

                        // Extract comment text and HTML (preserve links)
                        val html = postBody.html()?.takeIf { it.isNotEmpty() }
                        val text =
                            html?.let { htmlContent ->
                                // Convert <br> tags to newlines, then extract text
                                htmlContent
                                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                                    .replace(Regex("<span class=\"post-br\"><br\\s*/?></span>", RegexOption.IGNORE_CASE), "\n")
                                    .let {
                                        org.jsoup.Jsoup
                                            .parse(it)
                                            .toStr()
                                    }.trim()
                            } ?: postBody.toStr().trim()

                        // Clean HTML: normalize <br> tags, quotes and preserve links
                        val cleanedHtml = html?.let { processCommentHtml(it).body().html() }

                        if (text.isNotEmpty() && text.length > 10) { // Filter out very short comments
                            comments.add(
                                com.jabook.app.jabook.compose.data.remote.model.Comment(
                                    id = postId,
                                    author = author,
                                    date = date,
                                    text = text,
                                    html = cleanedHtml,
                                    avatarUrl = normalizedAvatarUrl,
                                ),
                            )
                        }
                    }
                    // Return last 50 comments (most recent)
                    // Comments are in chronological order (oldest first), UI will reverse to show newest first
                    return if (comments.size > 50) {
                        comments.takeLast(50)
                    } else {
                        comments
                    }
                }

                // Skip first post (main post) and process comments
                for (i in 1 until postRows.size) {
                    val postRow = postRows[i]
                    val postIdAttr = postRow.attr("id")
                    val postId = postIdAttr.removePrefix("post_")

                    // Extract post body
                    val postBody = postRow.selectFirst(".post_body")
                    if (postBody == null) continue

                    // Extract author - try multiple selectors
                    // For bots and special cases, also check show-for-print span
                    val authorElement =
                        postRow.selectFirst("p.nick a")
                            ?: postRow.selectFirst(".nick a")
                            ?: postRow.selectFirst("a[onclick*='bbcode.onclickPoster']")

                    val author =
                        authorElement?.text()?.trim()?.takeIf { it.isNotEmpty() }
                            ?: run {
                                // Fallback 1: Try show-for-print span (for bots and special cases)
                                // Format: "Author &middot;" or "Author ·"
                                postRow.selectFirst("span.show-for-print.bold")?.let { span ->
                                    val text = span.text()
                                    // Extract name before &middot; or ·
                                    text
                                        .split("·", "&middot;", "&nbsp;")
                                        .firstOrNull()
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() && it.length > 1 }
                                }
                            } ?: run {
                            // Fallback 2: Try profile link text
                            postRow
                                .selectFirst("a[href*='profile.php?mode=viewprofile']")
                                ?.text()
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                        } ?: "Unknown"

                    // Extract date - try multiple selectors
                    val dateElement =
                        postRow.selectFirst("a.p-link.small")
                            ?: postRow.selectFirst(".post-time a")
                            ?: postRow.selectFirst(".p-link")
                    val date = dateElement?.text()?.trim() ?: ""

                    // Extract avatar URL and normalize CDN domain
                    val avatarElement = postRow.selectFirst("p.avatar img")
                    var avatarUrl =
                        avatarElement
                            ?.attr("src")
                            ?.takeIf { it.isNotEmpty() }

                    // Root relative URLs
                    if (avatarUrl != null && avatarUrl.startsWith("/")) {
                        avatarUrl = "https://rutracker.net$avatarUrl"
                    }

                    val normalizedAvatarUrl = avatarUrl?.let { coverExtractor.normalizeUrl(it) }

                    // Extract comment text and HTML (preserve links)
                    val html = postBody.html()?.takeIf { it.isNotEmpty() }
                    val text =
                        html?.let { htmlContent ->
                            // Convert <br> tags to newlines, then extract text
                            htmlContent
                                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                                .replace(Regex("<span class=\"post-br\"><br\\s*/?></span>", RegexOption.IGNORE_CASE), "\n")
                                .let {
                                    org.jsoup.Jsoup
                                        .parse(it)
                                        .toStr()
                                }.trim()
                        } ?: postBody.toStr().trim()

                    // Clean HTML: normalize <br> tags, quotes and preserve links
                    val cleanedHtml =
                        html?.let {
                            val doc = processCommentHtml(it)

                            // Append signature if present
                            val signature = postRow.selectFirst(".signature")
                            if (signature != null) {
                                // Process signature links similar to body
                                val signatureDoc = processCommentHtml(signature.html())
                                doc.body().append("<br><div class='signature'>${signatureDoc.body().html()}</div>")
                            }

                            doc.body().html()
                        }

                    if (text.isNotEmpty() && text.length > 10) { // Filter out very short comments
                        comments.add(
                            com.jabook.app.jabook.compose.data.remote.model.Comment(
                                id = postId,
                                author = author,
                                date = date,
                                text = text,
                                html = cleanedHtml,
                                avatarUrl = avatarUrl,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to extract comments" }
            }

            // Return last 50 comments (most recent)
            // Comments are in chronological order (oldest first), UI will reverse to show newest first
            return if (comments.size > 50) {
                comments.takeLast(50)
            } else {
                comments
            }
        }

        private fun processCommentHtml(rawHtml: String): org.jsoup.nodes.Document {
            // Normalize <br> and <span class="post-br">
            val intermediate =
                rawHtml
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "<br>")
                    .replace(Regex("<span class=\"post-br\"><br\\s*/?></span>", RegexOption.IGNORE_CASE), "<br>")

            // Parse with baseUri for proper absolute URL resolution
            val doc = org.jsoup.Jsoup.parse(intermediate, getBaseUrl())

            // Transform quotes
            transformQuotes(doc)

            // Convert relative links to absolute
            doc.select("a[href]").forEach { link ->
                val href = link.attr("href")
                if (href.isNotEmpty() &&
                    !href.startsWith("http://") &&
                    !href.startsWith("https://") &&
                    !href.startsWith("magnet:")
                ) {
                    link.attr("href", doc.baseUri() + href.removePrefix("/"))
                }
            }
            return doc
        }

        private fun transformQuotes(doc: org.jsoup.nodes.Document) {
            val headers = doc.select("div.q-head")
            for (header in headers) {
                val quoteBody = header.nextElementSibling()
                if (quoteBody != null && quoteBody.hasClass("q")) {
                    val blockquote = doc.createElement("blockquote")

                    // Format header
                    val authorRaw = header.text().replace("писал(а):", "").trim()
                    if (authorRaw.isNotEmpty()) {
                        blockquote.appendElement("b").text("$authorRaw wrote:")
                        blockquote.appendElement("br")
                    } else {
                        blockquote.appendElement("b").text("Quote:")
                        blockquote.appendElement("br")
                    }

                    // Remove internal post ID
                    quoteBody.select("u.q-post").remove()

                    // Move content
                    blockquote.appendChildren(quoteBody.childNodes())

                    header.replaceWith(blockquote)
                    quoteBody.remove()
                }
            }
        }

        private fun extractRelatedBooks(postBody: Element?): List<RelatedBook> {
            if (postBody == null) return emptyList()

            val related = mutableListOf<RelatedBook>()

            // Look for links to other topics
            // Use selectStream() for lazy evaluation of large lists (jsoup 1.19.1+)
            val links = postBody.select("a[href*=\"viewtopic.php?t=\"]")
            for (link in links) {
                // Use absUrl() for proper absolute URL resolution
                val href = link.absUrl("href")
                val topicId = href.substringAfter("t=").substringBefore("&")
                val title = link.text()

                if (topicId.isNotEmpty() && title.isNotEmpty()) {
                    related.add(RelatedBook(topicId, title))
                }
            }

            return related.take(10) // Limit to 10 related books
        }

        /**
         * Clean title by removing technical details.
         *
         * Removes:
         * - Content in square brackets ([...])
         * - Quality indicators (WEB-DL, BDRip, etc.)
         * - Resolution (1080p, 720p, etc.)
         * - File formats (MKV, MP4, AVI, etc.)
         *
         * @param rawTitle Raw title from Rutracker
         * @return Cleaned, human-readable title
         */
        private fun cleanTitle(rawTitle: String): String {
            var cleaned = rawTitle

            // Remove content in square brackets: [1962, СССР, рисованный мультфильм]
            cleaned = cleaned.replace(Regex("\\[.*?\\]"), "")

            // Remove quality indicators
            val qualityPatterns =
                listOf(
                    "WEB-DL",
                    "WEBRip",
                    "BDRip",
                    "DVDRip",
                    "HDTV",
                    "BluRay",
                    "Blu-Ray",
                    "BD-Rip",
                    "Web-DL",
                    "WebRip",
                )
            for (pattern in qualityPatterns) {
                cleaned = cleaned.replace(Regex("\\b$pattern\\b", RegexOption.IGNORE_CASE), "")
            }

            // Remove resolutions: 1080p, 720p, 2160p, etc.
            cleaned = cleaned.replace(Regex("\\b\\d{3,4}[pi]\\b", RegexOption.IGNORE_CASE), "")

            // Remove file formats
            val formatPatterns =
                listOf(
                    "MKV",
                    "MP4",
                    "AVI",
                    "MOV",
                    "WMV",
                    "FLV",
                    "M4V",
                    "MP3",
                    "AAC",
                    "FLAC",
                    "OGG",
                    "WAV",
                    "M4A",
                )
            for (pattern in formatPatterns) {
                cleaned = cleaned.replace(Regex("\\b$pattern\\b", RegexOption.IGNORE_CASE), "")
            }

            // Remove extra whitespace and trim
            cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

            // Remove trailing/leading dashes, commas, and periods
            cleaned = cleaned.trim('-', ',', '.', ' ')

            return cleaned.ifEmpty { rawTitle } // Return original if cleaning results in empty string
        }

        /**
         * Result of a login attempt.
         */
        public sealed interface LoginResult {
            public data object Success : LoginResult

            public data class Error(
                val message: String,
            ) : LoginResult

            public data class Captcha(
                val data: com.jabook.app.jabook.compose.domain.model.CaptchaData,
            ) : LoginResult
        }

        /**
         * Parse login response from HTML with detailed logging.
         *
         * @param html HTML content from login response
         * @return LoginResult
         */
        public fun parseLoginResponse(html: String): LoginResult {
            logger.d { "Parsing login response, html length: ${html.length}" }

            val document = Jsoup.parse(html)
            val lowerHtml = html.lowercase()

            // Check for ERROR: wrong username/password (PRIORITY!)
            // Russian: "неверный пароль" or "неверное имя пользователя"
            if (lowerHtml.contains("неверн")) {
                logger.w { "Login failed: Invalid credentials detected in response" }
                return LoginResult.Error("Invalid username or password")
            }

            // Check for CAPTCHA requirement
            // Russian: "введите код подтверждения" or "введите код с картинки"
            if (lowerHtml.contains("введите код") || lowerHtml.contains("cap_code")) {
                logger.i { "Captcha required, extracting captcha data" }
                val captchaData = extractCaptcha(html)
                if (captchaData != null) {
                    return LoginResult.Captcha(captchaData)
                } else {
                    logger.w { "Captcha detected but extraction failed" }
                    return LoginResult.Error("Captcha required but couldn't extract data")
                }
            }

            // Check for successful login
            // Success indicators:
            // - No login form present
            // - Logout link present
            // - User profile links
            val hasLoginForm = lowerHtml.contains("name=\\\"login_username\\\"")
            val hasLogout =
                lowerHtml.contains("login.php?logout=1") ||
                    lowerHtml.contains("mode=logout")

            if (!hasLoginForm || hasLogout) {
                logger.i { "Login successful (hasLoginForm=$hasLoginForm, hasLogout=$hasLogout)" }
                return LoginResult.Success
            }

            // Unknown error
            logger.w { "Login failed: Unknown error (no specific markers found)" }
            return LoginResult.Error("Authentication failed. Please try again.")
        }

        private fun extractCaptcha(html: String): com.jabook.app.jabook.compose.domain.model.CaptchaData? {
            try {
                // Parse with baseUri for proper absolute URL resolution (using current mirror)
                val document = Jsoup.parse(html, getBaseUrl())

                // <input type="hidden" name="cap_sid" value="12345">
                val sidElement = document.selectFirst("input[name=cap_sid]")
                val sid = sidElement?.attr("value") ?: return null

                // <img src="//static.t-ru.org/captcha/..." ...>
                // Use absUrl() for proper absolute URL resolution
                val imgElement = document.selectFirst("img[src*=\"captcha\"]")
                var url = imgElement?.absUrl("src") ?: imgElement?.attr("src") ?: return null

                if (url.startsWith("//")) {
                    url = "https:$url"
                }

                return com.jabook.app.jabook.compose.domain.model
                    .CaptchaData(url, sid)
            } catch (e: Exception) {
                logger.e(e) { "Failed to parse captcha" }
                return null
            }
        }
    }
