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
         * Strategy (matching Flutter implementation exactly):
         * 1. Check Content-Type header for charset
         * 2. If windows-1251/cp1251/1251 -> use Windows-1251
         * 3. If utf-8/utf8 -> use UTF-8
         * 4. If not specified -> use Windows-1251 (RuTracker default)
         *
         * Note: No validation is performed - we trust the decoding result,
         * just like the Flutter implementation does. The parser will handle any issues.
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

            Log.w(TAG, "=== DECODING START ===")
            Log.w(TAG, "Bytes size: ${bytes.size}")
            Log.w(TAG, "Content-Type: $contentType")

            // Extract charset from Content-Type header
            val detectedEncoding = extractCharsetFromContentType(contentType)
            Log.w(TAG, "Detected encoding from header: $detectedEncoding")

            // Preview first 100 bytes as hex for debugging
            val hexPreview = bytes.take(100).joinToString(" ") { "%02x".format(it) }
            Log.w(TAG, "First 100 bytes (hex): $hexPreview")

            val result =
                when {
                    detectedEncoding != null && isWindows1251(detectedEncoding) -> {
                        Log.w(TAG, "Using Windows-1251 (from header)")
                        String(bytes, CP1251)
                    }
                    detectedEncoding != null && isUtf8(detectedEncoding) -> {
                        Log.w(TAG, "Using UTF-8 (from header)")
                        String(bytes, UTF8)
                    }
                    else -> {
                        // No encoding specified - use Windows-1251 (RuTracker default)
                        Log.w(TAG, "Using Windows-1251 (default, no header)")
                        String(bytes, CP1251)
                    }
                }

            Log.w(TAG, "Decoded length: ${result.length}")
            Log.w(TAG, "First 200 chars: ${result.take(200)}")
            Log.w(TAG, "=== DECODING END ===")

            return result
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
