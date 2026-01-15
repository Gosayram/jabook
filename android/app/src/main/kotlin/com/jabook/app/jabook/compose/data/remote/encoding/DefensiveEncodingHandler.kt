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

/**
 * Defensive encoding handler for RuTracker HTML responses.
 *
 * Handles the encoding hell:
 * - Windows-1251 (CP1251) - RuTracker default
 * - UTF-8 - modern standard
 * - Latin-1 (ISO-8859-1) - emergency fallback
 * - BOM markers
 * - Mojibake detection
 * - Double encoding recovery
 *
 * Uses cascading decode strategy with multiple validation checks.
 *
 * Reference: Flutter implementation parseSearchResults() lines 203-443
 */
@Singleton
public class DefensiveEncodingHandler
    @Inject
    constructor(
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("DefensiveEncodingHandler")

        public companion object {
            // Supported charsets for RuTracker
            private val CP1251 = Charset.forName("windows-1251")
            private val UTF8 = Charsets.UTF_8
            private val LATIN1 = Charsets.ISO_8859_1

            // Mojibake patterns - typical artifacts of incorrect Windows-1251 → UTF-8 decoding
            // These appear when Cyrillic text is decoded with wrong encoding
            private val MOJIBAKE_PATTERNS =
                listOf(
                    "Рђ", // А decoded as UTF-8
                    "Р СЊ", // Ь decoded as UTF-8
                    "вҐ", // Typical pattern
                    "в„–", // № symbol corrupted
                    "Р°", // а decoded as UTF-8
                    "РІ", // в decoded as UTF-8
                    "Р", // Common mojibake prefix
                    "�", // Unicode replacement character
                )
        }

        /**
         * Decode bytes with cascading strategy.
         *
         * Decoding strategy:
         * 1. Remove BOM markers if present
         * 2. Try encoding from Content-Type header
         * 3. Statistical detection based on byte patterns
         * 4. Fallback chain: CP1251 → UTF-8 → Latin-1
         * 5. Validate result (check for mojibake, HTML structure)
         *
         * @param bytes Raw response bytes
         * @param contentType Content-Type header value (may contain charset)
         * @return DecodingResult with decoded text and metadata
         */
        public fun decode(
            bytes: ByteArray,
            contentType: String? = null,
        ): DecodingResult {
            val startTime = System.currentTimeMillis()

            // Step 1: Remove BOM if present
            val cleanBytes = removeBOM(bytes)

            logger.d {
                "Decoding ${cleanBytes.size} bytes (original: ${bytes.size}), " +
                    "contentType: ${contentType ?: "null"}"
            }

            // Step 2: Try encoding from Content-Type header
            val headerEncoding = extractCharsetFromContentType(contentType)
            if (headerEncoding != null) {
                val result = tryDecode(cleanBytes, headerEncoding)
                if (result.isValid && !result.hasMojibake) {
                    val duration = System.currentTimeMillis() - startTime
                    logger.i {
                        "✅ Decoded with header encoding '$headerEncoding' " +
                            "(${result.text.length} chars, ${duration}ms)"
                    }
                    return result
                } else {
                    logger.w {
                        "⚠️ Header encoding '$headerEncoding' produced invalid/mojibake result, " +
                            "trying alternatives"
                    }
                }
            }

            // Step 3: Statistical detection (for Cyrillic)
            val detectedEncoding = detectEncodingByStatistics(cleanBytes)
            if (detectedEncoding != null) {
                val result = tryDecode(cleanBytes, detectedEncoding)
                if (result.isValid && !result.hasMojibake) {
                    val duration = System.currentTimeMillis() - startTime
                    logger.i {
                        "✅ Decoded with detected encoding '${detectedEncoding.name()}' " +
                            "(${result.text.length} chars, ${duration}ms)"
                    }
                    return result
                }
            }

            // Step 4: Fallback chain - try all charsets
            for (charset in listOf(CP1251, UTF8, LATIN1)) {
                val result = tryDecode(cleanBytes, charset)
                if (result.isValid && !result.hasMojibake) {
                    val duration = System.currentTimeMillis() - startTime
                    logger.i {
                        "✅ Decoded with fallback '${charset.name()}' " +
                            "(${result.text.length} chars, ${duration}ms)"
                    }
                    return result
                }
            }

            // Step 5: Emergency fallback - return CP1251 even if has issues
            // This ensures we always return something, even if imperfect
            val emergencyResult =
                try {
                    val text = String(cleanBytes, CP1251)
                    val hasMojibake = detectMojibake(text)
                    DecodingResult.lowConfidence(
                        text = text,
                        encoding = "windows-1251",
                        hasMojibake = hasMojibake,
                    )
                } catch (e: Exception) {
                    logger.e({ "❌ Emergency CP1251 fallback failed" }, e)
                    DecodingResult.invalid()
                }

            val duration = System.currentTimeMillis() - startTime
            logger.w {
                "⚠️ All decoding strategies failed, using emergency fallback " +
                    "(confidence: ${emergencyResult.confidence}, ${duration}ms)"
            }

            return emergencyResult
        }

        /**
         * Try to decode bytes with given charset and validate result.
         *
         * @param bytes Bytes to decode
         * @param charset Charset to use
         * @return DecodingResult with validation flags
         */
        private fun tryDecode(
            bytes: ByteArray,
            charset: Charset,
        ): DecodingResult =
            try {
                val text = String(bytes, charset)

                // Validation checks
                val hasMojibake = detectMojibake(text)
                val hasHtmlStructure =
                    text.contains("<html", ignoreCase = true) ||
                        text.contains("<body", ignoreCase = true) ||
                        text.contains("<!doctype", ignoreCase = true)

                val isValid = hasHtmlStructure && text.isNotBlank()
                val confidence =
                    when {
                        !isValid -> 0.0f
                        hasMojibake -> 0.3f
                        else -> 0.9f
                    }

                DecodingResult(
                    text = text,
                    encoding = charset.name(),
                    confidence = confidence,
                    hasMojibake = hasMojibake,
                    isValid = isValid,
                )
            } catch (e: Exception) {
                logger.e({ "Failed to decode with ${charset.name()}" }, e)
                DecodingResult.invalid()
            }

        /**
         * Remove BOM (Byte Order Mark) from bytes if present.
         *
         * Supports:
         * - UTF-8 BOM: EF BB BF
         * - UTF-16 BE BOM: FE FF
         * - UTF-16 LE BOM: FF FE
         *
         * @param bytes Original bytes
         * @return Bytes with BOM removed (or original if no BOM)
         */
        private fun removeBOM(bytes: ByteArray): ByteArray {
            // UTF-8 BOM: EF BB BF
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                logger.d { "Removed UTF-8 BOM" }
                return bytes.copyOfRange(3, bytes.size)
            }

            // UTF-16 BE BOM: FE FF
            if (bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte()
            ) {
                logger.d { "Removed UTF-16 BE BOM" }
                return bytes.copyOfRange(2, bytes.size)
            }

            // UTF-16 LE BOM: FF FE
            if (bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte()
            ) {
                logger.d { "Removed UTF-16 LE BOM" }
                return bytes.copyOfRange(2, bytes.size)
            }

            return bytes
        }

        /**
         * Detect mojibake patterns in decoded text.
         *
         * Mojibake = garbled/corrupted text from wrong encoding.
         * Common when Cyrillic Windows-1251 is decoded as UTF-8.
         *
         * @param text Decoded text to check
         * @return True if mojibake patterns detected
         */
        private fun detectMojibake(text: String): Boolean {
            val hasMojibake = MOJIBAKE_PATTERNS.any { pattern -> text.contains(pattern) }

            if (hasMojibake) {
                val foundPatterns =
                    MOJIBAKE_PATTERNS.filter { pattern -> text.contains(pattern) }
                logger.w {
                    "🔍 Mojibake detected! Found patterns: $foundPatterns"
                }
            }

            return hasMojibake
        }

        /**
         * Extract charset from Content-Type header.
         *
         * Examples:
         * - "text/html; charset=windows-1251"
         * - "text/html; charset=utf-8"
         *
         * @param contentType Content-Type header value
         * @return Charset if found and supported, null otherwise
         */
        private fun extractCharsetFromContentType(contentType: String?): Charset? {
            if (contentType == null) return null

            val charsetMatch =
                Regex("""charset=([^;\s]+)""", RegexOption.IGNORE_CASE)
                    .find(contentType)
                    ?: return null

            val charsetName = charsetMatch.groupValues[1].lowercase()

            return when {
                charsetName.contains("windows-1251") || charsetName.contains("cp1251") -> CP1251
                charsetName.contains("utf-8") || charsetName.contains("utf8") -> UTF8
                charsetName.contains("iso-8859-1") || charsetName.contains("latin1") -> LATIN1
                else -> {
                    logger.d { "Unknown charset in Content-Type: $charsetName" }
                    null
                }
            }
        }

        /**
         * Detect encoding by statistical analysis of byte patterns.
         *
         * For Cyrillic text:
         * - Windows-1251: bytes 0xC0-0xFF represent Cyrillic А-Я, а-я
         * - UTF-8: Cyrillic uses multi-byte sequences (0xD0-0xD1 prefixes)
         *
         * @param bytes Bytes to analyze
         * @return Detected charset or null if uncertain
         */
        private fun detectEncodingByStatistics(bytes: ByteArray): Charset? {
            if (bytes.isEmpty()) return null

            var cp1251CyrillicCount = 0
            var utf8CyrillicCount = 0
            var i = 0

            while (i < bytes.size) {
                val byte = bytes[i].toInt() and 0xFF

                // Check for Windows-1251 Cyrillic range (0xC0-0xFF)
                if (byte in 0xC0..0xFF) {
                    cp1251CyrillicCount++
                }

                // Check for UTF-8 Cyrillic sequences (0xD0 0x80-0xBF or 0xD1 0x80-0xBF)
                if (i < bytes.size - 1) {
                    val nextByte = bytes[i + 1].toInt() and 0xFF
                    if ((byte == 0xD0 || byte == 0xD1) && nextByte in 0x80..0xBF) {
                        utf8CyrillicCount++
                        i++ // Skip next byte as it's part of multi-byte sequence
                    }
                }

                i++
            }

            logger.d {
                "Statistical detection: CP1251 Cyrillic=$cp1251CyrillicCount, " +
                    "UTF-8 Cyrillic=$utf8CyrillicCount"
            }

            // Heuristic: if significant Cyrillic detected, choose based on counts
            return when {
                cp1251CyrillicCount > bytes.size / 10 && cp1251CyrillicCount > utf8CyrillicCount * 2 -> {
                    logger.d { "Detected: Windows-1251 (based on byte patterns)" }
                    CP1251
                }
                utf8CyrillicCount > bytes.size / 20 -> {
                    logger.d { "Detected: UTF-8 (based on multi-byte sequences)" }
                    UTF8
                }
                else -> {
                    logger.d { "Statistical detection inconclusive" }
                    null
                }
            }
        }
    }
