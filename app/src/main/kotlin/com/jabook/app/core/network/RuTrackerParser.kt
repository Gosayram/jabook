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
 */
@Singleton
class RuTrackerParserImpl @Inject constructor(
    private val debugLogger: IDebugLogger,
) : RuTrackerParser {

    override suspend fun parseSearchResults(html: String): List<RuTrackerAudiobook> {
        return try {
            val doc = Jsoup.parse(html)
            SearchResultsParser.parseTorrentRows(doc, debugLogger)
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

    override suspend fun extractMagnetLink(html: String): String? = MagnetParser.extractMagnetLink(html, debugLogger)
    override suspend fun extractTorrentLink(html: String): String? = MagnetParser.extractTorrentLink(html, debugLogger)
    override suspend fun parseTorrentState(html: String): TorrentState = TorrentStateParser.parseTorrentState(html, debugLogger)
}

object SearchResultsParser {
    fun parseTorrentRows(doc: Document, debugLogger: IDebugLogger): List<RuTrackerAudiobook> {
        val torrentRows = doc.select("tr[id^=tr-]")
        return torrentRows.mapNotNull { RowParser.parseTorrentRow(it, debugLogger) }
    }
}

object DetailsParser {
    fun parseAudiobookDetails(doc: Document, html: String, debugLogger: IDebugLogger): RuTrackerAudiobook? {
        return try {
            val topicId = MetadataParser.extractTopicId(doc)
            if (topicId == null) {
                debugLogger.logWarning("RuTrackerParser: Could not extract topic ID")
                return null
            }
            val titleElement = doc.selectFirst("h1.maintitle")
            val title = titleElement?.text()?.trim() ?: return null
            val author = DetailsMetadataParser.extractAuthor(doc)
            val narrator = DetailsMetadataParser.extractNarrator(doc)
            val description = DetailsMetadataParser.extractDescription(doc)
            val category = DetailsMetadataParser.extractCategory(doc)
            val categoryId = DetailsMetadataParser.extractCategoryId(doc)
            val year = DetailsMetadataParser.extractYear(doc)
            val quality = DetailsMetadataParser.extractQuality(doc)
            val duration = DetailsMetadataParser.extractDuration(doc)
            val size = DetailsMetadataParser.extractSize(doc)
            val sizeBytes = DateParser.parseSizeToBytes(size)
            val seeders = DetailsMetadataParser.extractSeeders(doc)
            val leechers = DetailsMetadataParser.extractLeechers(doc)
            val completed = DetailsMetadataParser.extractCompleted(doc)
            val addedDate = DetailsMetadataParser.extractAddedDate(doc)
            val lastUpdate = DetailsMetadataParser.extractLastUpdate(doc)
            val coverUrl = DetailsMetadataParser.extractCoverUrl(doc)
            val rating = DetailsMetadataParser.extractRating(doc)
            val genreList = DetailsMetadataParser.extractGenres(doc)
            val tags = DetailsMetadataParser.extractTags(doc)
            val isVerified = DetailsMetadataParser.extractIsVerified(doc)
            val state = DetailsMetadataParser.extractTorrentState(doc)
            val downloads = DetailsMetadataParser.extractDownloads(doc)
            val registered = DetailsMetadataParser.extractRegistered(doc)
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
}

object RowParser {
    fun parseTorrentRow(row: Element, debugLogger: IDebugLogger): RuTrackerAudiobook? {
        return try {
            val cells = row.select("td")
            if (cells.size < 8) return null
            val topicId = MetadataParser.extractTopicIdFromRow(row)
            val title = MetadataParser.extractTitleFromRow(row)
            val author = MetadataParser.extractAuthorFromRow(row)
            val size = MetadataParser.extractSizeFromRow(row)
            val sizeBytes = DateParser.parseSizeToBytes(size)
            val seeders = MetadataParser.extractSeedersFromRow(row)
            val leechers = MetadataParser.extractLeechersFromRow(row)
            val addedDate = MetadataParser.extractAddedDateFromRow(row)
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
            debugLogger.logError("RuTrackerParserUtils: Failed to parse torrent row", e)
            null
        }
    }
}

object MetadataParser {
    // Helper methods for extracting data from HTML elements
    fun extractTopicIdFromRow(row: Element): String {
        val link = row.select("td.tLeft a").firstOrNull()
        return link?.attr("href")?.substringAfter("t=") ?: ""
    }

    fun extractTitleFromRow(row: Element): String {
        val titleElement = row.select("td.tLeft a").firstOrNull()
        return titleElement?.text()?.trim() ?: ""
    }

    fun extractAuthorFromRow(row: Element): String {
        // Author is usually in the title or description
        val title = extractTitleFromRow(row)
        return title.split(" - ").firstOrNull()?.trim() ?: ""
    }

    fun extractSizeFromRow(row: Element): String {
        val sizeElement = row.select("td.tCenter").getOrNull(3)
        return sizeElement?.text()?.trim() ?: "0 MB"
    }

    fun extractSeedersFromRow(row: Element): Int {
        val seedersElement = row.select("td.tCenter").getOrNull(4)
        return seedersElement?.text()?.toIntOrNull() ?: 0
    }

    fun extractLeechersFromRow(row: Element): Int {
        val leechersElement = row.select("td.tCenter").getOrNull(5)
        return leechersElement?.text()?.toIntOrNull() ?: 0
    }

    fun extractAddedDateFromRow(row: Element): String {
        val dateElement = row.select("td.tCenter").getOrNull(6)
        return dateElement?.text()?.trim() ?: ""
    }

    // Helper methods for extracting data from details page
    fun extractTopicId(doc: Document): String? {
        val url = doc.select("link[rel=canonical]").firstOrNull()?.attr("href")
        return url?.substringAfter("t=")
    }
}

object DetailsMetadataParser {
    fun extractAuthor(doc: Document): String {
        val authorElement = doc.select("td.genmed:contains(Автор:)").firstOrNull()
        return authorElement?.text()?.substringAfter("Автор:")?.trim() ?: ""
    }

    fun extractNarrator(doc: Document): String? {
        val narratorElement = doc.select("td.genmed:contains(Читает:)").firstOrNull()
        return narratorElement?.text()?.substringAfter("Читает:")?.trim()
    }

    fun extractDescription(doc: Document): String {
        val descriptionElement = doc.select("td.genmed").firstOrNull()
        return descriptionElement?.text()?.trim() ?: ""
    }

    fun extractCategory(doc: Document): String {
        val categoryElement = doc.select("td.genmed a[href*=c=]").firstOrNull()
        return categoryElement?.text()?.trim() ?: "Audiobooks"
    }

    fun extractCategoryId(doc: Document): String {
        val categoryElement = doc.select("td.genmed a[href*=c=]").firstOrNull()
        return categoryElement?.attr("href")?.substringAfter("c=") ?: "33"
    }

    fun extractYear(doc: Document): Int? {
        val yearElement = doc.select("td.genmed:contains(Год:)").firstOrNull()
        return yearElement?.text()?.substringAfter("Год:")?.trim()?.toIntOrNull()
    }

    fun extractQuality(doc: Document): String? {
        val qualityElement = doc.select("td.genmed:contains(Качество:)").firstOrNull()
        return qualityElement?.text()?.substringAfter("Качество:")?.trim()
    }

    fun extractDuration(doc: Document): String? {
        val durationElement = doc.select("td.genmed:contains(Длительность:)").firstOrNull()
        return durationElement?.text()?.substringAfter("Длительность:")?.trim()
    }

    fun extractSize(doc: Document): String {
        val sizeElement = doc.select("td.genmed:contains(Размер:)").firstOrNull()
        return sizeElement?.text()?.substringAfter("Размер:")?.trim() ?: "0 MB"
    }

    fun extractSeeders(doc: Document): Int {
        val seedersElement = doc.select("td.genmed:contains(Сидов:)").firstOrNull()
        return seedersElement?.text()?.substringAfter("Сидов:")?.trim()?.toIntOrNull() ?: 0
    }

    fun extractLeechers(doc: Document): Int {
        val leechersElement = doc.select("td.genmed:contains(Личей:)").firstOrNull()
        return leechersElement?.text()?.substringAfter("Личей:")?.trim()?.toIntOrNull() ?: 0
    }

    fun extractCompleted(doc: Document): Int {
        val completedElement = doc.select("td.genmed:contains(Завершено:)").firstOrNull()
        return completedElement?.text()?.substringAfter("Завершено:")?.trim()?.toIntOrNull() ?: 0
    }

    fun extractAddedDate(doc: Document): String {
        val dateElement = doc.select("td.genmed:contains(Добавлен:)").firstOrNull()
        return dateElement?.text()?.substringAfter("Добавлен:")?.trim() ?: ""
    }

    fun extractLastUpdate(doc: Document): String? {
        val updateElement = doc.select("td.genmed:contains(Обновлен:)").firstOrNull()
        return updateElement?.text()?.substringAfter("Обновлен:")?.trim()
    }

    fun extractCoverUrl(doc: Document): String? {
        val coverElement = doc.select("img[src*=covers]").firstOrNull()
        return coverElement?.attr("src")
    }

    fun extractRating(doc: Document): Float? {
        val ratingElement = doc.select("td.genmed:contains(Рейтинг:)").firstOrNull()
        return ratingElement?.text()?.substringAfter("Рейтинг:")?.trim()?.toFloatOrNull()
    }

    fun extractGenres(doc: Document): List<String> {
        val genreElement = doc.select("td.genmed:contains(Жанр:)").firstOrNull()
        return genreElement?.text()?.substringAfter("Жанр:")?.trim()?.split(", ") ?: emptyList()
    }

    fun extractTags(doc: Document): List<String> {
        val tagsElement = doc.select("td.genmed:contains(Теги:)").firstOrNull()
        return tagsElement?.text()?.substringAfter("Теги:")?.trim()?.split(", ") ?: emptyList()
    }

    fun extractIsVerified(doc: Document): Boolean {
        val verifiedElement = doc.select("img[src*=state]").firstOrNull()
        return verifiedElement?.attr("title")?.contains("проверено") == true
    }

    fun extractTorrentState(doc: Document): TorrentState {
        val stateElement = doc.select("img[src*=state]").firstOrNull()
        val stateText = stateElement?.attr("title") ?: ""

        return when {
            stateText.contains("проверено") -> TorrentState.APPROVED
            stateText.contains("не проверено") -> TorrentState.NOT_APPROVED
            stateText.contains("недооформлено") -> TorrentState.NEED_EDIT
            stateText.contains("сомнительно") -> TorrentState.DUBIOUSLY
            stateText.contains("поглощена") -> TorrentState.CONSUMED
            stateText.contains("временная") -> TorrentState.TEMPORARY
            else -> TorrentState.APPROVED
        }
    }

    fun extractDownloads(doc: Document): Int {
        val downloadsElement = doc.select("td.genmed:contains(Скачиваний:)").firstOrNull()
        return downloadsElement?.text()?.substringAfter("Скачиваний:")?.trim()?.toIntOrNull() ?: 0
    }

    fun extractRegistered(doc: Document): Date? {
        val registeredElement = doc.select("td.genmed:contains(Зарегистрирован:)").firstOrNull()
        val dateText = registeredElement?.text()?.substringAfter("Зарегистрирован:")?.trim()
        return dateText?.let { DateParser.parseDate(it) }
    }
}

object CategoryParser {
    fun parseCategories(doc: Document, debugLogger: IDebugLogger): List<RuTrackerCategory> {
        return try {
            val categoryElements = doc.select("tr[id^=tr-]")
            categoryElements.mapNotNull { element ->
                val idElement = element.selectFirst("td.tCenter a")
                val nameElement = element.selectFirst("td.tLeft a")
                if (idElement != null && nameElement != null) {
                    val id = idElement.attr("href").substringAfter("c=")
                    val name = nameElement.text().trim()
                    val description = nameElement.attr("title").takeIf { it.isNotEmpty() }
                    RuTrackerCategory(
                        id = id,
                        name = name,
                        description = description,
                        parentId = null,
                        isActive = true,
                        topicCount = 0, // Topic count parsing not implemented, always 0
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse categories", e)
            emptyList()
        }
    }
}

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
