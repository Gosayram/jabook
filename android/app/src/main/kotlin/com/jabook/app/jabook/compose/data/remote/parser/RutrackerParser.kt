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
    constructor(
        private val mediaInfoParser: MediaInfoParser,
        private val encodingHandler: com.jabook.app.jabook.compose.data.remote.encoding.DefensiveEncodingHandler,
        private val fieldExtractor: DefensiveFieldExtractor,
        private val coverExtractor: CoverUrlExtractor,
    ) {
        companion object {
            private const val TAG = "RutrackerParser"

            // CSS Selectors for search results - UPDATED for 2025 based on robust Dart implementation
            // Primary and fallback selectors for rows
            private val ROW_SELECTORS =
                listOf(
                    "tr.hl-tr",
                    "tr[data-topic_id]",
                    "table.forumline tr.hl-tr", // More specific
                    "table.forumline tr[id^='t-']", // ID-based
                    "tbody#TORRENT_LIST_TBODY tr", // Structure-based
                )

            private const val TITLE_SELECTOR = "a.torTopic, a.tt-text, a[href*='viewtopic.php?t=']"
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

            private const val BASE_URL = "https://rutracker.org/forum/"
        }

        /**
         * Parse search results from raw bytes with encoding detection.
         *
         * This method uses DefensiveEncodingHandler to properly decode
         * RuTracker's windows-1251 or mixed encoding content.
         *
         * @param bytes Raw response bytes
         * @param contentType Optional Content-Type header
         * @return ParsingResult with search results
         */
        fun parseSearchResultsWithEncoding(
            bytes: ByteArray,
            contentType: String? = null,
        ): ParsingResult<List<SearchResult>> {
            // Decode with defensive handler
            val decodingResult = encodingHandler.decode(bytes, contentType)

            // Log encoding information
            Log.d(
                TAG,
                "Decoded with ${decodingResult.encoding}, confidence=${decodingResult.confidence}, hasMojibake=${decodingResult.hasMojibake}",
            )

            // Warn if mojibake detected
            if (decodingResult.hasMojibake) {
                Log.w(TAG, "Mojibake detected in response, results may be corrupted")
            }

            // Parse the decoded HTML
            return parseSearchResultsDefensive(decodingResult.text)
        }

        /**
         * Parse search results with graceful error handling.
         *
         * Returns ParsingResult instead of crashing on errors.
         *
         * @param html Decoded HTML content
         * @return ParsingResult with results, warnings, or errors
         */
        private fun parseSearchResultsDefensive(html: String): ParsingResult<List<SearchResult>> {
            val errors = mutableListOf<ParsingError>()
            val results = mutableListOf<SearchResult>()

            try {
                val document = Jsoup.parse(html)

                // Try to find rows using multiple selectors strategy
                var rows = org.jsoup.select.Elements()
                var successfulSelector = ""

                for (selector in ROW_SELECTORS) {
                    val found = document.select(selector)
                    if (found.isNotEmpty()) {
                        // Filter out header/ad rows if generic selector used
                        val validRows =
                            found.filter { row ->
                                // Basic validation: must have some content/structure
                                !row.hasClass("vf-col-header-row") &&
                                    !row.select(TITLE_SELECTOR).isEmpty()
                            }

                        if (validRows.isNotEmpty()) {
                            rows = org.jsoup.select.Elements(validRows)
                            successfulSelector = selector
                            Log.d(TAG, "Found ${rows.size} rows using validation-checked selector: $selector")
                            break
                        }

                        // If selected rows were all invalid, try next selector
                    }
                }

                if (rows.isEmpty()) {
                    // Check if it's just an empty result set (valid page, no results)
                    val isSearchPage = document.select("form#quick-search, input[name=nm]").isNotEmpty()
                    val isIndexPage = document.select("#forums_list_wrap").isNotEmpty()

                    if (isSearchPage || isIndexPage) {
                        Log.i(TAG, "No rows found, but page looks like valid search/index page (empty results)")
                        return ParsingResult.Success(emptyList())
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
                                errors.add(
                                    ParsingError(
                                        field = "row_$index",
                                        reason = "Failed to extract required fields",
                                        severity = ErrorSeverity.WARNING,
                                        htmlSnippet = row.html().take(200),
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        errors.add(
                            ParsingError(
                                field = "row_$index",
                                reason = "Exception: ${e.message}",
                                severity = ErrorSeverity.ERROR,
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
                Log.e(TAG, "Failed to parse search results", e)
                errors.add(
                    ParsingError(
                        field = "document",
                        reason = "Failed to parse HTML: ${e.message}",
                        severity = ErrorSeverity.CRITICAL,
                    ),
                )
                return ParsingResult.Failure(errors, emptyList())
            }
        }

        /**
         * Parse search results from HTML.
         *
         * @param html HTML content from search results page
         * @return List of search results
         */
        fun parseSearchResults(html: String): List<SearchResult> {
            Log.d(TAG, "=== PARSING SEARCH RESULTS ===")
            // internal implementation delegates to parseSearchResultsDefensive logic equivalent
            // For backward compatibility / simple calls

            try {
                val document = Jsoup.parse(html)

                var rows = org.jsoup.select.Elements()
                for (selector in ROW_SELECTORS) {
                    val found = document.select(selector)
                    if (found.isNotEmpty()) {
                        val validRows = found.filter { !it.select(TITLE_SELECTOR).isEmpty() }
                        if (validRows.isNotEmpty()) {
                            rows = org.jsoup.select.Elements(validRows)
                            Log.d(TAG, "Using selector '$selector': ${rows.size} rows")
                            break
                        }
                    }
                }

                if (rows.isEmpty()) {
                    Log.w(TAG, "⚠️ NO ROWS FOUND with any selector! Running diagnostics...")
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
                                Log.d(TAG, "✓ Result $idx: ${result.title} by ${result.author}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "✗ Failed to parse row $idx", e)
                    }
                }

                Log.d(TAG, "✅ Successfully parsed ${results.size}/${rows.size} results")
                return results
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse search results", e)
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
                        row
                            .selectFirst(TITLE_SELECTOR)
                            ?.attr("href")
                            ?.substringAfter("t=")
                            ?.substringBefore("&")
                            ?: return null
                    }
                }

            if (topicId.isEmpty()) {
                // Common for header rows or ads, detailed logging usually not needed unless debugging structure
                return null
            }

            // Extract title - use updated selector
            val titleElement = row.selectFirst(TITLE_SELECTOR)
            if (titleElement == null) {
                // Log.w(TAG, "No title found for topic $topicId")
                return null
            }
            val title = titleElement.text()
            if (title.isEmpty()) {
                return null
            }

            // Extract author - use new selector
            val authorElement = row.selectFirst(AUTHOR_SELECTOR)
            val author =
                authorElement?.text()?.trim()?.ifEmpty { null } ?: run {
                    "Unknown"
                }

            // Extract category (from data attribute or default)
            val category = row.attr("data-forum_id").ifEmpty { "Audiobooks" }

            // Use DefensiveFieldExtractor for robust extraction
            val size = fieldExtractor.extractSize(row, topicId)
            val seeders = fieldExtractor.extractSeeders(row, topicId)
            val leechers = fieldExtractor.extractLeechers(row, topicId)

            // Extract magnet link
            val magnetElement = row.selectFirst(MAGNET_LINK_SELECTOR)
            val magnetUrl = magnetElement?.attr("href")

            // Extract torrent download URL (using DOWNLOAD_HREF_SELECTOR)
            val torrentElement = row.selectFirst(DOWNLOAD_HREF_SELECTOR)
            val torrentUrl = torrentElement?.attr("href")?.let { BASE_URL + it } ?: ""

            // Extract cover URL with improved selectors
            val coverUrl =
                row.selectFirst("img[src]")?.attr("abs:src")
                    ?: row.selectFirst("img.postImg")?.attr("abs:src")
                    ?: row.selectFirst("img.preview")?.attr("abs:src")
                    ?: row.selectFirst("img.thumbnail")?.attr("abs:src")
                    ?: row.selectFirst("img[src*='static.t-ru.org']")?.attr("abs:src")
                    ?: row.selectFirst("img[src*='i.rutracker.cc']")?.attr("abs:src")

            // Clean the title to remove technical details
            val cleanedTitle = cleanTitle(title)

            return SearchResult(
                topicId = topicId,
                title = cleanedTitle,
                author = author,
                category = category,
                size = size,
                seeders = seeders,
                leechers = leechers,
                magnetUrl = magnetUrl,
                torrentUrl = torrentUrl,
                coverUrl = coverUrl,
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

                // Clean the title
                val cleanedTitle = cleanTitle(title)

                // Extract and parse MediaInfo if present in description
                val descriptionText = postBody?.text() ?: ""
                val parsedMediaInfo = mediaInfoParser.parse(descriptionText)

                return TopicDetails(
                    topicId = topicId,
                    title = cleanedTitle,
                    author = metadata["author"],
                    performer = metadata["performer"],
                    category = "Audiobooks",
                    size = size,
                    seeders = metadata["seeders"]?.toIntOrNull() ?: 0,
                    leechers = metadata["leechers"]?.toIntOrNull() ?: 0,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    coverUrl = postBody?.let { coverExtractor.extract(it) },
                    genres = extractGenres(postBody),
                    addedDate = metadata["addedDate"],
                    duration = metadata["duration"],
                    bitrate = metadata["bitrate"],
                    audioCodec = metadata["codec"],
                    description = descriptionText,
                    mediaInfo = parsedMediaInfo,
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
        sealed interface LoginResult {
            data object Success : LoginResult

            data class Error(
                val message: String,
            ) : LoginResult

            data class Captcha(
                val data: com.jabook.app.jabook.compose.domain.model.CaptchaData,
            ) : LoginResult
        }

        /**
         * Parse login response from HTML with detailed logging.
         *
         * @param html HTML content from login response
         * @return LoginResult
         */
        fun parseLoginResponse(html: String): LoginResult {
            android.util.Log.d(TAG, "Parsing login response, html length: ${html.length}")

            val document = Jsoup.parse(html)
            val lowerHtml = html.lowercase()

            // Check for ERROR: wrong username/password (PRIORITY!)
            // Russian: "неверный пароль" or "неверное имя пользователя"
            if (lowerHtml.contains("неверн")) {
                android.util.Log.w(TAG, "Login failed: Invalid credentials detected in response")
                return LoginResult.Error("Invalid username or password")
            }

            // Check for CAPTCHA requirement
            // Russian: "введите код подтверждения" or "введите код с картинки"
            if (lowerHtml.contains("введите код") || lowerHtml.contains("cap_code")) {
                android.util.Log.i(TAG, "Captcha required, extracting captcha data")
                val captchaData = extractCaptcha(html)
                if (captchaData != null) {
                    return LoginResult.Captcha(captchaData)
                } else {
                    android.util.Log.w(TAG, "Captcha detected but extraction failed")
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
                android.util.Log.i(TAG, "Login successful (hasLoginForm=$hasLoginForm, hasLogout=$hasLogout)")
                return LoginResult.Success
            }

            // Unknown error
            android.util.Log.w(TAG, "Login failed: Unknown error (no specific markers found)")
            return LoginResult.Error("Authentication failed. Please try again.")
        }

        private fun extractCaptcha(html: String): com.jabook.app.jabook.compose.domain.model.CaptchaData? {
            try {
                val document = Jsoup.parse(html)

                // <input type="hidden" name="cap_sid" value="12345">
                val sidElement = document.selectFirst("input[name=cap_sid]")
                val sid = sidElement?.attr("value") ?: return null

                // <img src="//static.t-ru.org/captcha/..." ...>
                val imgElement = document.selectFirst("img[src*=\"captcha\"]")
                var url = imgElement?.attr("src") ?: return null

                if (url.startsWith("//")) {
                    url = "https:$url"
                }

                return com.jabook.app.jabook.compose.domain.model
                    .CaptchaData(url, sid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse captcha", e)
                return null
            }
        }
    }
