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

import android.util.Log
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.model.AudiobookCategory
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for extracting audiobook categories from RuTracker forum structure.
 *
 * Parses the category structure from RuTracker forum pages,
 * focusing on the audiobooks section (c=33).
 *
 * Based on Flutter implementation with defensive programming enhancements.
 */
@Singleton
class CategoryParser
    @Inject
    constructor(
        private val mirrorManager: MirrorManager,
    ) {
        companion object {
            private const val TAG = "CategoryParser"

            // RuTracker audiobooks category ID
            const val AUDIOBOOKS_CATEGORY_ID = "33"

            // CSS Selectors
            private const val CATEGORY_ROOT_PREFIX = "#c-"
            private const val FORUM_ROW_SELECTOR = "tr[id^=\"f-\"]"
            private const val FORUM_LINK_SELECTOR = "h4.forumlink a"
            private const val SUBFORUMS_SELECTOR = ".subforums"

            // Blacklist patterns for filtering unwanted forums
            private val FORUM_BLACKLIST =
                setOf(
                    "новости",
                    "объявления",
                    "полезная информация",
                    "обсуждение",
                    "общение",
                    "предложения",
                    "поиск",
                    "авторы",
                    "исполнители",
                )

            // Blacklist patterns for filtering unwanted subcategories
            private val CATEGORY_BLACKLIST =
                setOf(
                    "новости",
                    "объявления",
                    "полезная информация",
                    "обсуждение",
                    "технический",
                    "флудильня",
                    "оффтоп",
                    "помощь",
                    "правила",
                )
        }

        /**
         * Parse main audiobooks categories page from RuTracker.
         *
         * Extracts categories and subcategories from forum structure,
         * filtering out unwanted sections (news, announcements, etc.).
         *
         * @param html HTML content (already decoded)
         * @return ParsingResult with list of categories
         */
        fun parseCategories(html: String): ParsingResult<List<AudiobookCategory>> {
            val errors = mutableListOf<ParsingError>()
            val categories = mutableListOf<AudiobookCategory>()

            try {
                // Parse with baseUri for proper absolute URL resolution
                // Using current mirror base URL for resolving relative links
                val baseUrl = "${mirrorManager.getBaseUrl()}/forum/"
                val document = Jsoup.parse(html, baseUrl)

                // Find audiobooks category (c=33)
                val audiobooksCategoryElement =
                    document.selectFirst("$CATEGORY_ROOT_PREFIX$AUDIOBOOKS_CATEGORY_ID")

                if (audiobooksCategoryElement == null) {
                    errors.add(
                        ParsingError(
                            field = "audiobooks_category",
                            reason = "Category c=$AUDIOBOOKS_CATEGORY_ID not found",
                            severity = ErrorSeverity.CRITICAL,
                            htmlSnippet = html.take(500),
                        ),
                    )
                    return ParsingResult.Failure(errors, emptyList())
                }

                // Extract forum rows
                val forumRows = audiobooksCategoryElement.select(FORUM_ROW_SELECTOR)
                Log.d(TAG, "Found ${forumRows.size} forum rows in audiobooks category")

                for (row in forumRows) {
                    try {
                        val category = parseForumRow(row)
                        if (category != null) {
                            categories.add(category)
                        }
                    } catch (e: Exception) {
                        errors.add(
                            ParsingError(
                                field = "forum_row",
                                reason = "Error parsing row: ${e.message}",
                                severity = ErrorSeverity.WARNING,
                            ),
                        )
                    }
                }

                Log.i(TAG, "Parsed ${categories.size} valid categories")

                return when {
                    errors.isEmpty() -> ParsingResult.Success(categories)
                    categories.isNotEmpty() -> ParsingResult.PartialSuccess(categories, errors)
                    else -> ParsingResult.Failure(errors, emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse categories", e)
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
         * Parse a single forum row into AudiobookCategory.
         *
         * @param row Forum row element
         * @return AudiobookCategory or null if should be skipped
         */
        private fun parseForumRow(row: Element): AudiobookCategory? {
            // Extract forum link
            val forumLink = row.selectFirst(FORUM_LINK_SELECTOR) ?: return null

            val forumName = forumLink.text().trim()
            // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
            val forumUrl = forumLink.absUrl("href")
            val forumId = row.id().removePrefix("f-")

            // Skip if blacklisted
            if (forumId.isEmpty() || shouldIgnoreForum(forumName)) {
                Log.d(TAG, "Skipping forum: $forumName (blacklisted or empty ID)")
                return null
            }

            // Parse subcategories
            val subcategories = parseSubcategories(row)

            return AudiobookCategory(
                id = forumId,
                name = forumName,
                url = forumUrl,
                subcategories = subcategories,
            )
        }

        /**
         * Parse subcategories from forum row.
         *
         * @param row Forum row element containing subforums section
         * @return List of subcategories
         */
        private fun parseSubcategories(row: Element): List<AudiobookCategory> {
            val subcategories = mutableListOf<AudiobookCategory>()

            val subforumsElement = row.selectFirst(SUBFORUMS_SELECTOR) ?: return emptyList()

            val subforumLinks = subforumsElement.select("a")

            for (link in subforumLinks) {
                val name = link.text().trim()
                // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
                val url = link.absUrl("href")
                val id = extractForumId(url)

                if (id.isNotEmpty() && !shouldIgnoreCategory(name)) {
                    subcategories.add(
                        AudiobookCategory(
                            id = id,
                            name = name,
                            url = url,
                        ),
                    )
                }
            }

            return subcategories
        }

        /**
         * Extract forum ID from URL.
         *
         * Matches pattern: f=<number>
         *
         * @param url URL containing forum ID
         * @return Forum ID or empty string
         */
        private fun extractForumId(url: String): String {
            val regex = Regex("""f=(\d+)""")
            return regex.find(url)?.groupValues?.get(1) ?: ""
        }

        /**
         * Check if forum should be ignored based on name.
         *
         * @param forumName Forum name to check
         * @return true if should be ignored
         */
        private fun shouldIgnoreForum(forumName: String): Boolean {
            val lowerName = forumName.lowercase()
            return FORUM_BLACKLIST.any { lowerName.contains(it) }
        }

        /**
         * Check if category should be ignored based on name.
         *
         * @param categoryName Category name to check
         * @return true if should be ignored
         */
        private fun shouldIgnoreCategory(categoryName: String): Boolean {
            val lowerName = categoryName.lowercase()
            return CATEGORY_BLACKLIST.any { lowerName.contains(it) }
        }
    }
