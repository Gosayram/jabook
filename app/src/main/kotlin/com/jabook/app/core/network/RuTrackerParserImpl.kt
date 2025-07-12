package com.jabook.app.core.network

import com.jabook.app.shared.debug.DebugLogger
import com.jabook.app.shared.utils.TextUtils
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of RuTracker HTML parser */
@Singleton
class RuTrackerParserImpl @Inject constructor() : RuTrackerParser {
    override suspend fun parseSearchResults(html: String): List<AudiobookInfo> {
        DebugLogger.logDebug("Parsing search results", "RuTrackerParser")

        return try {
            val audiobooks = mutableListOf<AudiobookInfo>()

            // Parse table rows with search results
            val tableRows = extractTableRows(html)

            tableRows.forEach { row ->
                val audiobookInfo = parseSearchResultRow(row)
                if (audiobookInfo != null) {
                    audiobooks.add(audiobookInfo)
                }
            }

            DebugLogger.logDebug("Parsed ${audiobooks.size} audiobooks from search results", "RuTrackerParser")
            audiobooks
        } catch (e: Exception) {
            DebugLogger.logError("Failed to parse search results", e, "RuTrackerParser")
            emptyList()
        }
    }

    override suspend fun parseAudiobookDetails(html: String): AudiobookInfo {
        DebugLogger.logDebug("Parsing audiobook details", "RuTrackerParser")

        return try {
            val id = extractTopicId(html)
            val title = extractTitle(html)
            val author = extractAuthor(html)
            val narrator = extractNarrator(html)
            val description = extractDescription(html)
            val category = extractCategory(html)
            val size = extractSize(html)
            val duration = extractDuration(html)
            val quality = extractQuality(html)
            val torrentUrl = extractTorrentUrl(html)
            val coverUrl = extractCoverUrl(html)
            val (seeders, leechers) = extractSeedersLeechers(html)
            val magnetLink = extractMagnetLink(html)

            AudiobookInfo(
                id = id,
                title = title,
                author = author,
                narrator = narrator,
                description = description,
                category = category,
                size = size,
                duration = duration,
                quality = quality,
                torrentUrl = torrentUrl,
                coverUrl = coverUrl,
                seeders = seeders,
                leechers = leechers,
                magnetLink = magnetLink,
            )
        } catch (e: Exception) {
            DebugLogger.logError("Failed to parse audiobook details", e, "RuTrackerParser")
            throw e
        }
    }

    override suspend fun parseCategories(html: String): List<Category> {
        DebugLogger.logDebug("Parsing categories", "RuTrackerParser")

        return try {
            val categories = mutableListOf<Category>()

            // Parse category tree structure
            val categoryElements = extractCategoryElements(html)

            categoryElements.forEach { element ->
                val category = parseCategoryElement(element)
                if (category != null) {
                    categories.add(category)
                }
            }

            DebugLogger.logDebug("Parsed ${categories.size} categories", "RuTrackerParser")
            categories
        } catch (e: Exception) {
            DebugLogger.logError("Failed to parse categories", e, "RuTrackerParser")
            emptyList()
        }
    }

    override suspend fun extractMagnetLink(html: String): String? {
        return try {
            val magnetPattern = Regex("magnet:\\?xt=urn:btih:[a-fA-F0-9]{40,}[^\"]*")
            val match = magnetPattern.find(html)
            match?.value
        } catch (e: Exception) {
            DebugLogger.logError("Failed to extract magnet link", e, "RuTrackerParser")
            null
        }
    }

    // Private helper methods for parsing

    private fun extractTableRows(html: String): List<String> {
        // Extract table rows from search results
        val tablePattern = Regex("<tr[^>]*class=\"[^\"]*tCenter[^\"]*\"[^>]*>.*?</tr>", RegexOption.DOT_MATCHES_ALL)
        return tablePattern.findAll(html).map { it.value }.toList()
    }

    private fun parseSearchResultRow(row: String): AudiobookInfo? {
        return try {
            // Extract basic info from table row
            val id = extractFromPattern(row, "viewtopic\\.php\\?t=(\\d+)", 1) ?: return null
            val title = extractFromPattern(row, "<a[^>]*class=\"[^\"]*topictitle[^\"]*\"[^>]*>([^<]+)</a>", 1) ?: "Unknown"
            val author = extractAuthorFromRow(row)
            val size = extractSizeFromRow(row)
            val (seeders, leechers) = extractSeedersLeechersFromRow(row)

            AudiobookInfo(
                id = id,
                title = TextUtils.removeHtmlTags(title),
                author = author,
                description = "",
                category = "",
                size = size,
                quality = "",
                torrentUrl = "",
                seeders = seeders,
                leechers = leechers,
            )
        } catch (e: Exception) {
            DebugLogger.logError("Failed to parse search result row", e, "RuTrackerParser")
            null
        }
    }

    private fun extractFromPattern(html: String, pattern: String, groupIndex: Int): String? {
        return try {
            val regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html)
            match?.groupValues?.getOrNull(groupIndex)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTopicId(html: String): String {
        return extractFromPattern(html, "viewtopic\\.php\\?t=(\\d+)", 1) ?: "0"
    }

    private fun extractTitle(html: String): String {
        return extractFromPattern(html, "<title>([^<]+)</title>", 1)?.replace(" :: RuTracker.org", "") ?: "Unknown Title"
    }

    private fun extractAuthor(html: String): String {
        return extractFromPattern(html, "Автор[^:]*:([^<]+)", 1) ?: extractFromPattern(html, "Author[^:]*:([^<]+)", 1) ?: "Unknown Author"
    }

    private fun extractNarrator(html: String): String? {
        return extractFromPattern(html, "Исполнитель[^:]*:([^<]+)", 1) ?: extractFromPattern(html, "Narrator[^:]*:([^<]+)", 1)
    }

    private fun extractDescription(html: String): String {
        return extractFromPattern(html, "<div[^>]*class=\"[^\"]*post_body[^\"]*\"[^>]*>([^<]+)</div>", 1)?.let {
            TextUtils.removeHtmlTags(it)
        } ?: ""
    }

    private fun extractCategory(html: String): String {
        return extractFromPattern(html, "Жанр[^:]*:([^<]+)", 1) ?: extractFromPattern(html, "Genre[^:]*:([^<]+)", 1) ?: "Unknown"
    }

    private fun extractSize(html: String): Long {
        val sizeText = extractFromPattern(html, "Размер[^:]*:([^<]+)", 1) ?: extractFromPattern(html, "Size[^:]*:([^<]+)", 1) ?: return 0

        return parseSizeString(sizeText)
    }

    private fun extractDuration(html: String): String? {
        return extractFromPattern(html, "Продолжительность[^:]*:([^<]+)", 1) ?: extractFromPattern(html, "Duration[^:]*:([^<]+)", 1)
    }

    private fun extractQuality(html: String): String {
        return extractFromPattern(html, "Качество[^:]*:([^<]+)", 1) ?: extractFromPattern(html, "Quality[^:]*:([^<]+)", 1) ?: "Unknown"
    }

    private fun extractTorrentUrl(html: String): String {
        val topicId = extractTopicId(html)
        return "https://rutracker.org/forum/dl.php?t=$topicId"
    }

    private fun extractCoverUrl(html: String): String? {
        return extractFromPattern(html, "<img[^>]*src=\"([^\"]*\\.(?:jpg|png|gif|jpeg))\"[^>]*>", 1)
    }

    private fun extractSeedersLeechers(html: String): Pair<Int, Int> {
        val seeders = extractFromPattern(html, "Сиды[^:]*:(\\d+)", 1)?.toIntOrNull() ?: 0
        val leechers = extractFromPattern(html, "Личи[^:]*:(\\d+)", 1)?.toIntOrNull() ?: 0
        return Pair(seeders, leechers)
    }

    private fun extractAuthorFromRow(row: String): String {
        // Extract author from search result row
        return extractFromPattern(row, "Автор[^:]*:([^<]+)", 1) ?: extractFromPattern(row, "Author[^:]*:([^<]+)", 1) ?: "Unknown"
    }

    private fun extractSizeFromRow(row: String): Long {
        val sizeText = extractFromPattern(row, "\\d+(?:\\.\\d+)?\\s*(?:KB|MB|GB)", 0) ?: return 0
        return parseSizeString(sizeText)
    }

    private fun extractSeedersLeechersFromRow(row: String): Pair<Int, Int> {
        val numbers = TextUtils.extractNumbers(row)
        return if (numbers.size >= 2) {
            Pair(numbers[numbers.size - 2], numbers[numbers.size - 1])
        } else {
            Pair(0, 0)
        }
    }

    private fun extractCategoryElements(html: String): List<String> {
        // Extract category elements from HTML
        val categoryPattern = Regex("<option[^>]*value=\"(\\d+)\"[^>]*>([^<]+)</option>")
        return categoryPattern.findAll(html).map { it.value }.toList()
    }

    private fun parseCategoryElement(element: String): Category? {
        return try {
            val id = extractFromPattern(element, "value=\"(\\d+)\"", 1) ?: return null
            val name = extractFromPattern(element, ">([^<]+)</option>", 1) ?: return null

            Category(id = id, name = TextUtils.removeHtmlTags(name))
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSizeString(sizeText: String): Long {
        return try {
            val cleanText = sizeText.trim()
            val numberPattern = Regex("([0-9.]+)\\s*(KB|MB|GB)", RegexOption.IGNORE_CASE)
            val match = numberPattern.find(cleanText) ?: return 0

            val number = match.groupValues[1].toDouble()
            val unit = match.groupValues[2].uppercase()

            when (unit) {
                "KB" -> (number * 1024).toLong()
                "MB" -> (number * 1024 * 1024).toLong()
                "GB" -> (number * 1024 * 1024 * 1024).toLong()
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
