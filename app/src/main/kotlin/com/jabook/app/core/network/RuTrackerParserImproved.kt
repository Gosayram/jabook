package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.TorrentState
import com.jabook.app.shared.debug.IDebugLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Improved RuTracker HTML Parser with updated selectors
 *
 * This parser uses current RuTracker HTML structure and includes
 * better error handling and fallback mechanisms
 */
@Singleton
class RuTrackerParserImproved @Inject constructor(
    private val debugLogger: IDebugLogger,
) : RuTrackerParser() {

    override suspend fun parseSearchResults(html: String): List<RuTrackerAudiobook> {
        return try {
            debugLogger.logDebug("RuTrackerParserImproved: Starting to parse search results")

            // Check for common error pages first
            if (isErrorPage(html)) {
                debugLogger.logWarning("RuTrackerParserImproved: Error page detected")
                return emptyList()
            }

            val doc = Jsoup.parse(html)

            // Try multiple selectors for torrent rows
            val torrentRows = findTorrentRows(doc)
            debugLogger.logDebug("RuTrackerParserImproved: Found ${torrentRows.size} torrent rows")

            if (torrentRows.isEmpty()) {
                debugLogger.logWarning("RuTrackerParserImproved: No torrent rows found")
                logHtmlStructure(doc)
                return emptyList()
            }

            val results = torrentRows.mapNotNull { row ->
                try {
                    parseSearchResultRow(row)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerParserImproved: Failed to parse row", e)
                    null
                }
            }

            debugLogger.logInfo("RuTrackerParserImproved: Successfully parsed ${results.size} audiobooks")
            results
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to parse search results", e)
            emptyList()
        }
    }

    override suspend fun parseAudiobookDetails(html: String): RuTrackerAudiobook? {
        return try {
            debugLogger.logDebug("RuTrackerParserImproved: Starting to parse audiobook details")

            if (isErrorPage(html)) {
                debugLogger.logWarning("RuTrackerParserImproved: Error page detected in details")
                return null
            }

            val doc = Jsoup.parse(html)
            parseAudiobookDetailsFromDocument(doc, html)
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to parse audiobook details", e)
            null
        }
    }

    override suspend fun parseCategories(html: String): List<RuTrackerCategory> {
        return try {
            debugLogger.logDebug("RuTrackerParserImproved: Starting to parse categories")

            val doc = Jsoup.parse(html)
            val categories = mutableListOf<RuTrackerCategory>()

            // Parse forum categories
            val forumRows = doc.select("tr.forum_row, tr.cat_row, .forum-item")

            forumRows.forEach { row ->
                try {
                    val category = parseCategoryRow(row)
                    if (category != null) {
                        categories.add(category)
                    }
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerParserImproved: Failed to parse category row", e)
                }
            }

            debugLogger.logInfo("RuTrackerParserImproved: Successfully parsed ${categories.size} categories")
            categories
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to parse categories", e)
            emptyList()
        }
    }

    override suspend fun extractMagnetLink(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)

            // Try multiple selectors for magnet links
            val magnetSelectors = listOf(
                "a[href^='magnet:']",
                ".magnet-link",
                ".dl-link[href*='magnet']",
                "a[title*='magnet']",
            )

            for (selector in magnetSelectors) {
                val magnetElement = doc.selectFirst(selector)
                if (magnetElement != null) {
                    val href = magnetElement.attr("href")
                    if (href.startsWith("magnet:")) {
                        debugLogger.logDebug("RuTrackerParserImproved: Found magnet link with selector: $selector")
                        return href
                    }
                }
            }

            // Fallback: search in text content
            val magnetRegex = Regex("magnet:\\?[^\\s\"'<>]+")
            val match = magnetRegex.find(html)
            if (match != null) {
                debugLogger.logDebug("RuTrackerParserImproved: Found magnet link via regex")
                return match.value
            }

            debugLogger.logWarning("RuTrackerParserImproved: No magnet link found")
            null
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to extract magnet link", e)
            null
        }
    }

    override suspend fun extractTorrentLink(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)

            // Try multiple selectors for torrent download links
            val torrentSelectors = listOf(
                "a[href*='dl.php']",
                "a[href*='download']",
                ".torrent-link",
                "a[title*='torrent']",
                "a[href*='.torrent']",
            )

            for (selector in torrentSelectors) {
                val torrentLink = processTorrentElement(doc, selector)
                if (torrentLink != null) {
                    return torrentLink
                }
            }

            debugLogger.logWarning("RuTrackerParserImproved: No torrent link found")
            null
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to extract torrent link", e)
            null
        }
    }

    private fun processTorrentElement(doc: Document, selector: String): String? {
        val torrentElement = doc.selectFirst(selector)
        if (torrentElement != null) {
            val href = torrentElement.attr("href")
            if (href.contains("dl.php") || href.endsWith(".torrent")) {
                debugLogger.logDebug("RuTrackerParserImproved: Found torrent link with selector: $selector")
                return if (href.startsWith("http")) href else "https://rutracker.net$href"
            }
        }
        return null
    }

    override suspend fun parseTorrentState(html: String): TorrentState {
        return try {
            val doc = Jsoup.parse(html)

            // Look for status indicators
            val statusElements = doc.select("img[src*='status'], .status, .torrent-status")

            for (element in statusElements) {
                val src = element.attr("src")
                val title = element.attr("title")
                val text = element.text()

                val statusText = "$src $title $text".lowercase()

                val torrentState = getTorrentState(statusText)
                if (torrentState != null) {
                    return torrentState
                }
            }

            TorrentState.APPROVED // Default
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to parse torrent state", e)
            TorrentState.APPROVED
        }
    }

    private fun getTorrentState(statusText: String): TorrentState? {
        return when {
            isApproved(statusText) -> TorrentState.APPROVED
            isNotApproved(statusText) -> TorrentState.NOT_APPROVED
            isNeedEdit(statusText) -> TorrentState.NEED_EDIT
            isDubious(statusText) -> TorrentState.DUBIOUSLY
            isConsumed(statusText) -> TorrentState.CONSUMED
            isTemporary(statusText) -> TorrentState.TEMPORARY
            else -> null
        }
    }

    private fun isApproved(statusText: String): Boolean {
        return statusText.contains("проверено") || statusText.contains("approved")
    }

    private fun isNotApproved(statusText: String): Boolean {
        return statusText.contains("не проверено") || statusText.contains("not approved")
    }

    private fun isNeedEdit(statusText: String): Boolean {
        return statusText.contains("недооформлено") || statusText.contains("need edit")
    }

    private fun isDubious(statusText: String): Boolean {
        return statusText.contains("сомнительно") || statusText.contains("dubious")
    }

    private fun isConsumed(statusText: String): Boolean {
        return statusText.contains("поглощена") || statusText.contains("consumed")
    }

    private fun isTemporary(statusText: String): Boolean {
        return statusText.contains("временная") || statusText.contains("temporary")
    }

    private fun isErrorPage(html: String): Boolean {
        val errorIndicators = listOf(
            "cloudflare",
            "captcha",
            "access denied",
            "доступ запрещен",
            "ошибка",
            "error",
            "blocked",
            "заблокирован",
        )

        val lowerHtml = html.lowercase()
        return errorIndicators.any { lowerHtml.contains(it) }
    }

    private fun findTorrentRows(doc: Document): List<Element> {
        // Try multiple selectors for torrent rows
        val rowSelectors = listOf(
            "tr[data-topic_id]",
            "tr.hl-tr",
            "tr.tCenter",
            "tr[id^='tr-']",
            ".torrent-row",
            "tbody tr:has(a[href*='viewtopic'])",
        )

        for (selector in rowSelectors) {
            val rows = doc.select(selector)
            if (rows.isNotEmpty()) {
                debugLogger.logDebug("RuTrackerParserImproved: Found rows with selector: $selector")
                return rows.toList()
            }
        }

        // Fallback: find any table rows with links to viewtopic
        val fallbackRows = doc.select("tr").filter { row ->
            row.select("a[href*='viewtopic']").isNotEmpty()
        }

        if (fallbackRows.isNotEmpty()) {
            debugLogger.logDebug("RuTrackerParserImproved: Using fallback row detection")
        }

        return fallbackRows
    }

    private fun parseSearchResultRow(row: Element): RuTrackerAudiobook? {
        try {
            // Extract topic ID
            val topicId = extractTopicId(row) ?: return null

            // Extract title
            val title = extractTitle(row) ?: return null

            // Extract other fields with fallbacks
            val author = extractAuthorFromTitle(title)
            val size = extractSize(row)
            val sizeBytes = parseSizeToBytes(size)
            val seeders = extractSeeders(row)
            val leechers = extractLeechers(row)
            val addedDate = extractAddedDate(row)

            return RuTrackerAudiobook(
                id = topicId,
                title = title,
                author = author,
                narrator = null,
                description = "",
                category = "Audiobooks",
                categoryId = "33",
                year = null,
                quality = null,
                duration = null,
                size = size,
                sizeBytes = sizeBytes,
                magnetUri = null,
                torrentUrl = null,
                seeders = seeders,
                leechers = leechers,
                completed = 0,
                addedDate = addedDate,
                lastUpdate = null,
                coverUrl = null,
                rating = null,
                genreList = emptyList(),
                tags = emptyList(),
                isVerified = false,
                state = TorrentState.APPROVED,
                downloads = 0,
                registered = null,
            )
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to parse search result row", e)
            return null
        }
    }

    private fun extractTopicId(row: Element): String? {
        // Try multiple selectors for topic links
        val linkSelectors = listOf(
            "a[href*='viewtopic.php?t=']",
            "a[href*='t=']",
            ".torrent-title a",
            ".topic-title a",
        )

        for (selector in linkSelectors) {
            val link = row.selectFirst(selector)
            if (link != null) {
                val href = link.attr("href")
                val topicId = extractTopicIdFromHref(href)
                if (topicId.isNotBlank()) {
                    return topicId
                }
            }
        }

        // Try data attributes
        val dataTopicId = row.attr("data-topic_id")
        if (dataTopicId.isNotBlank()) {
            return dataTopicId
        }

        return null
    }

    private fun extractTopicIdFromHref(href: String): String {
        return href.substringAfter("t=").substringBefore("&").takeIf { it.isNotBlank() } ?: ""
    }

    override fun extractTitle(row: Element): String? {
        // Try multiple selectors for title
        val titleSelectors = listOf(
            ".torrent-title a",
            ".topic-title a",
            "a[href*='viewtopic']",
            "td:nth-child(3) a",
            ".tLeft a",
        )

        for (selector in titleSelectors) {
            val titleElement = row.selectFirst(selector)
            if (titleElement != null) {
                val title = titleElement.text().trim()
                if (title.isNotBlank()) {
                    return title
                }
            }
        }

        return null
    }

    override fun extractAuthorFromTitle(title: String): String {
        // Common patterns for author extraction
        val patterns = listOf(
            Regex("^([^-]+)\\s*-"), // Author - Title
            Regex("^([^\\(]+)\\s*\\("), // Author (Narrator)
            Regex("^([^\\[]+)\\s*\\["), // Author [Series]
        )

        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                val author = match.groupValues[1].trim()
                if (author.isNotBlank()) {
                    return author
                }
            }
        }

        // Fallback: take first part before common separators
        return title.split(" - ", " (", " [").firstOrNull()?.trim() ?: ""
    }

    private fun extractSize(row: Element): String {
        // Try multiple selectors for size
        val sizeSelectors = listOf(
            ".size",
            ".torrent-size",
            "td:nth-child(5)",
            "td:nth-child(6)",
            ".tCenter:contains(MB), .tCenter:contains(GB)",
        )

        for (selector in sizeSelectors) {
            val sizeElement = row.selectFirst(selector)
            if (sizeElement != null) {
                val sizeText = sizeElement.text().trim()
                if (sizeText.matches(Regex(".*\\d+.*[KMGT]?B.*"))) {
                    return sizeText
                }
            }
        }

        return "0 MB"
    }

    private fun extractSeeders(row: Element): Int {
        return extractNumber(row, listOf(".seeders", ".seeds", "td:nth-child(6)", "td:nth-child(7)"))
    }

    private fun extractLeechers(row: Element): Int {
        return extractNumber(row, listOf(".leechers", ".leech", "td:nth-child(7)", "td:nth-child(8)"))
    }

    private fun extractNumber(row: Element, selectors: List<String>): Int {
        for (selector in selectors) {
            val element = row.selectFirst(selector)
            if (element != null) {
                val number = element.text().trim().toIntOrNull()
                if (number != null) {
                    return number
                }
            }
        }
        return 0
    }

    private fun extractAddedDate(row: Element): String {
        val dateSelectors = listOf(
            ".date",
            ".added-date",
            "td:nth-child(8)",
            "td:nth-child(9)",
            ".tCenter:last-child",
        )

        for (selector in dateSelectors) {
            val dateElement = row.selectFirst(selector)
            if (dateElement != null) {
                val dateText = dateElement.text().trim()
                if (dateText.matches(Regex("\\d{2}[.-]\\d{2}[.-]\\d{2,4}.*"))) {
                    return dateText
                }
            }
        }

        return ""
    }

    private suspend fun parseAudiobookDetailsFromDocument(doc: Document, html: String): RuTrackerAudiobook? {
        // Implementation similar to existing but with improved selectors
        // This would be a comprehensive implementation of detail parsing
        // For now, return a basic implementation

        val topicId = extractTopicIdFromDocument(doc) ?: return null
        val title = extractTitleFromDocument(doc) ?: return null

        return RuTrackerAudiobook(
            id = topicId,
            title = title,
            author = "",
            narrator = null,
            description = "",
            category = "Audiobooks",
            categoryId = "33",
            year = null,
            quality = null,
            duration = null,
            size = "0 MB",
            sizeBytes = 0L,
            magnetUri = extractMagnetLink(html),
            torrentUrl = extractTorrentLink(html),
            seeders = 0,
            leechers = 0,
            completed = 0,
            addedDate = "",
            lastUpdate = null,
            coverUrl = null,
            rating = null,
            genreList = emptyList(),
            tags = emptyList(),
            isVerified = false,
            state = parseTorrentState(html),
            downloads = 0,
            registered = null,
        )
    }

    private fun extractTopicIdFromDocument(doc: Document): String? {
        // Try canonical link first
        val canonical = doc.selectFirst("link[rel=canonical]")
        if (canonical != null) {
            val href = canonical.attr("href")
            val topicId = extractTopicIdFromHref(href)
            if (topicId.isNotBlank()) {
                return topicId
            }
        }

        // Try URL in address bar (meta)
        val urlMeta = doc.selectFirst("meta[property='og:url']")
        if (urlMeta != null) {
            val href = urlMeta.attr("content")
            val topicId = extractTopicIdFromHref(href)
            if (topicId.isNotBlank()) {
                return topicId
            }
        }

        return null
    }

    private fun extractTitleFromDocument(doc: Document): String? {
        // Try multiple selectors for page title
        val titleSelectors = listOf(
            "h1.maintitle",
            ".topic-title",
            "h1",
            "title",
        )

        for (selector in titleSelectors) {
            val titleElement = doc.selectFirst(selector)
            if (titleElement != null) {
                val title = titleElement.text().trim()
                if (title.isNotBlank() && !title.contains("RuTracker", ignoreCase = true)) {
                    return title
                }
            }
        }

        return null
    }

    private fun parseCategoryRow(row: Element): RuTrackerCategory? {
        // Implementation for category parsing
        return null // Placeholder
    }

    private fun parseSizeToBytes(size: String): Long {
        return try {
            val number = size.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            val unit = size.replace(Regex("[0-9. ]"), "").uppercase()

            when (unit) {
                "KB" -> (number * 1024).toLong()
                "MB" -> (number * 1024 * 1024).toLong()
                "GB" -> (number * 1024 * 1024 * 1024).toLong()
                "TB" -> (number * 1024 * 1024 * 1024 * 1024).toLong()
                else -> (number * 1024 * 1024).toLong() // Default to MB
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun logHtmlStructure(doc: Document) {
        try {
            val tables = doc.select("table")
            debugLogger.logDebug("RuTrackerParserImproved: Found ${tables.size} tables")

            tables.forEachIndexed { index, table ->
                val rows = table.select("tr")
                debugLogger.logDebug("RuTrackerParserImproved: Table $index has ${rows.size} rows")

                if (rows.size > 0) {
                    val firstRow = rows.first()!!
                    val cells = firstRow.select("td, th")
                    debugLogger.logDebug("RuTrackerParserImproved: First row has ${cells.size} cells")

                    cells.forEachIndexed { cellIndex, cell ->
                        val cellText = cell.text().take(50)
                        debugLogger.logDebug("RuTrackerParserImproved: Cell $cellIndex: '$cellText'")
                    }
                }
            }
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to log HTML structure", e)
        }
    }
}
override fun extractTitle(row: Element): String? {
    // Try multiple selectors for title
    val titleSelectors = listOf(
        ".torrent-title a",
        ".topic-title a",
        "a[href*='viewtopic']",
        "td:nth-child(3) a",
        ".tLeft a",
    )

    for (selector in titleSelectors) {
        val titleElement = row.selectFirst(selector)
        if (titleElement != null) {
            val title = titleElement.text().trim()
            if (title.isNotBlank()) {
                return title
            }
        }
    }

    return null
}

override fun extractAuthorFromTitle(title: String): String {
    // Common patterns for author extraction
    val patterns = listOf(
        Regex("^([^-]+)\\s*-"), // Author - Title
        Regex("^([^\\(]+)\\s*\\("), // Author (Narrator)
        Regex("^([^\\[]+)\\s*\\["), // Author [Series]
    )

    for (pattern in patterns) {
        val match = pattern.find(title)
        if (match != null) {
            val author = match.groupValues[1].trim()
            if (author.isNotBlank()) {
                return author
            }
        }
    }

    // Fallback: take first part before common separators
    return title.split(" - ", " (", " [").firstOrNull()?.trim() ?: ""
}

override fun extractSize(row: Element): String {
    // Try multiple selectors for size
    val sizeSelectors = listOf(
        ".size",
        ".torrent-size",
        "td:nth-child(5)",
        "td:nth-child(6)",
        ".tCenter:contains(MB), .tCenter:contains(GB)",
    )

    for (selector in sizeSelectors) {
        val sizeElement = row.selectFirst(selector)
        if (sizeElement != null) {
            val sizeText = sizeElement.text().trim()
            if (sizeText.matches(Regex(".*\\d+.*[KMGT]?B.*"))) {
                return sizeText
            }
        }
    }

    return "0 MB"
}

override fun extractSeeders(row: Element): Int {
    return extractNumber(row, listOf(".seeders", ".seeds", "td:nth-child(6)", "td:nth-child(7)"))
}

override fun extractLeechers(row: Element): Int {
    return extractNumber(row, listOf(".leechers", ".leech", "td:nth-child(7)", "td:nth-child(8)"))
}

private fun extractNumber(row: Element, selectors: List<String>): Int {
    for (selector in selectors) {
        val element = row.selectFirst(selector)
        if (element != null) {
            val number = element.text().trim().toIntOrNull()
            if (number != null) {
                return number
            }
        }
    }
    return 0
}

override fun extractAddedDate(row: Element): String {
    val dateSelectors = listOf(
        ".date",
        ".added-date",
        "td:nth-child(8)",
        "td:nth-child(9)",
        ".tCenter:last-child",
    )

    for (selector in dateSelectors) {
        val dateElement = row.selectFirst(selector)
        if (dateElement != null) {
            val dateText = dateElement.text().trim()
            if (dateText.matches(Regex("\\d{2}[.-]\\d{2}[.-]\\d{2,4}.*"))) {
                return dateText
            }
        }
    }

    return ""
}

private suspend fun parseAudiobookDetailsFromDocument(doc: Document, html: String): RuTrackerAudiobook? {
    // Implementation similar to existing but with improved selectors
    // This would be a comprehensive implementation of detail parsing
    // For now, return a basic implementation

    val topicId = extractTopicIdFromDocument(doc) ?: return null
    val title = extractTitleFromDocument(doc) ?: return null

    return RuTrackerAudiobook(
        id = topicId,
        title = title,
        author = "",
        narrator = null,
        description = "",
        category = "Audiobooks",
        categoryId = "33",
        year = null,
        quality = null,
        duration = null,
        size = "0 MB",
        sizeBytes = 0L,
        magnetUri = extractMagnetLink(html),
        torrentUrl = extractTorrentLink(html),
        seeders = 0,
        leechers = 0,
        completed = 0,
        addedDate = "",
        lastUpdate = null,
        coverUrl = null,
        rating = null,
        genreList = emptyList(),
        tags = emptyList(),
        isVerified = false,
        state = parseTorrentState(html),
        downloads = 0,
        registered = null,
    )
}

private fun extractTopicIdFromDocument(doc: Document): String? {
    // Try canonical link first
    val canonical = doc.selectFirst("link[rel=canonical]")
    if (canonical != null) {
        val href = canonical.attr("href")
        val topicId = extractTopicIdFromHref(href)
        if (topicId.isNotBlank()) {
            return topicId
        }
    }

    // Try URL in address bar (meta)
    val urlMeta = doc.selectFirst("meta[property='og:url']")
    if (urlMeta != null) {
        val href = urlMeta.attr("content")
        val topicId = extractTopicIdFromHref(href)
        if (topicId.isNotBlank()) {
            return topicId
        }
    }

    return null
}

private fun extractTitleFromDocument(doc: Document): String? {
    // Try multiple selectors for page title
    val titleSelectors = listOf(
        "h1.maintitle",
        ".topic-title",
        "h1",
        "title",
    )

    for (selector in titleSelectors) {
        val titleElement = doc.selectFirst(selector)
        if (titleElement != null) {
            val title = titleElement.text().trim()
            if (title.isNotBlank() && !title.contains("RuTracker", ignoreCase = true)) {
                return title
            }
        }
    }

    return null
}

private fun parseCategoryRow(row: Element): RuTrackerCategory? {
    // Implementation for category parsing
    return null // Placeholder
}

private fun parseSizeToBytes(size: String): Long {
    return try {
        val number = size.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        val unit = size.replace(Regex("[0-9. ]"), "").uppercase()

        when (unit) {
            "KB" -> (number * 1024).toLong()
            "MB" -> (number * 1024 * 1024).toLong()
            "GB" -> (number * 1024 * 1024 * 1024).toLong()
            "TB" -> (number * 1024 * 1024 * 1024 * 1024).toLong()
            else -> (number * 1024 * 1024).toLong() // Default to MB
        }
    } catch (e: Exception) {
        0L
    }
}

private fun logHtmlStructure(doc: Document) {
    try {
        val tables = doc.select("table")
        debugLogger.logDebug("RuTrackerParserImproved: Found ${tables.size} tables")

        tables.forEachIndexed { index, table ->
            val rows = table.select("tr")
            debugLogger.logDebug("RuTrackerParserImproved: Table $index has ${rows.size} rows")

            if (rows.size > 0) {
                val firstRow = rows.first()!!
                val cells = firstRow.select("td, th")
                debugLogger.logDebug("RuTrackerParserImproved: First row has ${cells.size} cells")

                cells.forEachIndexed { cellIndex, cell ->
                    val cellText = cell.text().take(50)
                    debugLogger.logDebug("RuTrackerParserImproved: Cell $cellIndex: '$cellText'")
                }
            }
        }
    } catch (e: Exception) {
        debugLogger.logError("RuTrackerParserImproved: Failed to log HTML structure", e)
    }
}
}
