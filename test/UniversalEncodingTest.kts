#!/usr/bin/env kotlin

import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Universal Encoding Test Script
 * Tests the EncodingDetector against MP3 files with known correct metadata.
 * For files where ffmpeg fails, we use manual ground truth overrides.
 */

// Ground Truth Overrides (for files where ffmpeg returns garbage)
val GROUND_TRUTH_OVERRIDES = mapOf(
    "022.mp3" to mapOf(
        "title" to "Глава 21",
        "artist" to "Айнур Галин",
        "album" to "Зеркала Реальности"
    ),
    "021 - Эпилог.mp3" to mapOf(
        "title" to "Эпилог",
        "artist" to "Сергей Лукьяненко",
        "album" to "Холодные берега"
    )
)

// =========================================================================================
// COPIED ENCODING DETECTOR LOGIC
// =========================================================================================

object EncodingDetector {
    private val RUSSIAN_CHARSETS = listOf(
        "UTF-8", "windows-1252", "windows-1251", "KOI8-R", "CP866", 
        "ISO-8859-5", "UTF-16LE", "UTF-16BE", "UTF-16"
    )

    fun detectEncoding(bytes: ByteArray, hint: String? = null): Charset {
        if (bytes.isEmpty()) return Charsets.UTF_8
        
        // BOM Detection
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charsets.UTF_16
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charsets.UTF_16
        }

        hint?.let { hintCharset ->
            try {
                val charset = Charset.forName(hintCharset)
                val decoded = String(bytes, charset)
                if (!decoded.contains('\uFFFD')) return charset
            } catch (e: Exception) { }
        }
        return detectEncodingHeuristic(bytes)
    }

    private fun detectEncodingHeuristic(bytes: ByteArray): Charset {
        val sample = bytes.take(1000)
        if (isValidUtf8(sample.toByteArray())) return Charsets.UTF_8

        val win1251 = Charset.forName("windows-1251")
        val koi8r = Charset.forName("KOI8-R")

        val sWin = String(sample.toByteArray(), win1251)
        val sKoi = String(sample.toByteArray(), koi8r)

        val confWin = calculateConfidence(sWin)
        val confKoi = calculateConfidence(sKoi)

        if (confWin > 0.0 || confKoi > 0.0) {
            return if (confWin >= confKoi) win1251 else koi8r
        }
        
        return Charsets.UTF_8
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean =
        try { !String(bytes, Charsets.UTF_8).contains('\uFFFD') } catch (e: Exception) { false }

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

        if (text.contains(Regex("[А-ЯЁ]{5,}"))) score -= 0.5
        
        val weirdChars = text.count { it in listOf('Ў', 'Ї', 'Є', 'І', '¦', '§', '¨', '©', 'ª', '«', '»', '¬', '­', '®', '¯') }
        if (weirdChars.toDouble() / text.length > 0.1) score -= 0.3

        val containsBadNumber = text.contains(Regex("\\p{L}№")) || text.contains(Regex("№\\p{L}"))
        if (containsBadNumber) score -= 0.4

        val serbianChars = "ђѓѕјљњћџЂЃЅЈЉЊЋЏ"
        if (text.any { it in serbianChars }) score -= 0.3

        val words = text.split(" ")
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
        // Don't try to fix CJK, Greek, Arabic, Latin-dominant text
        val hasCJK = text.any { it in '\u4E00'..'\u9FFF' }
        val hasGreek = text.any { it in '\u0370'..'\u03FF' }
        val hasArabic = text.any { it in '\u0600'..'\u06FF' }
        val latinCount = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val hasSignificantLatin = latinCount > 0 && latinCount.toDouble() / text.length > 0.5

        if (hasCJK || hasGreek || hasArabic || hasSignificantLatin) {
            return null
        }

        if (containsCyrillic(text) && calculateConfidence(text) > 0.7) return null

        val targetEncodings = listOf("windows-1251", "KOI8-R", "windows-1252", "ISO-8859-5", "CP866")
        var bestResult: Triple<String, String, Double>? = null
        var bestScore = 0.65 // Raised to avoid false positives

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
        
        val hasCyrillic = containsCyrillic(cleanText)
        val hasCJK = cleanText.any { it in '\u4E00'..'\u9FFF' }
        val currentConf = calculateConfidence(cleanText)
        
        if (hasCyrillic && !hasCJK && currentConf > 0.65) {
            return Pair(cleanText, null)
        }

        fixMojibake(cleanText)?.let { return Pair(it.first, it.second) }

        val sourceCharsets = listOf("ISO-8859-1", "windows-1252", "windows-1251", "UTF-8")
        for (source in sourceCharsets) {
            for (target in RUSSIAN_CHARSETS) {
                try {
                    val bytes = cleanText.toByteArray(Charset.forName(source))
                    val fixed = String(bytes, Charset.forName(target))
                    val conf = calculateConfidence(fixed)
                    
                    if (conf > 0.6 && conf > currentConf) {
                        return Pair(fixed, "$source->$target")
                    }
                } catch (e: Exception) {}
            }
        }
        return Pair(cleanText, null)
    }
}

// =========================================================================================
// TEST SCRIPT LOGIC
// =========================================================================================

fun runCommand(cmd: List<String>): String {
    val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
    process.waitFor(5, TimeUnit.SECONDS)
    return process.inputStream.bufferedReader().readText()
}

fun normalizeString(str: String): String {
    // Strip BOM, zero-width chars, null bytes, and other invisible Unicode
    return str.replace("\uFEFF", "")          // BOM
              .replace("\u200B", "")          // Zero-Width Space
              .replace("\u200C", "")          // Zero-Width Non-Joiner
              .replace("\u200D", "")          // Zero-Width Joiner
              .replace("\uFFFD", "")          // Replacement Character
              .replace("\u00A0", " ")         // Non-breaking space to regular space
              .replace("\u0000", "")          // Null byte
              .trim()
}

fun parseFfmpegMetadata(filePath: String): Map<String, String> {
    val fileName = File(filePath).name
    
    // Use override if available
    GROUND_TRUTH_OVERRIDES[fileName]?.let {
        println("  ⚙️  Using Manual Ground Truth (ffmpeg returns garbage for this file)")
        return it
    }

    val cmd = listOf("ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", filePath)
    val output = runCommand(cmd)
    
    val tags = mutableMapOf<String, String>()
    val regex = Regex("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"")
    
    for (match in regex.findAll(output)) {
        val key = match.groupValues[1].lowercase()
        val value = match.groupValues[2]
        if (key in listOf("title", "artist", "album")) {
            tags[key] = value
        }
    }
    return tags
}

fun readRawId3Tags(file: File): Map<String, ByteArray> {
    val bytes = file.readBytes()
    val rawTags = mutableMapOf<String, ByteArray>()
    
    if (bytes.size < 10 || String(bytes.sliceArray(0..2)) != "ID3") return rawTags
    
    var pos = 10
    val sizeBytes = bytes.sliceArray(6..9)
    val tagSize = (sizeBytes[0].toInt() and 0x7F shl 21) or
                  (sizeBytes[1].toInt() and 0x7F shl 14) or
                  (sizeBytes[2].toInt() and 0x7F shl 7) or
                  (sizeBytes[3].toInt() and 0x7F)
    
    var limit = 10 + tagSize
    if (limit > bytes.size) limit = bytes.size

    while (pos < limit - 10) {
        val frameId = String(bytes.sliceArray(pos..pos+3))
        if (frameId.all { it.code == 0 }) break
        if (!frameId.all { it.isLetterOrDigit() }) { pos++; continue }
        
        val s1 = bytes[pos+4].toInt() and 0xFF
        val s2 = bytes[pos+5].toInt() and 0xFF
        val s3 = bytes[pos+6].toInt() and 0xFF
        val s4 = bytes[pos+7].toInt() and 0xFF
        val frameSize = (s1 shl 24) or (s2 shl 16) or (s3 shl 8) or s4
        
        if (frameSize <= 0 || pos + 10 + frameSize > limit) { pos++; continue }

        pos += 10
        
        if (pos + frameSize <= bytes.size) {
            val content = bytes.sliceArray(pos until pos + frameSize)
            val key = when(frameId) {
                "TIT2" -> "title"
                "TPE1" -> "artist"
                "TALB" -> "album"
                else -> frameId
            }
            rawTags[key] = content
        }
        pos += frameSize
    }
    return rawTags
}

fun main() {
    val dir = File("../test_results")
    if (!dir.exists()) {
        println("Directory ../test_results not found!")
        System.exit(1)
    }

    val mp3Files = dir.listFiles { f -> f.name.endsWith(".mp3") } ?: emptyArray()
    println("Found ${mp3Files.size} MP3 files.\n")

    var failureCount = 0

    for (file in mp3Files) {
        println("=== Analyzing: ${file.name} ===")
        val groundTruth = parseFfmpegMetadata(file.absolutePath)
        println("  Ground Truth: $groundTruth")

        val rawTags = readRawId3Tags(file)
        if (rawTags.isEmpty()) {
            println("  ⚠️  Could not read raw ID3 tags")
            continue
        }

        for ((key, truthValue) in groundTruth) {
            if (key !in listOf("title", "artist", "album")) continue
            if (truthValue.isBlank()) continue

            val rawBytes = rawTags[key] ?: continue
            val payload = if (rawBytes.isNotEmpty()) rawBytes.drop(1).toByteArray() else rawBytes

            println("  Field: '$key'")
            println("    Expected: '$truthValue'")
            
            val detected = EncodingDetector.detectEncoding(payload)
            val decoded = String(payload, detected)
            
            val naiveIso = String(payload, Charset.forName("ISO-8859-1"))
            val (fixed, fixMethod) = EncodingDetector.fixGarbledText(naiveIso)
            
            // ВАЖНО: Передаем decoded БЕЗ trim(), чтобы fixGarbledText мог правильно его обработать
            val (fixedDecoded, fixMethodDecoded) = EncodingDetector.fixGarbledText(decoded)

            // Normalize for comparison
            val normalizedTruth = normalizeString(truthValue)
            val normalizedDecoded = normalizeString(decoded)
            val normalizedFixed = normalizeString(fixed)
            val normalizedFixedDecoded = normalizeString(fixedDecoded)

            val matched = normalizedDecoded == normalizedTruth || 
                          normalizedFixed == normalizedTruth ||
                          normalizedFixedDecoded == normalizedTruth

            if (matched) {
                println("    ✅ MATCHED!")
            } else {
                println("    ❌ FAILED!")
                println("       Decoded ($detected): '$normalizedDecoded'")
                println("       Fix(ISO): '$normalizedFixed' ($fixMethod)")
                println("       Fix(Decoded): '$normalizedFixedDecoded' ($fixMethodDecoded)")
                
                // Debug
                println("       DEBUG: truth='$normalizedTruth' (len=${normalizedTruth.length}) bytes=${normalizedTruth.toByteArray().joinToString(" ") { "%02X".format(it) }}")
                println("       DEBUG: fixed='$normalizedFixedDecoded' (len=${normalizedFixedDecoded.length}) bytes=${normalizedFixedDecoded.toByteArray().joinToString(" ") { "%02X".format(it) }}")
                
                failureCount++
            }
        }
        println()
    }

    if (failureCount > 0) {
        println("FAILED: $failureCount metadata fields did not resolve correctly.")
        System.exit(1)
    } else {
        println("✅ SUCCESS: All tests passed!")
    }
}

main()
