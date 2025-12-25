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
import org.jsoup.nodes.Element

/**
 * Defensive field extractor with cascading selectors.
 *
 * Implements multiple fallback strategies for each field to handle
 * RuTracker's inconsistent HTML structure across different page versions.
 *
 * Philosophy: Never fail, always try multiple strategies, return sensible defaults.
 */
class DefensiveFieldExtractor {
    companion object {
        private const val TAG = "DefensiveFieldExtractor"
    }

    /**
     * Extract seeders count with 6 fallback strategies.
     *
     * Strategies:
     * 1. New structure: td.vf-col-tor > span.seed(med) > b
     * 2. Legacy structure: span.seed, span.seedmed (with or without b tag)
     * 3. Direct td.seed class
     * 4. Data attribute: data-seeds
     * 5. Column position fallback (often 7th column)
     * 6. Regex on row text (last resort)
     *
     * @param row Topic row element
     * @param topicId Topic ID for logging
     * @return Seeders count (0 if not found)
     */
    fun extractSeeders(
        row: Element,
        topicId: String,
    ): Int {
        // Strategy 1: New structure (vf-col-tor > span.seedmed > b)
        val torCol = row.selectFirst("td.vf-col-tor")
        if (torCol != null) {
            val value =
                extractNumberFromElement(
                    torCol.selectFirst("span.seed b, span.seedmed b"),
                )
            if (value != null) {
                Log.d(TAG, "Seeders for $topicId: $value (strategy: vf-col-tor > seedmed > b)")
                return value
            }
        }

        // Strategy 2: Legacy structure (span.seed, span.seedmed)
        val seedElement = row.selectFirst("span.seed, span.seedmed")
        if (seedElement != null) {
            // Try b tag first
            val bValue = extractNumberFromElement(seedElement.selectFirst("b"))
            if (bValue != null) {
                Log.d(TAG, "Seeders for $topicId: $bValue (strategy: span.seed > b)")
                return bValue
            }
            // Try direct text
            val directValue = seedElement.text().trim().toIntOrNull()
            if (directValue != null) {
                Log.d(TAG, "Seeders for $topicId: $directValue (strategy: span.seed text)")
                return directValue
            }
        }

        // Strategy 3: Direct td.seed
        val seedTd = row.selectFirst("td.seed")
        if (seedTd != null) {
            val value = seedTd.text().trim().toIntOrNull()
            if (value != null) {
                Log.d(TAG, "Seeders for $topicId: $value (strategy: td.seed)")
                return value
            }
        }

        // Strategy 4: Data attribute
        val dataSeeds = row.attr("data-seeds")
        if (dataSeeds.isNotEmpty()) {
            val value = dataSeeds.toIntOrNull()
            if (value != null) {
                Log.d(TAG, "Seeders for $topicId: $value (strategy: data-seeds)")
                return value
            }
        }

        // Strategy 5: Column position (often 7th column = index 6)
        val colValue =
            row
                .select("td")
                .getOrNull(6)
                ?.text()
                ?.trim()
                ?.toIntOrNull()
        if (colValue != null) {
            Log.d(TAG, "Seeders for $topicId: $colValue (strategy: column position)")
            return colValue
        }

        // Strategy 6: Regex fallback (last resort)
        val regexMatch = Regex("""[↑↑]\s*(\d+)|Сиды[:\s]*(\d+)""").find(row.text())
        if (regexMatch != null) {
            val value =
                regexMatch.groupValues
                    .drop(1)
                    .firstNotNullOfOrNull { it.toIntOrNull() }
            if (value != null) {
                Log.d(TAG, "Seeders for $topicId: $value (strategy: regex)")
                return value
            }
        }

        // All strategies failed
        Log.w(TAG, "Failed to extract seeders for topic $topicId")
        return 0
    }

    /**
     * Extract leechers count with 6 fallback strategies.
     *
     * Similar strategies to seeders extraction.
     *
     * @param row Topic row element
     * @param topicId Topic ID for logging
     * @return Leechers count (0 if not found)
     */
    fun extractLeechers(
        row: Element,
        topicId: String,
    ): Int {
        // Strategy 1: New structure (vf-col-tor > span.leechmed > b)
        val torCol = row.selectFirst("td.vf-col-tor")
        if (torCol != null) {
            val value =
                extractNumberFromElement(
                    torCol.selectFirst("span.leech b, span.leechmed b"),
                )
            if (value != null) {
                return value
            }
        }

        // Strategy 2: Legacy structure (span.leech, span.leechmed)
        val leechElement = row.selectFirst("span.leech, span.leechmed")
        if (leechElement != null) {
            val bValue = extractNumberFromElement(leechElement.selectFirst("b"))
            if (bValue != null) return bValue

            val directValue = leechElement.text().trim().toIntOrNull()
            if (directValue != null) return directValue
        }

        // Strategy 3: Direct td.leech
        val leechTd = row.selectFirst("td.leech")
        if (leechTd != null) {
            val value = leechTd.text().trim().toIntOrNull()
            if (value != null) return value
        }

        // Strategy 4: Data attribute
        val dataLeechers = row.attr("data-leechers")
        if (dataLeechers.isNotEmpty()) {
            val value = dataLeechers.toIntOrNull()
            if (value != null) return value
        }

        // Strategy 5: Column position (often 8th column = index 7)
        val colValue =
            row
                .select("td")
                .getOrNull(7)
                ?.text()
                ?.trim()
                ?.toIntOrNull()
        if (colValue != null) return colValue

        // Strategy 6: Regex fallback
        val regexMatch = Regex("""[↓↓]\s*(\d+)|Личи[:\s]*(\d+)""").find(row.text())
        if (regexMatch != null) {
            val value =
                regexMatch.groupValues
                    .drop(1)
                    .firstNotNullOfOrNull { it.toIntOrNull() }
            if (value != null) return value
        }

        // All strategies failed
        Log.d(TAG, "Failed to extract leechers for topic $topicId")
        return 0
    }

    /**
     * Extract size with multiple fallback strategies and validation.
     *
     * @param row Topic row element
     * @param topicId Topic ID for logging
     * @return Size string (e.g., "1.5 GB") or "Unknown"
     */
    fun extractSize(
        row: Element,
        topicId: String,
    ): String {
        // Strategy 1: span.small or a.f-dl.dl-stub
        val sizeElement = row.selectFirst("span.small, a.f-dl.dl-stub, td.small, .small")
        if (sizeElement != null) {
            val size = sizeElement.text().trim()
            if (isValidSize(size)) {
                return size
            }
        }

        // Strategy 2: td.tor-size
        val torSizeTd = row.selectFirst("td.tor-size")
        if (torSizeTd != null) {
            val size = torSizeTd.text().trim()
            if (isValidSize(size)) return size
        }

        // Strategy 3: Column position (often 6th column = index 5)
        val colElement = row.select("td").getOrNull(5)
        if (colElement != null) {
            val size = colElement.text().trim()
            if (isValidSize(size)) return size
        }

        // Strategy 4: Download link (dl.php) often near size
        val dlLink = row.selectFirst("a[href*='dl.php']")
        if (dlLink != null) {
            val size = dlLink.text().trim()
            if (isValidSize(size)) return size
        }

        // Strategy 5: Regex for typical size patterns
        val regexMatch =
            Regex("""(\d+\.?\d*\s*[KMGT]B)""", RegexOption.IGNORE_CASE)
                .find(row.text())
        if (regexMatch != null) {
            return regexMatch.value
        }

        Log.w(TAG, "Failed to extract size for topic $topicId")
        return "Unknown"
    }

    /**
     * Extract title with fallback and cleaning.
     *
     * @param row Topic row element
     * @return Title or null if not found
     */
    fun extractTitle(row: Element): String? {
        // Strategy 1: a.torTopic
        val titleElement =
            row.selectFirst("a.torTopic, a.torTopic.tt-text")
        if (titleElement != null) {
            val title = titleElement.text()
            if (title.isNotBlank()) return title
        }

        // Strategy 2: Any link with viewtopic.php
        val topicLink = row.selectFirst("a[href*='viewtopic.php?t=']")
        if (topicLink != null) {
            val title = topicLink.text()
            if (title.isNotBlank()) return title
        }

        // Strategy 3: First non-empty link in row
        val anyLink = row.select("a").firstOrNull { it.text().isNotBlank() }
        if (anyLink != null) {
            val title = anyLink.text()
            if (title.length > 3) return title // Avoid single-char links
        }

        return null
    }

    /**
     * Extract number from element (handles b tag and direct text).
     */
    private fun extractNumberFromElement(element: Element?): Int? {
        if (element == null) return null

        // Try b tag
        val bTag = element.selectFirst("b")
        if (bTag != null) {
            return bTag.text().trim().toIntOrNull()
        }

        // Try direct text
        val text =
            element
                .text()
                .trim()
                .replace(Regex("""^(Сиды|Личи)[:\s]*"""), "") // Remove prefixes
        return text.toIntOrNull()
    }

    /**
     * Validate if size string looks reasonable.
     *
     * Valid examples: "1.5 GB", "500 MB", "10 KB"
     */
    private fun isValidSize(size: String): Boolean {
        if (size.isBlank()) return false

        // Check for size pattern (number + unit)
        val sizePattern = Regex("""^\d+\.?\d*\s*[KMGT]B$""", RegexOption.IGNORE_CASE)
        return sizePattern.matches(size) ||
            size.contains("MB", ignoreCase = true) ||
            size.contains("GB", ignoreCase = true) ||
            size.contains("KB", ignoreCase = true)
    }
}
