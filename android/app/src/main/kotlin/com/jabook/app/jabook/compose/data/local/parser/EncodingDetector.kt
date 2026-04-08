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

package com.jabook.app.jabook.compose.data.local.parser

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for detecting text encoding, particularly for Russian text formats.
 *
 * Supports:
 * - UTF-8 (default modern encoding)
 * - Windows-1251 (Windows Cyrillic)
 * - KOI8-R (Russian UNIX)
 * - CP866 (DOS Russian/Cyrillic)
 *
 * Uses Android ICU CharsetDetector on API 24+ for accurate detection,
 * falls back to heuristic-based detection on older devices.
 */
@Singleton
public class EncodingDetector
    @Inject
    constructor(
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("EncodingDetector")

        public companion object {
            // Supported encodings for Russian text
            // CRITICAL: Order matters! windows-1252 MUST come before windows-1251
            // to avoid false positives (1252 files being detected as 1251)
            private val RUSSIAN_CHARSETS =
                listOf(
                    "UTF-8", // Modern standard (highest priority)
                    "windows-1252", // Windows Western European (check BEFORE 1251!)
                    "windows-1251", // Windows Cyrillic (most common for Russian)
                    "KOI8-R", // Russian UNIX
                    "CP866", // DOS Russian
                    "ISO-8859-5", // Latin/Cyrillic
                    "UTF-16LE", // UTF-16 Little Endian
                    "UTF-16BE", // UTF-16 Big Endian
                    "UTF-16", // UTF-16 with BOM
                )
        }

        /**
         * Detect the encoding of the given text bytes.
         *
         * @param bytes Raw bytes of the text
         * @param hint Optional hint about expected encoding
         * @return Detected charset, defaults to UTF-8 if detection fails
         */
        public fun detectEncoding(
            bytes: ByteArray,
            hint: String? = null,
        ): Charset {
            if (bytes.isEmpty()) {
                return Charsets.UTF_8
            }

            // Check for BOMs (Byte Order Marks)
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                return Charsets.UTF_8
            }
            if (bytes.size >= 2) {
                if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                    return Charsets.UTF_16 // Use generic UTF-16 to consume BOM
                }
                if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                    return Charsets.UTF_16 // Use generic UTF-16 to consume BOM
                }
            }

            // Try hint first if provided
            hint?.let { hintCharset ->
                try {
                    val charset = Charset.forName(hintCharset)
                    val decoded = String(bytes, charset)
                    if (!decoded.contains('\uFFFD')) {
                        return charset
                    }
                } catch (e: Exception) {
                }
            }

            // Use heuristic-based detection
            return detectEncodingHeuristic(bytes)
        }

        /**
         * Heuristic-based encoding detection for Russian text.
         *
         * Checks for common byte patterns in different encodings.
         */
        private fun detectEncodingHeuristic(bytes: ByteArray): Charset {
            val sample = bytes.take(1000) // Sample first 1KB

            // Check if valid UTF-8
            if (isValidUtf8(sample.toByteArray())) {
                return Charsets.UTF_8
            }

            val win1251 = Charset.forName("windows-1251")
            val koi8r = Charset.forName("KOI8-R")

            val sWin = String(sample.toByteArray(), win1251)
            val sKoi = String(sample.toByteArray(), koi8r)

            val confWin = calculateConfidence(sWin)
            val confKoi = calculateConfidence(sKoi)

            if (confWin > 0.0 || confKoi > 0.0) {
                return if (confWin >= confKoi) win1251 else koi8r
            }

            // Default to UTF-8
            return Charsets.UTF_8
        }

        /**
         * Check if bytes form valid UTF-8 sequence.
         */
        private fun isValidUtf8(bytes: ByteArray): Boolean =
            try {
                val str = String(bytes, Charsets.UTF_8)
                // Check for replacement characters (indicates invalid UTF-8)
                !str.contains('\uFFFD')
            } catch (e: Exception) {
                false
            }

        /**
         * Attempt to decode string from bytes, trying multiple encodings.
         *
         * @param bytes Raw bytes
         * @param declaredEncoding Optional declared encoding to try first
         * @return Decoded string and detected encoding name
         */
        public fun decodeString(
            bytes: ByteArray,
            declaredEncoding: String? = null,
        ): Pair<String, String> {
            // Try declared encoding first
            declaredEncoding?.let { encoding ->
                try {
                    val charset = Charset.forName(encoding)
                    val decoded = String(bytes, charset)
                    if (!decoded.contains('\uFFFD')) {
                        // Strip BOM if present
                        val clean = decoded.replace("\uFEFF", "")
                        return Pair(clean, encoding)
                    }
                } catch (e: Exception) {
                }
            }

            // Auto-detect encoding
            val detectedCharset = detectEncoding(bytes, declaredEncoding)
            val decoded = String(bytes, detectedCharset)
            val clean = decoded.replace("\uFEFF", "")

            logger.w { "Decoded string with encoding: ${detectedCharset.name()}" }
            return Pair(clean, detectedCharset.name())
        }

        /**
         * Detects garbled patterns that indicate failed conversion.
         * Mixed scripts (Cyrillic + CJK/Greek) indicate corruption.
         */
        private fun containsGarbledPatterns(text: String): Boolean {
            val hasCyrillic = text.any { it in '\u0400'..'\u04FF' }
            val hasCJK = text.any { it in '\u4E00'..'\u9FFF' }
            val hasGreek = text.any { it in '\u0370'..'\u03FF' }
            val hasArabic = text.any { it in '\u0600'..'\u06FF' }

            // Cyrillic + (CJK or Greek or Arabic) = garbled
            return hasCyrillic && (hasCJK || hasGreek || hasArabic)
        }

        /**
         * Checks if text contains Cyrillic characters.
         */
        private fun containsCyrillic(text: String): Boolean = text.any { it in '\u0400'..'\u04FF' }

        /**
         * Calculates confidence score for fixed text (0.0 to 1.0).
         * Higher score = more likely to be correct Russian text.
         */
        public fun calculateConfidence(text: String): Double {
            if (text.isEmpty()) {
                return 0.0
            }
            var score = 0.0

            // Check 1: Has Cyrillic
            val cyrillicRatio =
                text.count { it in '\u0400'..'\u04FF' }.toDouble() / text.length
            if (cyrillicRatio == 0.0) {
                return 0.0
            }
            score += cyrillicRatio * 0.4

            // Check 2: No garbage characters
            val hasCJK = text.any { it in '\u4E00'..'\u9FFF' }
            val hasGreek = text.any { it in '\u0370'..'\u03FF' }
            val hasArabic = text.any { it in '\u0600'..'\u06FF' }
            if (!hasCJK && !hasGreek && !hasArabic) {
                score += 0.15
            }
            if (!text.contains('\uFFFD')) {
                score += 0.1
            }

            // Check 3: Top-10 most frequent Russian letters (covers ~60% of text)
            // Based on frequency analysis: о, е, а, и, н, т, с, р, в, л
            val topRussianLetters = "оеаинтсрвл"
            val topLetterRatio =
                text.lowercase().count { it in topRussianLetters }.toDouble() / text.length
            score += topLetterRatio * 0.25 // Increased weight for top letters

            // IMPROVED: Check for proper word structure and title patterns
            val hasSpaces = text.contains(' ') || text.contains('.') || text.contains(',')
            val words = text.split(Regex("\\s+|[.,;!?]"))
            val validWords = words.filter { it.length >= 2 }

            // If ALL-CAPS but has proper word structure, it's likely a valid title
            val allCaps = text.all { !it.isLetter() || it.isUpperCase() }
            if (allCaps) {
                if (hasSpaces && validWords.size >= 2) {
                    // Proper title like "ДВОЙНИК РОДА. НАЧАЛО ПУТИ"
                    score += 0.2
                } else if (text.filter { it.isLetter() }.length > 20) {
                    // Too long ALL-CAPS without structure is suspicious
                    score -= 0.3
                }
            }

            // REMOVED titleWords check - replaced by structure analysis above
            // Check for characters outside standard Russian Cyrillic (U+0410-U+044F)
            val nonStandardCyrillic =
                text.count {
                    it in '\u0400'..'\u04ff' && it !in 'А'..'я' && it != 'Ё' && it != 'ё'
                }
            if (nonStandardCyrillic.toDouble() / text.length > 0.1) {
                score -= 0.3
            }

            val containsBadNumber = text.contains(Regex("\\p{L}№")) || text.contains(Regex("№\\p{L}"))
            if (containsBadNumber) {
                score -= 0.4
            }

            // Serbian check already covered by nonStandardCyrillic above

            val mixedCaseCount =
                words.count {
                    it.drop(1).any { c -> c.isUpperCase() } && it.any { c -> c.isLowerCase() }
                }
            if (mixedCaseCount > 0) {
                score -= 0.4
            }

            // Vowel check
            val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
            val vowelCount = text.count { it in vowels }
            val letterCount = text.count { it.isLetter() }
            if (letterCount > 0) {
                val ratio = vowelCount.toDouble() / letterCount
                if (ratio < 0.15 || ratio > 0.80) {
                    score -= 0.2
                }
            }

            // Check 4: Bigram naturalness using Zipf's law
            // Natural text has few frequent bigrams (top 20% = ~60% of all bigrams)
            val bigrams =
                text
                    .lowercase()
                    .windowed(2)
                    .filter { it.all { c -> c in 'а'..'я' || c == 'ё' } }

            if (bigrams.isNotEmpty()) {
                val frequencies = bigrams.groupingBy { it }.eachCount()
                val sorted = frequencies.values.sortedDescending()

                // Calculate Zipf's law compliance
                val top20Percent = (sorted.size * 0.2).toInt().coerceAtLeast(1)
                val top20Sum = sorted.take(top20Percent).sum()
                val zipfRatio = top20Sum.toDouble() / bigrams.size

                // Natural Russian text: top 20% bigrams ≈ 50-70% of total
                if (zipfRatio in 0.45..0.75) {
                    score += 0.15
                }
            }

            // IMPORTANT: Heavy penalty for control characters (indicates corrupted encoding like ISO-8859-5)
            val controlChars = text.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
            if (controlChars > 0) {
                score -= 0.5 // Heavy penalty - control chars indicate garbage
            }

            return score.coerceIn(0.0, 1.0)
        }

        /**
         * Fix mojibake (garbled text) using correct algorithm.
         */
        private fun fixMojibake(text: String): Triple<String, String, Double>? {
            // Don't try to fix CJK, Greek, Arabic, Latin-dominant text
            val hasCJK = text.any { it in '\u4E00'..'\u9FFF' }
            val hasGreek = text.any { it in '\u0370'..'\u03FF' }
            val hasArabic = text.any { it in '\u0600'..'\u06FF' }
            val latinCount = text.count { it in 'A'..'Z' || it in 'a'..'z' }
            val hasSignificantLatin = latinCount > 0 && latinCount.toDouble() / text.length > 0.5

            if (hasCJK || hasGreek || hasArabic || hasSignificantLatin) {
                return null
            }

            // CRITICAL: If text already has good Cyrillic confidence, DON'T try to fix!
            // This prevents "reverse mojibake" where valid UTF-8 gets corrupted
            if (containsCyrillic(text) && calculateConfidence(text) > 0.75) {
                return null
            }

            val targetEncodings =
                listOf("windows-1251", "KOI8-R", "windows-1252", "ISO-8859-5", "CP866")
            var bestResult: Triple<String, String, Double>? = null
            var bestScore = 0.65 // Raised threshold to avoid false positives (was 0.5)

            for (target in targetEncodings) {
                try {
                    val utf8Bytes = text.toByteArray(Charsets.UTF_8)
                    val latin1 = String(utf8Bytes, Charset.forName("ISO-8859-1"))
                    val originalBytes = latin1.toByteArray(Charset.forName("ISO-8859-1"))
                    val fixed = String(originalBytes, Charset.forName(target))

                    val conf = calculateConfidence(fixed)
                    if (conf > bestScore) {
                        bestScore = conf
                        bestResult = Triple(fixed, "UTF-8->latin1->$target", conf)
                    }
                } catch (e: Exception) {
                }
            }
            return bestResult
        }

        /**
         * Fix garbled Russian text by trying different encodings.
         */
        public fun fixGarbledText(text: String): Pair<String, String?> {
            val cleanText = text.replace("\uFEFF", "")

            // Don't try to fix CJK, Greek, Arabic, Latin-dominant text
            val hasCJK = cleanText.any { it in '\u4E00'..'\u9FFF' }
            val hasGreek = cleanText.any { it in '\u0370'..'\u03FF' }
            val hasArabic = cleanText.any { it in '\u0600'..'\u06FF' }
            val latinCount = cleanText.count { it in 'A'..'Z' || it in 'a'..'z' }
            val hasSignificantLatin = latinCount > 0 && latinCount.toDouble() / cleanText.length > 0.5

            if (hasCJK || hasGreek || hasArabic || hasSignificantLatin) {
                return Pair(cleanText, null)
            }

            val hasCyrillic = containsCyrillic(cleanText)
            val currentConf = calculateConfidence(cleanText)

            // Raised threshold from 0.55 to 0.65 to prevent CP866 false positives
            if (hasCyrillic && currentConf > 0.65) {
                return Pair(cleanText, null)
            }

            fixMojibake(cleanText)?.let {
                logger.e { "Used mojibake fix: ${it.second} (${(it.third * 100).toInt()}%)" }
                return Pair(it.first, it.second)
            }

            // Fallback: Try re-interpreting
            val sourceCharsets =
                listOf(
                    "ISO-8859-1",
                    "windows-1252",
                    "windows-1251", // IMPORTANT: Add windows-1251 for double-encoding check
                    "UTF-8",
                )
            for (source in sourceCharsets) {
                for (target in RUSSIAN_CHARSETS) {
                    // Skip problematic encodings that cause false positives
                    if (target == "ISO-8859-5" || target == "CP866") continue

                    try {
                        val bytes = cleanText.toByteArray(Charset.forName(source))
                        val fixed = String(bytes, Charset.forName(target))
                        val conf = calculateConfidence(fixed)

                        if (conf > 0.6 && conf > currentConf) { // Threshold 0.6
                            logger.e {
                                "Fixed garbled text: $source -> $target (confidence: $conf | original: ${cleanText.take(
                                    50,
                                )})"
                            }
                            return Pair(fixed, "$source->$target")
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            return Pair(cleanText, null)
        }
    }
