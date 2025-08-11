package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.TorrentState
import com.jabook.app.shared.debug.IDebugLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker HTML Parser
 *
 * Parses HTML content from RuTracker.net to extract audiobook information
 * Handles missing data gracefully and provides fallback values
 */
interface RuTrackerParser {
    suspend fun parseSearchResults(html: String): List<RuTrackerAudiobook>
    suspend fun parseAudiobookDetails(html: String): RuTrackerAudiobook?
    suspend fun parseCategories(html: String): List<RuTrackerCategory>
    suspend fun extractMagnetLink(html: String): String?
    suspend fun extractTorrentLink(html: String): String?
    suspend fun parseTorrentState(html: String): TorrentState
}

/**
 * RuTracker HTML Parser implementation
 *
 * Based on actual HTML structure from RuTracker.net
 * Handles missing data gracefully with fallback values
 */
@Singleton
class RuTrackerParserImpl @Inject constructor(
    private val debugLogger: IDebugLogger,
) : RuTrackerParser {

    override suspend fun parseSearchResults(html: String): List<RuTrackerAudiobook> {
        return try {
            val doc = Jsoup.parse(html)
            val torrentRows = doc.select("tr[id^=tr-]")
            debugLogger.logDebug("RuTrackerParser: Found ${torrentRows.size} torrent rows in search results")

            if (torrentRows.isEmpty()) {
                debugLogger.logWarning("RuTrackerParser: No torrent rows found. HTML snippet: ${html.take(500)}")
                return emptyList()
            }

            torrentRows.mapNotNull { row ->
                try {
                    SearchResultsParser.parseTorrentRow(row, debugLogger)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerParser: Failed to parse torrent row", e)
                    null
                }
            }
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse search results", e)
            emptyList()
        }
    }

    override suspend fun parseAudiobookDetails(html: String): RuTrackerAudiobook? {
        return try {
            val doc = Jsoup.parse(html)
            DetailsParser.parseAudiobookDetails(doc, html, debugLogger)
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse audiobook details", e)
            null
        }
    }

    override suspend fun parseCategories(html: String): List<RuTrackerCategory> {
        return try {
            val doc = Jsoup.parse(html)
            CategoryParser.parseCategories(doc, debugLogger)
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse categories", e)
            emptyList()
        }
    }

    override suspend fun extractMagnetLink(html: String): String? =
        MagnetParser.extractMagnetLink(html, debugLogger)

    override suspend fun extractTorrentLink(html: String): String? =
        MagnetParser.extractTorrentLink(html, debugLogger)

    override suspend fun parseTorrentState(html: String): TorrentState =
        TorrentStateParser.parseTorrentState(html, debugLogger)
}

/**
 * Parser for search results page
 */
object SearchResultsParser {
    fun parseTorrentRow(row: Element, debugLogger: IDebugLogger): RuTrackerAudiobook? {
        return try {
            val cells = row.select("td")
            if (cells.size < 6) {
                debugLogger.logWarning("RuTrackerParser: Row has insufficient cells: ${cells.size}")
                return null
            }

            val topicId = extractTopicIdFromRow(row)
            val title = extractTitleFromRow(row)
            val author = extractAuthorFromTitle(title)
            val size = extractSizeFromRow(row)
            val sizeBytes = DateParser.parseSizeToBytes(size)
            val seeders = extractSeedersFromRow(row)
            val leechers = extractLeechersFromRow(row)
            val addedDate = extractAddedDateFromRow(row)

            if (topicId.isBlank() || title.isBlank()) {
                debugLogger.logWarning("RuTrackerParser: Missing required fields - topicId: '$topicId', title: '$title'")
                return null
            }

            RuTrackerAudiobook(
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
            debugLogger.logError("RuTrackerParser: Failed to parse torrent row", e)
            null
        }
    }

    private fun extractTopicIdFromRow(row: Element): String {
        val link = row.select("td.tLeft a").firstOrNull()
        val href = link?.attr("href") ?: ""
        return href.substringAfter("t=").substringBefore("&").takeIf { it.isNotBlank() } ?: ""
    }

    private fun extractTitleFromRow(row: Element): String {
        val titleElement = row.select("td.tLeft a").firstOrNull()
        return titleElement?.text()?.trim() ?: ""
    }

    private fun extractAuthorFromTitle(title: String): String {
        return title.split(" - ").firstOrNull()?.trim() ?: ""
    }

    private fun extractSizeFromRow(row: Element): String {
        val sizeElement = row.select("td.tCenter").getOrNull(3)
        return sizeElement?.text()?.trim() ?: "0 MB"
    }

    private fun extractSeedersFromRow(row: Element): Int {
        val seedersElement = row.select("td.tCenter").getOrNull(4)
        return seedersElement?.text()?.toIntOrNull() ?: 0
    }

    private fun extractLeechersFromRow(row: Element): Int {
        val leechersElement = row.select("td.tCenter").getOrNull(5)
        return leechersElement?.text()?.toIntOrNull() ?: 0
    }

    private fun extractAddedDateFromRow(row: Element): String {
        val dateElement = row.select("td.tCenter").getOrNull(6)
        return dateElement?.text()?.trim() ?: ""
    }
}

/**
 * Parser for audiobook details page
 * Based on actual HTML structure from RuTracker
 */
object DetailsParser {
    fun parseAudiobookDetails(doc: Document, html: String, debugLogger: IDebugLogger): RuTrackerAudiobook? {
        return try {
            val topicId = extractTopicId(doc)
            if (topicId.isBlank()) {
                debugLogger.logWarning("RuTrackerParser: Could not extract topic ID")
                return null
            }

            val title = extractTitle(doc)
            if (title.isBlank()) {
                debugLogger.logWarning("RuTrackerParser: Could not extract title")
                return null
            }

            val author = extractAuthor(doc)
            val narrator = extractNarrator(doc)
            val description = extractDescription(doc)
            val category = extractCategory(doc)
            val categoryId = extractCategoryId(doc)
            val year = extractYear(doc)
            val quality = extractQuality(doc)
            val duration = extractDuration(doc)
            val size = extractSize(doc)
            val sizeBytes = DateParser.parseSizeToBytes(size)
            val seeders = extractSeeders(doc)
            val leechers = extractLeechers(doc)
            val completed = extractCompleted(doc)
            val addedDate = extractAddedDate(doc)
            val lastUpdate = extractLastUpdate(doc)
            val coverUrl = extractCoverUrl(doc)
            val rating = extractRating(doc)
            val genreList = extractGenres(doc)
            val tags = extractTags(doc)
            val isVerified = extractIsVerified(doc)
            val state = extractTorrentState(doc)
            val downloads = extractDownloads(doc)
            val registered = extractRegistered(doc)
            val magnetUri = MagnetParser.extractMagnetLink(html, debugLogger)

            RuTrackerAudiobook(
                id = topicId,
                title = title,
                author = author,
                narrator = narrator,
                description = description,
                category = category,
                categoryId = categoryId,
                year = year,
                quality = quality,
                duration = duration,
                size = size,
                sizeBytes = sizeBytes,
                magnetUri = magnetUri,
                torrentUrl = null,
                seeders = seeders,
                leechers = leechers,
                completed = completed,
                addedDate = addedDate,
                lastUpdate = lastUpdate,
                coverUrl = coverUrl,
                rating = rating,
                genreList = genreList,
                tags = tags,
                isVerified = isVerified,
                state = state,
                downloads = downloads,
                registered = registered,
            )
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse audiobook details", e)
            null
        }
    }

    private fun extractTopicId(doc: Document): String {
        // Try multiple selectors for topic ID
        val selectors = listOf(
            "link[rel=canonical]",
            "a[href*='viewtopic.php?t=']",
            "h1.maintitle a",
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            val href = element?.attr("href") ?: ""
            val topicId = href.substringAfter("t=").substringBefore("&").takeIf { it.isNotBlank() }
            if (!topicId.isNullOrBlank()) {
                return topicId
            }
        }

        return ""
    }

    private fun extractTitle(doc: Document): String {
        // Try multiple selectors for title
        val selectors = listOf(
            "h1.maintitle a",
            "h1.maintitle",
            "title",
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            val title = element?.text()?.trim()?.takeIf { it.isNotBlank() }
            if (!title.isNullOrBlank()) {
                return title
            }
        }

        return ""
    }

    private fun extractAuthor(doc: Document): String {
        // Try multiple approaches for author extraction
        val authorSelectors = listOf(
            "span.post-b:contains(Автор:) + br + *",
            "span.post-b:contains(Автор:)",
            "span.post-b:contains(Автор)",
            "div.post_body:contains(Автор:)",
            "div.post_body:contains(Автор)",
        )

        for (selector in authorSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val author = text.substringAfter("Автор:").substringAfter("Автор").trim()
            if (author.isNotBlank()) {
                return author
            }
        }

        // Fallback: try to extract from title
        val title = extractTitle(doc)
        return title.split(" - ").firstOrNull()?.trim() ?: ""
    }

    private fun extractNarrator(doc: Document): String? {
        val narratorSelectors = listOf(
            "span.post-b:contains(Читает:) + br + *",
            "span.post-b:contains(Читает:)",
            "div.post_body:contains(Читает:)",
            "div.post_body:contains(Читает)",
        )

        for (selector in narratorSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val narrator = text.substringAfter("Читает:").trim()
            if (narrator.isNotBlank()) {
                return narrator
            }
        }

        return null
    }

    private fun extractDescription(doc: Document): String {
        val descriptionSelectors = listOf(
            "div.post_body",
            "div.post_body span.post-i",
            "div.post_body span.post-b:contains(Описание:) + *",
        )

        for (selector in descriptionSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text()?.trim()
            if (!text.isNullOrBlank()) {
                return text
            }
        }

        return ""
    }

    private fun extractCategory(doc: Document): String {
        val categorySelectors = listOf(
            "td.nav a[href*='c=']",
            "td.nav a[href*='f=']",
            "div.t-breadcrumb-top a",
        )

        for (selector in categorySelectors) {
            val element = doc.selectFirst(selector)
            val category = element?.text()?.trim()
            if (!category.isNullOrBlank()) {
                return category
            }
        }

        return "Audiobooks"
    }

    private fun extractCategoryId(doc: Document): String {
        val categorySelectors = listOf(
            "td.nav a[href*='c=']",
            "td.nav a[href*='f=']",
            "div.t-breadcrumb-top a",
        )

        for (selector in categorySelectors) {
            val element = doc.selectFirst(selector)
            val href = element?.attr("href") ?: ""
            val categoryId = href.substringAfter("c=").substringAfter("f=").substringBefore("&").takeIf { it.isNotBlank() }
            if (!categoryId.isNullOrBlank()) {
                return categoryId
            }
        }

        return "33"
    }

    private fun extractYear(doc: Document): Int? {
        val yearSelectors = listOf(
            "span.post-b:contains(Год выпуска:) + *",
            "span.post-b:contains(Год:)",
            "div.post_body:contains(Год выпуска:)",
            "div.post_body:contains(Год:)",
        )

        for (selector in yearSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val yearText = text.substringAfter("Год выпуска:").substringAfter("Год:").trim()
            val year = yearText.toIntOrNull()
            if (year != null) {
                return year
            }
        }

        return null
    }

    private fun extractQuality(doc: Document): String? {
        val qualitySelectors = listOf(
            "span.post-b:contains(Битрейт аудио:) + *",
            "span.post-b:contains(Качество:)",
            "div.post_body:contains(Битрейт аудио:)",
            "div.post_body:contains(Качество:)",
        )

        for (selector in qualitySelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val quality = text.substringAfter("Битрейт аудио:").substringAfter("Качество:").trim()
            if (quality.isNotBlank()) {
                return quality
            }
        }

        return null
    }

    private fun extractDuration(doc: Document): String? {
        val durationSelectors = listOf(
            "span.post-b:contains(Продолжительность:) + *",
            "span.post-b:contains(Длительность:)",
            "div.post_body:contains(Продолжительность:)",
            "div.post_body:contains(Длительность:)",
        )

        for (selector in durationSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val duration = text.substringAfter("Продолжительность:").substringAfter("Длительность:").trim()
            if (duration.isNotBlank()) {
                return duration
            }
        }

        return null
    }

    private fun extractSize(doc: Document): String {
        // Try to extract from magnet link info
        val magnetInfo = doc.select("div.attach_link li").text()
        val sizeMatch = Regex("(\\d+(\\.\\d+)?\\s*[KMGT]?B)").find(magnetInfo)
        if (sizeMatch != null) {
            return sizeMatch.value
        }

        return "0 MB"
    }

    private fun extractSeeders(doc: Document): Int {
        // Seeders/leechers info is not typically shown on details page
        return 0
    }

    private fun extractLeechers(doc: Document): Int {
        // Seeders/leechers info is not typically shown on details page
        return 0
    }

    private fun extractCompleted(doc: Document): Int {
        // Completed downloads info is not typically shown on details page
        return 0
    }

    private fun extractAddedDate(doc: Document): String {
        val dateSelectors = listOf(
            "a.p-link.small",
            "span.post-time a",
        )

        for (selector in dateSelectors) {
            val element = doc.selectFirst(selector)
            val date = element?.text()?.trim()
            if (!date.isNullOrBlank()) {
                return date
            }
        }

        return ""
    }

    private fun extractLastUpdate(doc: Document): String? {
        // Last update info is not typically shown on details page
        return null
    }

    private fun extractCoverUrl(doc: Document): String? {
        val coverSelectors = listOf(
            "var.postImg[title*='.jpg']",
            "var.postImg[title*='.png']",
            "img[src*='fastpic.ru']",
            "img[src*='covers']",
        )

        for (selector in coverSelectors) {
            val element = doc.selectFirst(selector)
            val src = element?.attr("src")
            val title = element?.attr("title")
            val url = src ?: title
            if (!url.isNullOrBlank()) {
                return url
            }
        }

        return null
    }

    private fun extractRating(doc: Document): Float? {
        // Rating info is not typically shown on details page
        return null
    }

    private fun extractGenres(doc: Document): List<String> {
        val genreSelectors = listOf(
            "span.post-b:contains(Жанр:) + *",
            "span.post-b:contains(Жанр:)",
            "div.post_body:contains(Жанр:)",
        )

        for (selector in genreSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val genreText = text.substringAfter("Жанр:").trim()
            if (genreText.isNotBlank()) {
                return genreText.split(", ").filter { it.isNotBlank() }
            }
        }

        return emptyList()
    }

    private fun extractTags(doc: Document): List<String> {
        // Tags are not typically shown on details page
        return emptyList()
    }

    private fun extractIsVerified(doc: Document): Boolean {
        // Verification status is not typically shown on details page
        return false
    }

    private fun extractTorrentState(doc: Document): TorrentState {
        // Torrent state is not typically shown on details page
        return TorrentState.APPROVED
    }

    private fun extractDownloads(doc: Document): Int {
        // Download count is not typically shown on details page
        return 0
    }

    private fun extractRegistered(doc: Document): Date? {
        // Registration date is not typically shown on details page
        return null
    }
}

/**
 * Parser for categories
 */
object CategoryParser {
    /**
     * Parse categories from HTML:
     * - Current category: <h1 class="maintitle"><a ...>...</a></h1>
     * - Breadcrumb: <td class="nav ..."> ... <a ...>...</a> ... </td>
     * Returns a chain of categories (from root to current).
     */
    fun parseCategories(doc: Document, debugLogger: IDebugLogger): List<RuTrackerCategory> {
        return try {
            val categories = mutableListOf<RuTrackerCategory>()

            // 1. Хлебные крошки (навигация)
            val navTd = doc.selectFirst("td.nav")
            if (navTd != null) {
                val navLinks = navTd.select("a")
                var parentId: String? = null
                navLinks.forEach { link ->
                    val href = link.attr("href")
                    val id = when {
                        href.contains("c=") -> href.substringAfter("c=").substringBefore("&")
                        href.contains("f=") -> href.substringAfter("f=").substringBefore("&")
                        else -> null
                    }
                    val name = link.text().trim()
                    if (!id.isNullOrBlank() && name.isNotBlank()) {
                        categories.add(
                            RuTrackerCategory(
                                id = id,
                                name = name,
                                description = null,
                                parentId = parentId,
                                isActive = true,
                                topicCount = 0,
                            ),
                        )
                        parentId = id
                    }
                }
            }

            // 2. Current category (title)
            val titleLink = doc.selectFirst("h1.maintitle a")
            if (titleLink != null) {
                val href = titleLink.attr("href")
                val id = when {
                    href.contains("c=") -> href.substringAfter("c=").substringBefore("&")
                    href.contains("f=") -> href.substringAfter("f=").substringBefore("&")
                    else -> null
                }
                val name = titleLink.text().trim()
                val parentId = categories.lastOrNull()?.id
                if (!id.isNullOrBlank() && name.isNotBlank() && categories.none { it.id == id }) {
                    categories.add(
                        RuTrackerCategory(
                            id = id,
                            name = name,
                            description = null,
                            parentId = parentId,
                            isActive = true,
                            topicCount = 0,
                        ),
                    )
                }
            }

            // 3. Fallback: old parsing (by rows)
            if (categories.isEmpty()) {
                val categoryElements = doc.select("tr[id^=tr-]")
                categoryElements.mapNotNullTo(categories) { element ->
                    try {
                        val idElement = element.selectFirst("td.tCenter a")
                        val nameElement = element.selectFirst("td.tLeft a")
                        if (idElement != null && nameElement != null) {
                            val href = idElement.attr("href")
                            val id = href.substringAfter("c=").substringBefore("&").takeIf { it.isNotBlank() } ?: ""
                            val name = nameElement.text().trim()
                            val description = nameElement.attr("title").takeIf { it.isNotEmpty() }
                            if (id.isNotBlank() && name.isNotBlank()) {
                                RuTrackerCategory(
                                    id = id,
                                    name = name,
                                    description = description,
                                    parentId = null,
                                    isActive = true,
                                    topicCount = 0,
                                )
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        debugLogger.logError("RuTrackerParser: Failed to parse category element", e)
                        null
                    }
                }
            }

            categories
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse categories", e)
            emptyList()
        }
    }
}

/**
 * Parser for magnet and torrent links
 */
object MagnetParser {
    fun extractMagnetLink(html: String, debugLogger: IDebugLogger): String? {
        return try {
            val doc = Jsoup.parse(html)
            val magnetLink = doc.select("a[href^=magnet:]").firstOrNull()
            magnetLink?.attr("href")
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to extract magnet link", e)
            null
        }
    }

    fun extractTorrentLink(html: String, debugLogger: IDebugLogger): String? {
        return try {
            val doc = Jsoup.parse(html)
            val torrentLink = doc.select("a[href^=dl.php]").firstOrNull()
            torrentLink?.attr("href")
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to extract torrent link", e)
            null
        }
    }
}

/**
 * Parser for torrent state
 */
object TorrentStateParser {
    fun parseTorrentState(html: String, debugLogger: IDebugLogger): TorrentState {
        return try {
            val doc = Jsoup.parse(html)
            val stateText = doc.select("td.tCenter img[src*=state]").firstOrNull()?.attr("title") ?: ""
            when {
                stateText.contains("проверено") -> TorrentState.APPROVED
                stateText.contains("не проверено") -> TorrentState.NOT_APPROVED
                stateText.contains("недооформлено") -> TorrentState.NEED_EDIT
                stateText.contains("сомнительно") -> TorrentState.DUBIOUSLY
                stateText.contains("поглощена") -> TorrentState.CONSUMED
                stateText.contains("временная") -> TorrentState.TEMPORARY
                else -> TorrentState.APPROVED
            }
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse torrent state", e)
            TorrentState.APPROVED
        }
    }
}

/**
 * Utility parser for dates and sizes
 */
object DateParser {
    fun parseSizeToBytes(size: String): Long {
        return try {
            val number = size.replace(Regex("[^0-9.]"), "").toDouble()
            val unit = size.replace(Regex("[0-9. ]"), "").uppercase()

            when (unit) {
                "KB" -> (number * 1024).toLong()
                "MB" -> (number * 1024 * 1024).toLong()
                "GB" -> (number * 1024 * 1024 * 1024).toLong()
                else -> (number * 1024 * 1024).toLong() // Default to MB
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun parseDate(dateText: String): Date? {
        return try {
            // Simple date parsing - can be enhanced
            java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).parse(dateText)
        } catch (e: Exception) {
            null
        }
    }
}
