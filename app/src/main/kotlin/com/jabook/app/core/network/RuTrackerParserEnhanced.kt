package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.exceptions.RuTrackerException.ParseException
import com.jabook.app.core.network.models.RuTrackerCategory
import com.jabook.app.core.network.models.RuTrackerSearchResult
import com.jabook.app.core.network.models.RuTrackerTorrentDetails
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced RuTracker Parser with fallback selectors and robust error handling
 */
@Singleton
class RuTrackerParserEnhanced
  @Inject
  constructor(
    private val debugLogger: IDebugLogger,
  ) {
    companion object {
      // Character encoding patterns
      private val CHARSET_PATTERN = Pattern.compile("charset=([^\"]+)")
      private val META_CHARSET_PATTERN = Pattern.compile("charset=([^\"]+)")

      // Date formats
      private val DATE_FORMATS =
        listOf(
          SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
          SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()),
          SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()),
          SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
          SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
          SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()),
        )

      // Size patterns
      private val SIZE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(KB|MB|GB|TB)", Pattern.CASE_INSENSITIVE)

      // Duration patterns
      private val DURATION_PATTERN = Pattern.compile("(\\d+):(\\d+):(\\d+)")
      private val DURATION_MINUTES_PATTERN = Pattern.compile("(\\d+):(\\d+)")
    }

    /**
     * Parse search results with multiple fallback selectors
     */
    suspend fun parseSearchResults(html: String): List<RuTrackerSearchResult> =
      withContext(Dispatchers.Default) {
        try {
          val document = parseHtml(html)
          val results = mutableListOf<RuTrackerSearchResult>()

          // Try multiple selectors for search results table
          val searchRows =
            tryMultipleSelectors(
              document,
              listOf(
                "table.forumline tr:has(a.tLink)",
                "table.forumline tr:has(a.topiclink)",
                "table.forumline tr:has(a[href*=\"viewtopic.php\"])",
                "table.forumline tr:not(:has(th))",
                "table.torrent-list tr",
                "table.search-results tr",
              ),
            )

          if (searchRows.isEmpty()) {
            debugLogger.logWarning("RuTrackerParserEnhanced: No search results found with any selector")
            return@withContext emptyList()
          }

          for (row in searchRows) {
            try {
              val result = parseSearchResultRow(row)
              if (result != null) {
                results.add(result)
              }
            } catch (e: Exception) {
              debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse search result row", e)
              // Continue with next row
            }
          }

          debugLogger.logDebug("RuTrackerParserEnhanced: Parsed ${results.size} search results")
          results
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerParserEnhanced: Failed to parse search results", e)
          throw ParseException("Failed to parse search results: ${e.message}") as Throwable
        }
      }

    /**
     * Parse torrent details with multiple fallback selectors
     */
    suspend fun parseTorrentDetails(html: String): RuTrackerTorrentDetails =
      withContext(Dispatchers.Default) {
        try {
          val document = parseHtml(html)

          val title = parseTitle(document)
          val author = parseAuthor(document)
          val description = parseDescription(document)
          val category = parseCategory(document)
          val size = parseSize(document)
          val seeders = parseSeeders(document)
          val leechers = parseLeechers(document)
          val downloads = parseDownloads(document)
          val addedDate = parseAddedDate(document)
          val magnetLink = parseMagnetLink(document)
          val fileList = parseFileList(document)

          val details =
            RuTrackerTorrentDetails(
              title = title,
              author = author,
              description = description,
              category = category,
              size = size,
              seeders = seeders,
              leechers = leechers,
              downloads = downloads,
              addedDate = addedDate,
              magnetLink = magnetLink,
              fileList = fileList,
            )

          debugLogger.logDebug("RuTrackerParserEnhanced: Parsed torrent details for: $title")
          details
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerParserEnhanced: Failed to parse torrent details", e)
          throw ParseException("Failed to parse torrent details: ${e.message}")
        }
      }

    /**
     * Parse categories with multiple fallback selectors
     */
    suspend fun parseCategories(html: String): List<RuTrackerCategory> =
      withContext(Dispatchers.Default) {
        try {
          val document = parseHtml(html)
          val categories = mutableListOf<RuTrackerCategory>()

          // Try multiple selectors for category links
          val categoryLinks =
            tryMultipleSelectors(
              document,
              listOf(
                "a[href*=\"index.php?c=\"]",
                "table.forumline a[href*=\"index.php?c=\"]",
                "div.category-list a[href*=\"index.php?c=\"]",
                "a.catTitle[href*=\"index.php?c=\"]",
              ),
            )

          if (categoryLinks.isEmpty()) {
            debugLogger.logWarning("RuTrackerParserEnhanced: No categories found with any selector")
            return@withContext emptyList()
          }

          for (link in categoryLinks) {
            try {
              val category = parseCategoryLink(link)
              if (category != null) {
                categories.add(category)
              }
            } catch (e: Exception) {
              debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse category link", e)
              // Continue with next category
            }
          }

          debugLogger.logDebug("RuTrackerParserEnhanced: Parsed ${categories.size} categories")
          categories
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerParserEnhanced: Failed to parse categories", e)
          throw ParseException("Failed to parse categories: ${e.message}")
        }
      }

    /**
     * Parse magnet link with multiple fallback selectors
     */
    suspend fun parseMagnetLink(html: String): String? =
      withContext(Dispatchers.Default) {
        try {
          val document = parseHtml(html)

          // Try multiple selectors for magnet link
          val magnetElements =
            tryMultipleSelectors(
              document,
              listOf(
                "a[href^=\"magnet:\"]",
                "a.magnet-link",
                "a[title*=\"magnet\"]",
                "div.attach_link a[href^=\"magnet:\"]",
                "a:containsOwn(magnet)",
              ),
            )

          if (magnetElements.isEmpty()) {
            debugLogger.logWarning("RuTrackerParserEnhanced: No magnet link found with any selector")
            return@withContext null
          }

          val magnetLink = magnetElements.first().attr("href")

          if (magnetLink.isNullOrEmpty() || !magnetLink.startsWith("magnet:")) {
            debugLogger.logWarning("RuTrackerParserEnhanced: Invalid magnet link format")
            return@withContext null
          }

          debugLogger.logDebug("RuTrackerParserEnhanced: Parsed magnet link")
          magnetLink
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerParserEnhanced: Failed to parse magnet link", e)
          null
        }
      }

    /**
     * Parse HTML with charset detection
     */
    private suspend fun parseHtml(html: String): Document =
      withContext(Dispatchers.Default) {
        try {
          // Detect charset
          val charset = detectCharset(html)

          // Parse with detected charset
          val document = Jsoup.parse(html)

          // Set document charset
          document.outputSettings().charset(charset)

          document
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerParserEnhanced: Failed to parse HTML", e)
          throw ParseException("Failed to parse HTML: ${e.message}")
        }
      }

    /**
     * Detect charset from HTML
     */
    private fun detectCharset(html: String): Charset {
      try {
        // Try to find charset in meta tags
        val matcher = CHARSET_PATTERN.matcher(html)
        if (matcher.find()) {
          val charsetName = matcher.group(1)
          try {
            return Charset.forName(charsetName)
          } catch (e: Exception) {
            // Continue to fallback
          }
        }

        // Try meta charset pattern
        val metaMatcher = META_CHARSET_PATTERN.matcher(html)
        if (metaMatcher.find()) {
          val charsetName = metaMatcher.group(1)
          try {
            return Charset.forName(charsetName)
          } catch (e: Exception) {
            // Continue to fallback
          }
        }

        // Fallback to Windows-1251 (common for RuTracker)
        return Charset.forName("Windows-1251")
      } catch (e: Exception) {
        // Ultimate fallback
        return StandardCharsets.UTF_8
      }
    }

    /**
     * Try multiple selectors and return first non-empty result
     */
    private fun tryMultipleSelectors(
      document: Document,
      selectors: List<String>,
    ): Elements {
      for (selector in selectors) {
        try {
          val elements = document.select(selector)
          if (elements.isNotEmpty()) {
            debugLogger.logDebug("RuTrackerParserEnhanced: Selector '$selector' found ${elements.size} elements")
            return elements
          }
        } catch (e: Exception) {
          debugLogger.logWarning("RuTrackerParserEnhanced: Selector '$selector' failed", e)
          // Continue with next selector
        }
      }

      debugLogger.logWarning("RuTrackerParserEnhanced: No selectors found any elements")
      return Elements()
    }

    /**
     * Parse search result row
     */
    private fun parseSearchResultRow(row: Element): RuTrackerSearchResult? {
      try {
        // Extract topic ID
        val topicLink =
          row.select("a[href*=\"viewtopic.php?\"]").firstOrNull()
            ?: return null

        val href = topicLink.attr("href")
        val topicId =
          extractTopicId(href)
            ?: return null

        // Extract title
        val title =
          topicLink
            .text()
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: return null

        // Extract author
        val author =
          row
            .select("td:has(a[href*=\"profile.php\"])")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?: "Unknown"

        // Extract size
        val sizeText =
          row
            .select("td")
            .find { td ->
              td.text().contains("KB", ignoreCase = true) ||
                td.text().contains("MB", ignoreCase = true) ||
                td.text().contains("GB", ignoreCase = true)
            }?.text()
            ?.trim() ?: "0 KB"

        val size = parseSizeText(sizeText)

        // Extract seeders and leechers
        val seeders =
          row
            .select("td")
            .find { td ->
              td.text().matches(Regex("\\d+"))
            }?.text()
            ?.trim()
            ?.toIntOrNull() ?: 0

        val leechers =
          row
            .select("td")
            .find { td ->
              td.text().matches(Regex("\\d+"))
            }?.text()
            ?.trim()
            ?.toIntOrNull() ?: 0

        // Extract downloads
        val downloads =
          row
            .select("td")
            .find { td ->
              td.text().matches(Regex("\\d+"))
            }?.text()
            ?.trim()
            ?.toIntOrNull() ?: 0

        return RuTrackerSearchResult(
          topicId = topicId,
          title = title,
          author = author,
          size = size,
          seeders = seeders,
          leechers = leechers,
          downloads = downloads,
        )
      } catch (e: Exception) {
        debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse search result row", e)
        return null
      }
    }

    /**
     * Parse title with multiple fallback selectors
     */
    private fun parseTitle(document: Document): String =
      tryMultipleTextSelectors(
        document,
        listOf(
          "h1.maintitle",
          "h1",
          "title",
          ".post-title",
          ".topic-title",
        ),
      ) ?: "Unknown Title"

    /**
     * Parse author with multiple fallback selectors
     */
    private fun parseAuthor(document: Document): String =
      tryMultipleTextSelectors(
        document,
        listOf(
          ".post_body:contains(Автор)",
          ".post_body:contains(Автор) + *",
          ".author-name",
          ".post-author",
        ),
      ) ?: "Unknown Author"

    /**
     * Parse description with multiple fallback selectors
     */
    private fun parseDescription(document: Document): String =
      tryMultipleTextSelectors(
        document,
        listOf(
          ".post-i",
          ".post_body",
          ".description",
          ".topic-description",
        ),
      ) ?: ""

    /**
     * Parse category with multiple fallback selectors
     */
    private fun parseCategory(document: Document): String =
      tryMultipleTextSelectors(
        document,
        listOf(
          ".nav:contains(Аудиокниги)",
          ".breadcrumb:contains(Аудиокниги)",
          ".category-name",
        ),
      ) ?: "Аудиокниги"

    /**
     * Parse size with multiple fallback selectors
     */
    private fun parseSize(document: Document): Long {
      val sizeText =
        tryMultipleTextSelectors(
          document,
          listOf(
            ".tor-size",
            ".torrent-size",
            ".size:contains(MB)",
            ".size:contains(GB)",
          ),
        ) ?: "0 MB"

      return parseSizeText(sizeText)
    }

    /**
     * Parse seeders with multiple fallback selectors
     */
    private fun parseSeeders(document: Document): Int {
      val seedersText =
        tryMultipleTextSelectors(
          document,
          listOf(
            ".seedmed",
            ".seeders",
            ".seeds",
          ),
        ) ?: "0"

      return seedersText.toIntOrNull() ?: 0
    }

    /**
     * Parse leechers with multiple fallback selectors
     */
    private fun parseLeechers(document: Document): Int {
      val leechersText =
        tryMultipleTextSelectors(
          document,
          listOf(
            ".leechmed",
            ".leechers",
            ".leeches",
          ),
        ) ?: "0"

      return leechersText.toIntOrNull() ?: 0
    }

    /**
     * Parse downloads with multiple fallback selectors
     */
    private fun parseDownloads(document: Document): Int {
      val downloadsText =
        tryMultipleTextSelectors(
          document,
          listOf(
            ".downloaded",
            ".downloads",
            ".dl-count",
          ),
        ) ?: "0"

      return downloadsText.toIntOrNull() ?: 0
    }

    /**
     * Parse added date with multiple fallback selectors
     */
    private fun parseAddedDate(document: Document): Date? {
      val dateText =
        tryMultipleTextSelectors(
          document,
          listOf(
            ".posted",
            ".date",
            ".added-date",
          ),
        ) ?: return null

      return parseDateText(dateText)
    }

    /**
     * Parse file list with multiple fallback selectors
     */
    private fun parseFileList(document: Document): List<String> {
      val fileElements =
        tryMultipleSelectors(
          document,
          listOf(
            ".filelist .f-name",
            ".file-list .file-name",
            "#tor-filelist .f-name",
          ),
        )

      return fileElements.map { it.text().trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Parse category link
     */
    private fun parseCategoryLink(link: Element): RuTrackerCategory? {
      try {
        val href = link.attr("href")
        val categoryId =
          extractCategoryId(href)
            ?: return null

        val name =
          link
            .text()
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: return null

        return RuTrackerCategory(
          id = categoryId,
          name = name,
          url = href,
        )
      } catch (e: Exception) {
        debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse category link", e)
        return null
      }
    }

    /**
     * Try multiple text selectors and return first non-empty result
     */
    private fun tryMultipleTextSelectors(
      document: Document,
      selectors: List<String>,
    ): String? {
      for (selector in selectors) {
        try {
          val element = document.select(selector).firstOrNull()
          val text = element?.text()?.trim()
          if (!text.isNullOrEmpty()) {
            return text
          }
        } catch (e: Exception) {
          debugLogger.logWarning("RuTrackerParserEnhanced: Text selector '$selector' failed", e)
          // Continue with next selector
        }
      }
      return null
    }

    /**
     * Extract topic ID from URL
     */
    private fun extractTopicId(url: String): String? {
      val pattern = Pattern.compile("[?&]t=(\\d+)")
      val matcher = pattern.matcher(url)
      return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Extract category ID from URL
     */
    private fun extractCategoryId(url: String): Int? {
      val pattern = Pattern.compile("[?&]c=(\\d+)")
      val matcher = pattern.matcher(url)
      return if (matcher.find()) matcher.group(1)?.toIntOrNull() else null
    }

    /**
     * Parse size text to bytes
     */
    private fun parseSizeText(sizeText: String): Long {
      try {
        val matcher = SIZE_PATTERN.matcher(sizeText)
        if (matcher.find()) {
          val size = matcher.group(1).toDouble()
          val unit = matcher.group(2).uppercase()

          return when (unit) {
            "KB" -> (size * 1024).toLong()
            "MB" -> (size * 1024 * 1024).toLong()
            "GB" -> (size * 1024 * 1024 * 1024).toLong()
            "TB" -> (size * 1024 * 1024 * 1024 * 1024).toLong()
            else -> size.toLong()
          }
        }
      } catch (e: Exception) {
        debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse size text: $sizeText", e)
      }

      return 0L
    }

    /**
     * Parse date text to Date object
     */
    private fun parseDateText(dateText: String): Date? {
      try {
        for (format in DATE_FORMATS) {
          try {
            return format.parse(dateText)
          } catch (e: Exception) {
            // Try next format
          }
        }
      } catch (e: Exception) {
        debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse date text: $dateText", e)
      }

      return null
    }

    /**
     * Validate parsed data
     */
    private fun validateSearchResult(result: RuTrackerSearchResult): Boolean =
      result.topicId.isNotEmpty() &&
        result.title.isNotEmpty() &&
        result.size >= 0 &&
        result.seeders >= 0 &&
        result.leechers >= 0 &&
        result.downloads >= 0

    /**
     * Validate parsed torrent details
     */
    private fun validateTorrentDetails(details: RuTrackerTorrentDetails): Boolean =
      details.title.isNotEmpty() &&
        details.size >= 0 &&
        details.seeders >= 0 &&
        details.leechers >= 0 &&
        details.downloads >= 0

    /**
     * Clean text content
     */
    private fun cleanText(text: String): String =
      text
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\n\\s*\\n"), "\n")
        .replace(Regex("\\[\\d+\\]"), "")

    /**
     * Extract text with fallback
     */
    private fun extractTextWithFallback(
      element: Element,
      vararg selectors: String,
    ): String {
      for (selector in selectors) {
        try {
          val text = element.select(selector).text().trim()
          if (text.isNotEmpty()) {
            return cleanText(text)
          }
        } catch (e: Exception) {
          // Continue with next selector
        }
      }
      return ""
    }
  }

annotation class RuTrackerTorrentDetails
