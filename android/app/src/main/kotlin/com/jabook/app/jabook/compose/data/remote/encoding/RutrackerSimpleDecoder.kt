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

package com.jabook.app.jabook.compose.data.remote.encoding

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull

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
public class RutrackerSimpleDecoder
    @Inject
    constructor(
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("RutrackerSimpleDecoder")

        public companion object {
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
        public fun decode(
            bytes: ByteArray,
            contentType: String? = null,
        ): String {
            if (bytes.isEmpty()) {
                logger.w { "Empty bytes provided" }
                return ""
            }

            // Extract charset from Content-Type header (OkHttp parses parameters correctly)
            val detectedCharset = contentType?.toMediaTypeOrNull()?.charset()
            val detectedEncoding = detectedCharset?.name()

            val result =
                try {
                    when {
                        detectedCharset != null && detectedCharset.name().contains("1251") -> {
                            String(bytes, CP1251)
                        }
                        detectedCharset != null -> {
                            String(bytes, detectedCharset)
                        }
                        else -> {
                            // No encoding specified - use Windows-1251 (RuTracker default)
                            String(bytes, CP1251)
                        }
                    }
                } catch (e: Exception) {
                    // Log decoding error only when it occurs
                    logger.e(
                        {
                            "Decoding error: ${e.javaClass.simpleName}: ${e.message} (bytes: ${bytes.size}, encoding: $detectedEncoding)"
                        },
                        e,
                    )
                    // Fallback to UTF-8 if Windows-1251 fails
                    try {
                        String(bytes, UTF8)
                    } catch (e2: Exception) {
                        logger.e({ "UTF-8 fallback also failed: ${e2.message}" }, e2)
                        String(bytes, Charsets.ISO_8859_1) // Last resort
                    }
                }

            // Only log if result looks invalid (contains replacement characters or is suspiciously short)
            if (result.contains("\uFFFD") || (result.length < 10 && bytes.size > 100)) {
                logger.w {
                    "Decoding may have failed: result contains replacement chars or suspiciously short " +
                        "(result length: ${result.length}, bytes: ${bytes.size}, encoding: $detectedEncoding)"
                }
            }

            return result
        }
    }
