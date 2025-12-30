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

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URI

/**
 * Safe parsing extensions for Jsoup Elements.
 *
 * Based on Flow project analysis - provides safe parsing functions
 * that avoid NPE and provide sensible defaults.
 *
 * Usage:
 * ```kotlin
 * val title = element.select(".title").toStr()
 * val id = element.select(".id").toIntOrNull() ?: 0
 * val topicId = element.queryParamOrNull("t")
 * ```
 */

/**
 * Get text from Element, or empty string if null.
 */
internal fun Element?.toStr(): String = this?.text().orEmpty()

/**
 * Get text from Element as Int, or default value if null/invalid.
 */
internal fun Element?.toInt(default: Int = 0): Int = this?.text()?.toIntOrNull() ?: default

/**
 * Get text from Element as Int, or null if invalid.
 */
internal fun Element?.toIntOrNull(): Int? = this?.text()?.toIntOrNull()

/**
 * Get URL from Element's href attribute, or null if not found.
 */
internal fun Element?.urlOrNull(): String? = this?.attr("href")

/**
 * Get URL from Element's href attribute, or throw if not found.
 */
internal fun Element?.url(): String = requireNotNull(urlOrNull()) { "url not found in $this" }

/**
 * Get query parameter from Element's href URL, or null if not found.
 */
internal fun Element?.queryParamOrNull(key: String): String? = urlOrNull()?.let { parseUrl(it) }?.get(key)?.firstOrNull()

/**
 * Get query parameter from Element's href URL, or throw if not found.
 */
internal fun Element?.queryParam(key: String): String =
    requireNotNull(queryParamOrNull(key)) {
        "query param '$key' not found in $this"
    }

/**
 * Get text from Elements, or empty string if null/empty.
 */
internal fun Elements?.toStr(): String = this?.text().orEmpty()

/**
 * Get text from Elements as Int, or default value if null/invalid.
 */
internal fun Elements?.toInt(default: Int = 0): Int = this?.text()?.toIntOrNull() ?: default

/**
 * Get text from Elements as Int, or null if invalid.
 */
internal fun Elements?.toIntOrNull(): Int? = this?.text()?.toIntOrNull()

/**
 * Get URL from Elements' href attribute, or null if not found.
 */
internal fun Elements?.urlOrNull(): String? = this?.attr("href")

/**
 * Get URL from Elements' href attribute, or throw if not found.
 */
internal fun Elements?.url(): String = requireNotNull(urlOrNull()) { "url not found in $this" }

/**
 * Get query parameter from Elements' href URL, or null if not found.
 */
internal fun Elements?.queryParamOrNull(key: String): String? = urlOrNull()?.let { parseUrl(it) }?.get(key)?.firstOrNull()

/**
 * Get query parameter from Elements' href URL, or throw if not found.
 */
internal fun Elements?.queryParam(key: String): String =
    requireNotNull(queryParamOrNull(key)) {
        "query param '$key' not found in $this"
    }

/**
 * Parse URL query parameters.
 *
 * @param url URL string to parse
 * @return Map of query parameters
 */
private fun parseUrl(url: String): Map<String, List<String>> =
    runCatching {
        URI
            .create(url)
            .query
            ?.split("&")
            ?.associate { queryParam ->
                val split = queryParam.split("=")
                split[0] to split.drop(1)
            }
            ?: emptyMap()
    }.getOrDefault(emptyMap())
