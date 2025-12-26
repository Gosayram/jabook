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

package com.jabook.app.jabook.compose.data.remote.encoding

import android.util.Log
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple decoder for RuTracker responses.
 *
 * Based on Flutter implementation: tries Windows-1251 first (RuTracker default),
 * then falls back to UTF-8 if needed.
 *
 * This is a minimal solution that works reliably for RuTracker's encoding.
 * For books in library, use EncodingDetector instead.
 */
@Singleton
class RutrackerSimpleDecoder
    @Inject
    constructor() {
        companion object {
            private const val TAG = "RutrackerSimpleDecoder"
            private val CP1251 = Charset.forName("windows-1251")
            private val UTF8 = Charsets.UTF_8
        }

        /**
         * Decode bytes from RuTracker response.
         *
         * Strategy (matching Flutter implementation):
         * 1. Check Content-Type header for charset
         * 2. If windows-1251/cp1251/1251 -> try Windows-1251, fallback to UTF-8
         * 3. If utf-8/utf8 -> try UTF-8, fallback to Windows-1251
         * 4. If not specified -> try Windows-1251 first (RuTracker default), then UTF-8
         *
         * @param bytes Raw response bytes
         * @param contentType Optional Content-Type header value
         * @return Decoded string
         */
        fun decode(
            bytes: ByteArray,
            contentType: String? = null,
        ): String {
            if (bytes.isEmpty()) {
                Log.w(TAG, "Empty bytes provided")
                return ""
            }

            // Extract charset from Content-Type header
            val detectedEncoding = extractCharsetFromContentType(contentType)

            return when {
                detectedEncoding != null && isWindows1251(detectedEncoding) -> {
                    // Try Windows-1251 first, fallback to UTF-8
                    val decoded1251 = String(bytes, CP1251)
                    if (isValidDecoding(decoded1251)) {
                        Log.d(TAG, "Successfully decoded with Windows-1251 (from header)")
                        decoded1251
                    } else {
                        Log.w(TAG, "Windows-1251 decoding produced invalid result, trying UTF-8")
                        val decodedUtf8 = String(bytes, UTF8)
                        if (isValidDecoding(decodedUtf8)) {
                            Log.d(TAG, "Successfully decoded with UTF-8 (fallback)")
                            decodedUtf8
                        } else {
                            Log.e(TAG, "Both Windows-1251 and UTF-8 decoding produced invalid results, using Windows-1251")
                            decoded1251
                        }
                    }
                }
                detectedEncoding != null && isUtf8(detectedEncoding) -> {
                    // Try UTF-8 first, fallback to Windows-1251 (RuTracker sometimes lies)
                    val decodedUtf8 = String(bytes, UTF8)
                    if (isValidDecoding(decodedUtf8)) {
                        Log.d(TAG, "Successfully decoded with UTF-8 (from header)")
                        decodedUtf8
                    } else {
                        Log.w(TAG, "UTF-8 decoding produced invalid result, trying Windows-1251")
                        val decoded1251 = String(bytes, CP1251)
                        if (isValidDecoding(decoded1251)) {
                            Log.d(TAG, "Successfully decoded with Windows-1251 (fallback)")
                            decoded1251
                        } else {
                            Log.e(TAG, "Both UTF-8 and Windows-1251 decoding produced invalid results, using UTF-8")
                            decodedUtf8
                        }
                    }
                }
                else -> {
                    // No encoding specified - try Windows-1251 first (RuTracker default)
                    val decoded1251 = String(bytes, CP1251)
                    if (isValidDecoding(decoded1251)) {
                        Log.d(TAG, "Successfully decoded with Windows-1251 (default)")
                        decoded1251
                    } else {
                        Log.w(TAG, "Windows-1251 decoding produced invalid result, trying UTF-8")
                        val decodedUtf8 = String(bytes, UTF8)
                        if (isValidDecoding(decodedUtf8)) {
                            Log.d(TAG, "Successfully decoded with UTF-8 (fallback)")
                            decodedUtf8
                        } else {
                            Log.e(TAG, "Both Windows-1251 and UTF-8 decoding produced invalid results, using Windows-1251")
                            decoded1251
                        }
                    }
                }
            }
        }

        /**
         * Check if decoded text is valid (not corrupted/mojibake).
         *
         * Valid HTML should contain:
         * - HTML structure tags (<html>, <body>, etc.)
         * - No replacement characters ()
         * - No obvious mojibake patterns
         * - Common RuTracker page elements
         */
        private fun isValidDecoding(text: String): Boolean {
            if (text.isEmpty()) return false

            // Check for replacement characters (indicates invalid encoding)
            if (text.contains('\uFFFD')) {
                Log.d(TAG, "Invalid: contains replacement characters")
                return false
            }

            // Check for obvious mojibake patterns (common when Windows-1251 is decoded as UTF-8)
            // These patterns appear when Cyrillic text is incorrectly decoded
            val mojibakePatterns =
                listOf(
                    "Рђ",
                    "Р СЊ",
                    "вҐ",
                    "в„–",
                    "Р°",
                    "РІ",
                    "Р", // Common mojibake
                )
            if (mojibakePatterns.any { text.contains(it) }) {
                Log.d(TAG, "Invalid: contains mojibake patterns")
                return false
            }

            // Check for HTML structure (RuTracker responses should be HTML)
            val hasHtmlStructure =
                text.contains("<html", ignoreCase = true) ||
                    text.contains("<body", ignoreCase = true) ||
                    text.contains("<!doctype", ignoreCase = true)

            // Check for common RuTracker page elements
            val hasRutrackerContent =
                text.contains("rutracker", ignoreCase = true) ||
                    text.contains("форум", ignoreCase = true) ||
                    text.contains("поиск", ignoreCase = true) ||
                    text.contains("tracker.php", ignoreCase = true) ||
                    text.contains("viewtopic.php", ignoreCase = true)

            // Valid if has HTML structure AND (RuTracker content OR reasonable Cyrillic text)
            if (!hasHtmlStructure) {
                Log.d(TAG, "Invalid: no HTML structure found")
                return false
            }

            // If has HTML structure, check for valid content
            val isValid = hasRutrackerContent || containsValidCyrillic(text)
            if (!isValid) {
                Log.d(TAG, "Invalid: HTML structure found but no valid content")
            }
            return isValid
        }

        /**
         * Check if text contains valid Cyrillic characters (not mojibake).
         * Valid Cyrillic should have proper word boundaries and common Russian words.
         */
        private fun containsValidCyrillic(text: String): Boolean {
            // Sample first 1000 characters for performance
            val sample = text.take(1000)

            // Check for Cyrillic characters
            val hasCyrillic = sample.any { it in '\u0400'..'\u04FF' }
            if (!hasCyrillic) return false

            // Check for common Russian words/patterns that indicate valid decoding
            val commonWords =
                listOf(
                    "и",
                    "в",
                    "на",
                    "с",
                    "по",
                    "для",
                    "это",
                    "что",
                    "как",
                    "автор",
                    "книга",
                    "размер",
                    "скачать",
                    "торрент",
                )

            val lowerSample = sample.lowercase()
            return commonWords.any { lowerSample.contains(it) }
        }

        /**
         * Extract charset from Content-Type header.
         *
         * Examples:
         * - "text/html; charset=windows-1251"
         * - "text/html; charset=utf-8"
         *
         * @param contentType Content-Type header value
         * @return Charset name if found, null otherwise
         */
        private fun extractCharsetFromContentType(contentType: String?): String? {
            if (contentType == null) return null

            val charsetMatch =
                Regex("""charset=([^;\s]+)""", RegexOption.IGNORE_CASE)
                    .find(contentType)
                    ?: return null

            return charsetMatch.groupValues[1].lowercase()
        }

        /**
         * Check if encoding is Windows-1251.
         */
        private fun isWindows1251(encoding: String): Boolean =
            encoding.contains("windows-1251") ||
                encoding.contains("cp1251") ||
                encoding.contains("1251")

        /**
         * Check if encoding is UTF-8.
         */
        private fun isUtf8(encoding: String): Boolean = encoding.contains("utf-8") || encoding.contains("utf8")
    }
