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
                    try {
                        val decoded = String(bytes, CP1251)
                        Log.d(TAG, "Successfully decoded with Windows-1251 (from header)")
                        decoded
                    } catch (e: Exception) {
                        Log.w(TAG, "Windows-1251 decoding failed, trying UTF-8", e)
                        try {
                            val decoded = String(bytes, UTF8)
                            Log.d(TAG, "Successfully decoded with UTF-8 (fallback)")
                            decoded
                        } catch (e2: Exception) {
                            Log.e(TAG, "Both Windows-1251 and UTF-8 decoding failed", e2)
                            // Emergency fallback to Windows-1251 even if it fails
                            String(bytes, CP1251)
                        }
                    }
                }
                detectedEncoding != null && isUtf8(detectedEncoding) -> {
                    // Try UTF-8 first, fallback to Windows-1251 (RuTracker sometimes lies)
                    try {
                        val decoded = String(bytes, UTF8)
                        Log.d(TAG, "Successfully decoded with UTF-8 (from header)")
                        decoded
                    } catch (e: Exception) {
                        Log.w(TAG, "UTF-8 decoding failed, trying Windows-1251", e)
                        try {
                            val decoded = String(bytes, CP1251)
                            Log.d(TAG, "Successfully decoded with Windows-1251 (fallback)")
                            decoded
                        } catch (e2: Exception) {
                            Log.e(TAG, "Both UTF-8 and Windows-1251 decoding failed", e2)
                            // Emergency fallback to UTF-8 even if it fails
                            String(bytes, UTF8)
                        }
                    }
                }
                else -> {
                    // No encoding specified - try Windows-1251 first (RuTracker default)
                    try {
                        val decoded = String(bytes, CP1251)
                        Log.d(TAG, "Successfully decoded with Windows-1251 (default)")
                        decoded
                    } catch (e: Exception) {
                        Log.w(TAG, "Windows-1251 decoding failed, trying UTF-8", e)
                        try {
                            val decoded = String(bytes, UTF8)
                            Log.d(TAG, "Successfully decoded with UTF-8 (fallback)")
                            decoded
                        } catch (e2: Exception) {
                            Log.e(TAG, "Both Windows-1251 and UTF-8 decoding failed", e2)
                            // Emergency fallback to Windows-1251 even if it fails
                            String(bytes, CP1251)
                        }
                    }
                }
            }
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
