package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.shared.debug.IDebugLogger
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Improved RuTracker HTML Parser with updated selectors
 *
 * This parser uses current RuTracker HTML structure and includes
 * better error handling and fallback mechanisms
 */
@Singleton
class RuTrackerParser @Inject constructor(
    private val debugLogger: IDebugLogger,
) {

    suspend fun parseSearchResults(html: String): List<RuTrackerAudiobook> {
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

    suspend fun parseAudiobookDetails(html: String): RuTrackerAudiobook? {
        return try {
            debugLogger.logDebug("RuTrackerParserImproved: Starting to parse audiobook details")

            if (isErrorPage(html)) {
                debugLogger.logWarning("RuTrackerParserImproved: Error page detected in details")
                return null
            }

            val doc = Jsoup.parse(html)
            return parseAudiobookDetailsFromDocument(doc, html)
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerParserImproved: Failed to parse audiobook details", e)
            null
        }
    }

    suspend fun parseCategories(html: String): List<RuTrackerCategory> {
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

    suspend fun extractMagnetLink(html: String): String? {
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

    suspend fun extractTorrentLink(html: String): String? {
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
            return null
        }
    }
}
