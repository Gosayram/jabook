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
import javax.inject.Inject

/**
 * Smart cover URL extractor with priority-based fallback strategies.
 *
 * Implements 6-level extraction strategy to handle various
 * cover image formats on RuTracker pages.
 */
class CoverUrlExtractor
    @Inject
    constructor() {
        companion object {
            private const val TAG = "CoverUrlExtractor"

            // Valid image domains (allowlist)
            private val VALID_DOMAINS =
                listOf(
                    "fastpic",
                    "rutracker",
                    "i.rutracker",
                    "static.t-ru.org",
                    "imgur",
                    "imageban",
                    "radikal",
                )

            // Icon/smiley blacklist patterns
            private val ICON_PATTERNS =
                listOf(
                    "icon",
                    "smile",
                    "emoji",
                    "button",
                    "avatar",
                    "rank",
                    "/images/",
                    "16x16",
                    "32x32",
                    "48x48",
                )
        }

        /**
         * Extract cover URL with 6 priority-based strategies.
         *
         * Priority order:
         * 1. var.postImg with title (most reliable)
         * 2. img from static.rutracker or fastpic
         * 3. img with data-src (lazy loading)
         * 4. img with srcset
         * 5. Any img from valid domains
         * 6. First img (last resort, with filtering)
         *
         * @param container Element to search within (e.g., post body)
         * @return Cover URL or null if not found
         */
        fun extract(container: Element): String? {
            // Priority 1: var.postImg with title attribute (Gold standard)
            container.selectFirst("var.postImg[title], var.postImgAligned[title]")?.let { varElement ->
                val url = varElement.attr("title")
                if (isValidImageUrl(url)) {
                    Log.d(TAG, "Cover found via var.postImg: $url")
                    return normalizeUrl(url)
                }
            }

            // Priority 2: img from static.rutracker or fastpic (Highly reliable)
            container
                .selectFirst("img[src*='static.rutracker'], img[src*='fastpic'], img[src*='i.rutracker']")
                ?.let { imgElement ->
                    val url = imgElement.attr("abs:src")
                    if (isValidImageUrl(url) && !isIconOrSmile(url)) {
                        Log.d(TAG, "Cover found via static.rutracker/fastpic: $url")
                        return normalizeUrl(url)
                    }
                }

            // Priority 3: img with data-src (Lazy loading)
            container.selectFirst("img[data-src]")?.let { imgElement ->
                val url = imgElement.attr("data-src")
                if (isValidImageUrl(url) && !isIconOrSmile(url)) {
                    Log.d(TAG, "Cover found via data-src: $url")
                    return normalizeUrl(url)
                }
            }

            // Priority 4: img with srcset
            container.selectFirst("img[srcset]")?.let { imgElement ->
                val srcset = imgElement.attr("srcset")
                val firstUrl =
                    srcset
                        .split(",")
                        .firstOrNull()
                        ?.trim()
                        ?.split(" ")
                        ?.firstOrNull()
                if (firstUrl != null && isValidImageUrl(firstUrl) && !isIconOrSmile(firstUrl)) {
                    Log.d(TAG, "Cover found via srcset: $firstUrl")
                    return normalizeUrl(firstUrl)
                }
            }

            // Priority 5: Any img from valid domains
            for (domain in VALID_DOMAINS) {
                container.selectFirst("img[src*='$domain']")?.let { imgElement ->
                    val url = imgElement.attr("abs:src")
                    if (isValidImageUrl(url) && !isIconOrSmile(url)) {
                        Log.d(TAG, "Cover found via domain match ($domain): $url")
                        return normalizeUrl(url)
                    }
                }
            }

            // Priority 6: First valid img (last resort with strict filtering)
            container.select("img[src]").forEach { imgElement ->
                val url = imgElement.attr("abs:src")
                if (isValidImageUrl(url) && !isIconOrSmile(url)) {
                    // Additional check: image should be reasonably sized
                    val width = imgElement.attr("width").toIntOrNull() ?: 0
                    val height = imgElement.attr("height").toIntOrNull() ?: 0

                    // Assume cover images are at least 100x100 (unless size unknown)
                    if ((width == 0 && height == 0) || (width >= 100 && height >= 100)) {
                        Log.d(TAG, "Cover found via fallback img: $url")
                        return normalizeUrl(url)
                    }
                }
            }

            // No cover found - this is OK
            Log.d(TAG, "No cover URL found (this is acceptable)")
            return null
        }

        /**
         * Validate if URL is a valid image URL.
         *
         * Checks for:
         * - Valid image extension
         * - Valid domain or HTTP(S) scheme
         */
        private fun isValidImageUrl(url: String): Boolean {
            if (url.isBlank()) return false

            // Check for valid extensions
            val validExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
            val hasValidExt = validExtensions.any { ext -> url.lowercase().contains(ext) }

            if (!hasValidExt) return false

            // Check for valid domain or HTTP scheme
            val hasValidDomain = VALID_DOMAINS.any { domain -> url.contains(domain, ignoreCase = true) }
            val hasHttpScheme = url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")

            return hasValidDomain || hasHttpScheme
        }

        /**
         * Check if URL is likely an icon or smiley.
         */
        private fun isIconOrSmile(url: String): Boolean = ICON_PATTERNS.any { pattern -> url.contains(pattern, ignoreCase = true) }

        /**
         * Normalize URL to absolute form with HTTPS.
         *
         * Handles:
         * - Protocol-relative URLs (//static.rutracker.org/...)
         * - Relative URLs (/forum/...)
         * - Already absolute URLs
         */
        fun normalizeUrl(url: String): String =
            when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("//") -> "https:$url"
                url.startsWith("/") -> "https://rutracker.org$url"
                else -> "https://rutracker.org/$url"
            }
    }
