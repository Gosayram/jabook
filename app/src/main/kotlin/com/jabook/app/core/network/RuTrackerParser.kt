package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.TorrentState
import com.jabook.app.core.network.extractors.AuthorExtractor
import com.jabook.app.core.network.extractors.CategoryExtractor
import com.jabook.app.core.network.extractors.CoverExtractor
import com.jabook.app.core.network.extractors.DateExtractor
import com.jabook.app.core.network.extractors.DescriptionExtractor
import com.jabook.app.core.network.extractors.DetailsExtractor
import com.jabook.app.core.network.extractors.DownloadsExtractor
import com.jabook.app.core.network.extractors.GenreExtractor
import com.jabook.app.core.network.extractors.LastUpdateExtractor
import com.jabook.app.core.network.extractors.RatingExtractor
import com.jabook.app.core.network.extractors.RegisteredExtractor
import com.jabook.app.core.network.extractors.SeedersExtractor
import com.jabook.app.core.network.extractors.SizeExtractor
import com.jabook.app.core.network.extractors.StateExtractor
import com.jabook.app.core.network.extractors.TagsExtractor
import com.jabook.app.core.network.TitleExtractor
import com.jabook.app.core.network.TopicIdExtractor
import com.jabook.app.core.network.VerificationExtractor
import com.jabook.app.shared.debug.IDebugLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * Parser for audiobook details page
 * Based on actual HTML structure from RuTracker
 */
object RuTrackerParser {
    private const val DEFAULT_SEEDERS = "0"
    private const val DEFAULT_LEECHERS = "0"
    private const val DEFAULT_COMPLETED = "0"
    private const val DEFAULT_LAST_UPDATE = ""
    private const val DEFAULT_RATING = ""
    private const val DEFAULT_IS_VERIFIED = false
    private const val DEFAULT_DOWNLOADS = "0"
    private const val DEFAULT_REGISTERED = ""

    fun parseAudiobookDetails(
        doc: Document,
        html: String,
        debugLogger: IDebugLogger,
    ): RuTrackerAudiobook? {
        return try {
            val topicId = TopicIdExtractor.extractTopicId(doc)
            if (topicId.isBlank()) {
                debugLogger.logWarning("RuTrackerParser: Could not extract topic ID")
                return null
            }

            val title = TitleExtractor.extractTitle(doc)
            if (title.isBlank()) {
                debugLogger.logWarning("RuTrackerParser: Could not extract title")
                return null
            }

            val author = AuthorExtractor.extractAuthor(doc)
            val narrator = AuthorExtractor.extractNarrator(doc)
            val description = DescriptionExtractor.extractDescription(doc)
            val category = CategoryExtractor.extractCategory(doc)
            val categoryId = CategoryExtractor.extractCategoryId(doc)
            val year = DetailsExtractor.extractYear(doc)
            val quality = QualityExtractor.DEFAULT_QUALITY
            val duration = DurationExtractor.DEFAULT_DURATION
            val size = SizeExtractor.extractSize(doc)
            val sizeBytes = DateParser.parseSizeToBytes(size)
            val seeders = DEFAULT_SEEDERS
            val leechers = DEFAULT_LEECHERS
            val completed = DEFAULT_COMPLETED
            val addedDate = DateExtractor.extractAddedDate(doc)
            val lastUpdate = LastUpdateExtractor.DEFAULT_LAST_UPDATE
            val coverUrl = CoverExtractor.extractCoverUrl(doc)
            val rating = RatingExtractor.DEFAULT_RATING
            val genreList = GenreExtractor.extractGenres(doc)
            val tags = TagsExtractor.extractTags(doc)
            val isVerified = VerificationExtractor.DEFAULT_IS_VERIFIED
            val state = StateExtractor.extractTorrentState(doc)
            val downloads = DownloadsExtractor.DEFAULT_DOWNLOADS
            val registered = RegisteredExtractor.DEFAULT_REGISTERED

            RuTrackerAudiobook(
                id = topicId,
                title = title,
                author = author,
                narrator = narrator,
                description = description,
                category = category,
                categoryId = categoryId,
                year = year.toIntOrNull() ?: 0,
                quality = quality,
                duration = duration,
                size = size,
                sizeBytes = sizeBytes,
                magnetUri = MagnetParser.extractMagnetLink(html, debugLogger),
                torrentUrl = null,
                seeders = seeders.toIntOrNull() ?: 0,
                leechers = leechers.toIntOrNull() ?: 0,
                completed = completed.toIntOrNull() ?: 0,
                addedDate = DateParser.parseDate(addedDate),
                lastUpdate = lastUpdate,
                coverUrl = coverUrl,
                rating = rating.toFloatOrNull(),
                genreList = genreList,
                tags = tags,
                isVerified = isVerified,
                state = state,
                downloads = downloads.toIntOrNull() ?: 0,
                registered = registered,
            )
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse audiobook details", e)
            null
        }
    }

    /**
     * Parser for categories
     */
    fun parseCategories(doc: Document, debugLogger: IDebugLogger): List<RuTrackerCategory> {
        return try {
            val categories = mutableListOf<RuTrackerCategory>()

            parseBreadcrumbCategories(doc, categories)

            parseCurrentCategory(doc, categories)

            if (categories.isEmpty()) {
                parseFallbackCategories(doc, categories, debugLogger)
            }

            categories
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse categories", e)
            emptyList()
        }
    }

    private fun parseBreadcrumbCategories(doc: Document, categories: MutableList<RuTrackerCategory>) {
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
    }

    private fun parseCurrentCategory(doc: Document, categories: MutableList<RuTrackerCategory>) {
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
    }

    private fun parseFallbackCategories(doc: Document, categories: MutableList<RuTrackerCategory>, debugLogger: IDebugLogger) {
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
            val state = when {
                stateText.contains("проверено") -> TorrentState.APPROVED
                stateText.contains("не проверено") -> TorrentState.NOT_APPROVED
                stateText.contains("недооформлено") -> TorrentState.NEED_EDIT
                stateText.contains("сомнительно") -> TorrentState.DUBIOUSLY
                stateText.contains("поглощена") -> TorrentState.CONSUMED
                stateText.contains("временная") -> TorrentState.TEMPORARY
                else -> TorrentState.APPROVED
            }
            return state
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse torrent state", e)
            return TorrentState.APPROVED
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
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText)
        } catch (e: Exception) {
            null
        }
    }
}

object QualityExtractor {
    const val DEFAULT_QUALITY = ""
}

object DurationExtractor {
    const val DEFAULT_DURATION = ""
}

object LastUpdateExtractor {
    const val DEFAULT_LAST_UPDATE = ""
}

object RatingExtractor {
    const val DEFAULT_RATING = ""
}

object VerificationExtractor {
    const val DEFAULT_IS_VERIFIED = false
}

object DownloadsExtractor {
    const val DEFAULT_DOWNLOADS = "0"
}

object RegisteredExtractor {
    const val DEFAULT_REGISTERED = ""
}
</content>
</write_to_file>
```
     * Parse categories from HTML:
     * - Current category: <h1 class="maintitle"><a ...>...</a></h1>
     * - Breadcrumb: <td class="nav ..."> ... <a ...>...</a> ... </td>
     * Returns a chain of categories (from root to current).
     */
    fun parseCategories(doc: Document, debugLogger: IDebugLogger): List<RuTrackerCategory> {
        return try {
            val categories = mutableListOf<RuTrackerCategory>()

            parseBreadcrumbCategories(doc, categories)

            parseCurrentCategory(doc, categories)

            if (categories.isEmpty()) {
                parseFallbackCategories(doc, categories, debugLogger)
            }

            categories
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse categories", e)
            emptyList()
        }
    }

    private fun parseBreadcrumbCategories(doc: Document, categories: MutableList<RuTrackerCategory>) {
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
    }

    private fun parseCurrentCategory(doc: Document, categories: MutableList<RuTrackerCategory>) {
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
    }

    private fun parseFallbackCategories(doc: Document, categories: MutableList<RuTrackerCategory>, debugLogger: IDebugLogger) {
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
            val state = when {
                stateText.contains("проверено") -> TorrentState.APPROVED
                stateText.contains("не проверено") -> TorrentState.NOT_APPROVED
                stateText.contains("недооформлено") -> TorrentState.NEED_EDIT
                stateText.contains("сомнительно") -> TorrentState.DUBIOUSLY
                stateText.contains("поглощена") -> TorrentState.CONSUMED
                stateText.contains("временная") -> TorrentState.TEMPORARY
                else -> TorrentState.APPROVED
            }
            return state
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParser: Failed to parse torrent state", e)
            return TorrentState.APPROVED
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
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText)
        } catch (e: Exception) {
            null
        }
    }
}

object QualityExtractor {
    const val DEFAULT_QUALITY = ""
}

object DurationExtractor {
    const val DEFAULT_DURATION = ""
}

object LastUpdateExtractor {
    const val DEFAULT_LAST_UPDATE = ""
}

object RatingExtractor {
    const val DEFAULT_RATING = ""
}

object VerificationExtractor {
    const val DEFAULT_IS_VERIFIED = false
}

object DownloadsExtractor {
    const val DEFAULT_DOWNLOADS = "0"
}

object RegisteredExtractor {
    const val DEFAULT_REGISTERED = ""
}
