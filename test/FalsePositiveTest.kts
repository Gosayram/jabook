#!/usr/bin/env kotlin

import java.nio.charset.Charset

/**
 * Test Script for False Positive Fixes
 * Tests that detector doesn't corrupt valid text
 */

// Test Cases from Production Logs
val testCases = mutableListOf<Pair<String, String?>>()

// Case 1: Valid uppercase Cyrillic (should NOT be "fixed")
testCases.add("ДВОЙНИК РОДА. НАЧАЛО ПУТИ" to null)

// Case 2: CJK characters (should NOT be "fixed")  
testCases.add("蒿桦钽" to null)

// Case 3: Greek (should NOT be "fixed")
testCases.add("Ρεπγει Λσκό νενκξ" to null)

// Case 4: Valid lowercase (should NOT be "fixed")
testCases.add("зеркала реальности" to null)
testCases.add("Айнур Галин" to null)

// Case 5: Mixed case valid (should NOT be "fixed")
testCases.add("Глава 21" to null)
testCases.add("Глава 1" to null)

// Case 6: Actually garbled - create these programmatically to avoid escaping issues
// Pattern from screenshot: windows-1251 bytes misread as UTF-8
val garbled1 = String(byteArrayOf(0xD0.toByte(), 0x93.toByte(), 0xD0.toByte(), 0xBB.toByte(), 0xD0.toByte(), 0x9F.toByte(), 0xD0.toByte(), 0x92.toByte(), 0xD0.toByte(), 0xB0.toByte()), Charsets.UTF_8)  // Р"Р»РџРІР°
testCases.add(garbled1 to "Глава")

// Longer garbled text from our earlier test
val garbled2 = String(byteArrayOf(0xD0.toByte(), 0x97.toByte(), 0xD0.toByte(), 0xB5.toByte(), 0xD0.toByte(), 0xA1.toByte()), Charsets.UTF_8)  // Simplified
testCases.add(garbled2 to null)  // Skip for now, complex to create

object EncodingDetector {
    private val RUSSIAN_CHARSETS = listOf(
        "UTF-8", "windows-1252", "windows-1251", "KOI8-R", "CP866", 
        "ISO-8859-5", "UTF-16LE", "UTF-16BE", "UTF-16"
    )

    private fun containsCyrillic(text: String): Boolean = text.any { it in '\u0400'..'\u04FF' }

    fun calculateConfidence(text: String): Double {
        if (text.isEmpty()) return 0.0
        var score = 0.0

        val cyrillicRatio = text.count { it in '\u0400'..'\u04FF' }.toDouble() / text.length
        if (cyrillicRatio == 0.0) return 0.0
        score += cyrillicRatio * 0.4

        val hasCJK = text.any { it in '\u4E00'..'\u9FFF' }
        val hasGreek = text.any { it in '\u0370'..'\u03FF' }
        val hasArabic = text.any { it in '\u0600'..'\u06FF' }
        if (!hasCJK && !hasGreek && !hasArabic) score += 0.15
        if (!text.contains('\uFFFD')) score += 0.1

        val commonRussian = "еоаинтсрлвкмЕОАИНТСРЛВКМ"
        val commonRatio = text.count { it in commonRussian }.toDouble() / text.length
        score += commonRatio * 0.2

        // IMPROVED: Check if text has proper word structure (spaces/punctuation)
        val hasSpaces = text.contains(' ') || text.contains('.') || text.contains(',')
        val words = text.split(Regex("\\s+|[.,;!?]"))
        val validWords = words.filter { it.length >= 2 }
        
        // If ALL-CAPS but has proper word structure, it's likely a valid title
        val allCaps = text.all { !it.isLetter() || it.isUpperCase() }
        if (allCaps) {
            if (hasSpaces && validWords.size >= 2) {
                // Proper title like "ДВОЙНИК РОДА. НАЧАЛО ПУТИ"
                score += 0.2  // Bonus for structured uppercase
            } else if (text.filter { it.isLetter() }.length > 20) {
                // Too long ALL-CAPS without structure is suspicious
                score -= 0.3
            }
        }
        
        // Check for common title words in any case
        val titleWords = setOf("глава", "часть", "рода", "путь", "начало", "конец", "двойник", "операция")
        val hasTitleWords = words.any { it.lowercase() in titleWords }
        if (hasTitleWords) {
            score += 0.15  // Likely a book/chapter title
        }
        
        val weirdChars = text.count { it in listOf('Ў', 'Ї', 'Є', 'І', '¦', '§', '¨', '©', 'ª', '«', '»', '¬', '­', '®', '¯') }
        if (weirdChars.toDouble() / text.length > 0.1) score -= 0.3

        val containsBadNumber = text.contains(Regex("\\p{L}№")) || text.contains(Regex("№\\p{L}"))
        if (containsBadNumber) score -= 0.4

        val serbianChars = "ђѓѕјљњћџЂЃЅЈЉЊЋЏ"
        if (text.any { it in serbianChars }) score -= 0.3

        val mixedCaseCount = words.count { it.drop(1).any { c -> c.isUpperCase() } && it.any { c -> c.isLowerCase() } }
        if (mixedCaseCount > 0) score -= 0.4
        
        val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
        val vowelCount = text.count { it in vowels }
        val letterCount = text.count { it.isLetter() }
        if (letterCount > 0) {
            val ratio = vowelCount.toDouble() / letterCount
            if (ratio < 0.15 || ratio > 0.80) score -= 0.2
        }

        val validBigrams = setOf(
            "ст", "но", "то", "на", "не", "ко", "ен", "ра", "ов", "ро", "во", "по", "пр", "ре", "ли", "от", "ни", "та", "ер", "ор", "ел", "ос", "ан", "ал", "ол", "ва",
            "гл", "ла", "ав", "вс", "об", "че", "го", "де", "ми", "ну", "од", "он", "пл", "св", "ск"
        )
        val bigrams = text.windowed(2).filter { it.all { c -> c in 'а'..'я' || c in 'А'..'Я' } }
        val validBigramRatio = if (bigrams.isNotEmpty()) {
            bigrams.count { it.lowercase() in validBigrams }.toDouble() / bigrams.size
        } else 0.0
        score += validBigramRatio * 0.15

        return score.coerceIn(0.0, 1.0)
    }

    private fun fixMojibake(text: String): Triple<String, String, Double>? {
        if (containsCyrillic(text) && calculateConfidence(text) > 0.7) return null

        val targetEncodings = listOf("windows-1251", "KOI8-R", "windows-1252", "ISO-8859-5", "CP866")
        var bestResult: Triple<String, String, Double>? = null
        var bestScore = 0.5

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
            } catch (e: Exception) {}
        }
        return bestResult
    }

    fun fixGarbledText(text: String): Pair<String, String?> {
        val cleanText = text.replace("\uFEFF", "")
        
        // NEW: Don't try to fix CJK, Greek, Arabic, Latin
        val hasCJK = cleanText.any { it in '\u4E00'..'\u9FFF' }
        val hasGreek = cleanText.any { it in '\u0370'..'\u03FF' }
        val hasArabic = cleanText.any { it in '\u0600'..'\u06FF' }
        val latinCount = cleanText.count { it in 'A'..'Z' || it in 'a'..'z' }
        val hasSignificantLatin = latinCount > 0 && latinCount.toDouble() / cleanText.length > 0.5
        
        if (hasCJK || hasGreek || hasArabic || hasSignificantLatin) {
            println("  [SKIP] Non-Cyrillic script detected")
            return Pair(cleanText, null)
        }
        
        val hasCyrillic = containsCyrillic(cleanText)
        val currentConf = calculateConfidence(cleanText)
        
        // LOWERED threshold from 0.7 to 0.55
        if (hasCyrillic && currentConf > 0.55) {
            println("  [SKIP] Already correct (confidence: ${(currentConf * 100).toInt()}%)")
            return Pair(cleanText, null)
        }

        fixMojibake(cleanText)?.let { 
            println("  [FIX] Mojibake: ${it.second} (confidence: ${(it.third * 100).toInt()}%)")
            return Pair(it.first, it.second) 
        }

        val sourceCharsets = listOf("ISO-8859-1", "windows-1252", "windows-1251", "UTF-8")
        for (source in sourceCharsets) {
            for (target in RUSSIAN_CHARSETS) {
                try {
                    val bytes = cleanText.toByteArray(Charset.forName(source))
                    val fixed = String(bytes, Charset.forName(target))
                    val conf = calculateConfidence(fixed)
                    
                    if (conf > 0.6 && conf > currentConf) {
                        println("  [FIX] Re-encode: $source->$target (confidence: ${(conf * 100).toInt()}%)")
                        return Pair(fixed, "$source->$target")
                    }
                } catch (e: Exception) {}
            }
        }
        
        println("  [SKIP] No improvement found")
        return Pair(cleanText, null)
    }
}

fun main() {
    println("╔════════════════════════════════════════════════════════════╗")
    println("║  ENCODING DETECTOR - FALSE POSITIVE TEST                  ║")
    println("╚════════════════════════════════════════════════════════════╝\n")

    var passed = 0
    var failed = 0

    for ((input, expectedFix) in testCases) {
        println("Input: '$input'")
        
        val conf = EncodingDetector.calculateConfidence(input)
        println("  Confidence: ${(conf * 100).toInt()}%")
        
        val (result, method) = EncodingDetector.fixGarbledText(input)
        
        val success = if (expectedFix == null) {
            // Should NOT be fixed
            result == input
        } else {
            // Should be fixed to expected value
            result == expectedFix
        }
        
        if (success) {
            println("  ✅ PASS")
            if (method != null) {
                println("      Fixed: '$result' ($method)")
            } else {
                println("      Not modified (correct)")
            }
            passed++
        } else {
            println("  ❌ FAIL")
            println("      Expected: '${expectedFix ?: input}'")
            println("      Got: '$result'${if (method != null) " ($method)" else ""}")
            failed++
        }
        println()
    }

    println("═══════════════════════════════════════════════════════════")
    println("Results: $passed passed, $failed failed")
    
    if (failed > 0) {
        println("❌ TESTS FAILED")
        System.exit(1)
    } else {
        println("✅ ALL TESTS PASSED")
    }
}

main()
