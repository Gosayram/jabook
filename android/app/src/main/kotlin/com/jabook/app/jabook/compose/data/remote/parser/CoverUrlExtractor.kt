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
import org.jsoup.nodes.Element
import javax.inject.Inject

/**
 * Smart cover URL extractor with priority-based fallback strategies.
 *
 * Implements 5-level extraction strategy to handle various
 * cover image formats on RuTracker pages.
 *
 * No domain validation - accepts any image with valid extension
 * (.jpg, .jpeg, .png, .webp, .gif) and HTTP(S) scheme.
 */
class CoverUrlExtractor
    @Inject
    constructor(
        private val mirrorManager: MirrorManager,
    ) {
        companion object {
            private const val TAG = "CoverUrlExtractor"

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
         * Priority order (based on Flow project analysis):
         * 1. img.postImg.postImgAligned.img-right with title (MOST RELIABLE - как в Flow)
         * 2. var.postImg with title (fallback для старых форматов)
         * 3. img from static.rutracker or fastpic
         * 4. img with data-src (lazy loading)
         * 5. img with srcset
         * 6. First valid img (last resort, with filtering)
         *
         * No domain validation - accepts any image with valid extension (.jpg, .jpeg, .png, .webp, .gif)
         * and HTTP(S) scheme.
         *
         * @param container Element to search within (e.g., post body)
         * @return Cover URL or null if not found
         */
        fun extract(container: Element): String? {
            // Priority 1: img.postImg.postImgAligned.img-right with title (как в Flow - РАБОТАЕТ!)
            // This is the correct selector used in Flow project that successfully loads covers
            val imgElement =
                container.selectFirst(".postImg.postImgAligned.img-right")
                    ?: container.selectFirst(".postImg.img-right")
                    ?: container.selectFirst("img.postImg[title]")
                    ?: container.selectFirst("img.postImgAligned[title]")

            imgElement?.let { element ->
                // Try title attribute first (most common in Flow)
                val url =
                    element.attr("title").takeIf { it.isNotBlank() }
                        ?: element.absUrl("src") // Use absUrl() for proper absolute URL resolution

                if (isValidImageUrl(url)) {
                    Log.d(TAG, "Cover found via img.postImg.postImgAligned.img-right: $url")
                    return normalizeUrl(url)
                } else {
                    Log.d(TAG, "Cover URL from img.postImg is invalid or blank: '$url'")
                }
            }

            // Priority 2: var.postImg with title (fallback для старых форматов)
            // Try multiple selectors to catch all variations
            val varElement =
                container.selectFirst("var.postImg[title]")
                    ?: container.selectFirst("var.postImgAligned[title]")
                    ?: container.selectFirst("var[class*='postImg'][title]")
                    ?: container.selectFirst("var[class*='postImgAligned'][title]")

            varElement?.let { element ->
                val url = element.attr("title")
                if (url.isNotBlank() && isValidImageUrl(url)) {
                    Log.d(TAG, "Cover found via var.postImg (fallback): $url")
                    return normalizeUrl(url)
                } else {
                    Log.d(TAG, "Cover URL from var.postImg is invalid or blank: '$url'")
                }
            }

            // Priority 3: img from static.rutracker or fastpic (Highly reliable)
            // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
            container
                .selectFirst("img[src*='static.rutracker'], img[src*='fastpic'], img[src*='i.rutracker']")
                ?.let { imgElement ->
                    val url = imgElement.absUrl("src")
                    if (isValidImageUrl(url) && !isIconOrSmile(url)) {
                        Log.d(TAG, "Cover found via static.rutracker/fastpic: $url")
                        return normalizeUrl(url)
                    }
                }

            // Priority 4: img with data-src (Lazy loading)
            container.selectFirst("img[data-src]")?.let { imgElement ->
                // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
                val url = imgElement.absUrl("data-src")
                if (isValidImageUrl(url) && !isIconOrSmile(url)) {
                    Log.d(TAG, "Cover found via data-src: $url")
                    return normalizeUrl(url)
                }
            }

            // Priority 5: img with srcset
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

            // Priority 6: First valid img (last resort with strict filtering)
            // Use absUrl() for proper absolute URL resolution (requires baseUri in parse())
            container.select("img[src]").forEach { imgElement ->
                val url = imgElement.absUrl("src")
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
         * - Valid image extension (.jpg, .jpeg, .png, .webp, .gif)
         * - HTTP(S) scheme or protocol-relative URL
         *
         * No domain validation - accepts images from any domain with valid extension.
         */
        private fun isValidImageUrl(url: String): Boolean {
            if (url.isBlank()) return false

            // Check for valid image extensions
            val validExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
            val hasValidExt = validExtensions.any { ext -> url.lowercase().contains(ext) }

            if (!hasValidExt) return false

            // Accept any URL with HTTP(S) scheme or protocol-relative URL
            val hasHttpScheme = url.startsWith("http://") || url.startsWith("https://")
            val isProtocolRelative = url.startsWith("//")

            return hasHttpScheme || isProtocolRelative
        }

        /**
         * Check if URL is likely an icon or smiley.
         */
        private fun isIconOrSmile(url: String): Boolean = ICON_PATTERNS.any { pattern -> url.contains(pattern, ignoreCase = true) }

        /**
         * Normalize URL to absolute form using CDN when possible.
         *
         * Handles:
         * - Protocol-relative URLs (//static.rutracker.org/...) - как в Flow
         * - Relative URLs (/forum/...)
         * - Already absolute URLs
         * - CDN normalization: replaces static.rutracker.* with static.rutracker.cc (always available)
         *
         * Note: This method should be called after absUrl() when possible,
         * as absUrl() handles relative URLs better when baseUri is set in Jsoup.parse().
         */
        fun normalizeUrl(url: String): String {
            // If URL is already absolute and valid, just normalize CDN domain
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // Replace static.rutracker.* domains with static.rutracker.cc (CDN is always available)
                // This ensures images load even if main mirror is blocked
                return url.replace(
                    Regex("https?://static\\.rutracker\\.(org|net|me|nl)"),
                    "https://static.rutracker.cc",
                )
            }

            val baseUrl = mirrorManager.getBaseUrl()
            val normalized =
                when {
                    url.startsWith("//") -> {
                        // Protocol-relative URL - как в Flow
                        // Check if it's static.rutracker CDN
                        if (url.contains("static.rutracker.", ignoreCase = true)) {
                            // Replace domain with static.rutracker.cc (CDN is always available)
                            "https:" +
                                url.replace(
                                    Regex("//static\\.rutracker\\.(org|net|me|nl)"),
                                    "//static.rutracker.cc",
                                )
                        } else {
                            "https:$url"
                        }
                    }
                    url.startsWith("/avatars/") || url.startsWith("/tt/") || url.startsWith("/sm/") -> {
                        // Static content paths should use static.rutracker.cc CDN
                        "https://static.rutracker.cc$url"
                    }
                    url.startsWith("/") -> "$baseUrl$url"
                    else -> "$baseUrl/$url"
                }

            // Final CDN normalization (in case baseUrl contained static.rutracker.*)
            return normalized.replace(
                Regex("https?://static\\.rutracker\\.(org|net|me|nl)"),
                "https://static.rutracker.cc",
            )
        }
    }
