package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.network.exceptions.RuTrackerException
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
class RuTrackerParserEnhanced @Inject constructor(
  private val debugLogger: IDebugLogger,
) {
  companion object {
    // Character encoding patterns
    private val CHARSET_PATTERN = Pattern.compile("charset=([^\"]+)")
    private val META_CHARSET_PATTERN = Pattern.compile("charset=([^\"]+)")

    private val DATE_PATTERNS = listOf(
      "yyyy-MM-dd HH:mm:ss",
      "dd-MM-yyyy HH:mm",
      "dd.MM.yyyy HH:mm",
      "yyyy-MM-dd",
      "dd-MM-yyyy",
      "dd.MM.yyyy",
    )

    // Size patterns
    private val SIZE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(KB|MB|GB|TB)", Pattern.CASE_INSENSITIVE)

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
        val searchRows = tryMultipleSelectors(
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
            if (result != null) results.add(result)
          } catch (e: Exception) {
            debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse search result row: ${e.message}")
          }
        }

        debugLogger.logDebug("RuTrackerParserEnhanced: Parsed ${results.size} search results")
        results
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerParserEnhanced: Failed to parse search results", e)
        throw RuTrackerException.ParseException("Failed to parse search results: ${e.message}")
      }
    }

  /**
   * Parse torrent details with multiple fallback selectors
   */
  suspend fun parseTorrentDetails(html: String): String =
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
        val magnetLink = parseMagnetLink(html)
        val fileList = parseFileList(document)

        val details = "Title: $title, Author: $author, Size: $size, Seeders: $seeders, Leechers: $leechers"

        debugLogger.logDebug("RuTrackerParserEnhanced: Parsed torrent details for: $title")
        details
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerParserEnhanced: Failed to parse torrent details", e)
        throw RuTrackerException.ParseException("Failed to parse torrent details: ${e.message}")
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

        val categoryLinks = tryMultipleSelectors(
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
            if (category != null) categories.add(category)
          } catch (e: Exception) {
            debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse category link: ${e.message}")
          }
        }

        debugLogger.logDebug("RuTrackerParserEnhanced: Parsed ${categories.size} categories")
        categories
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerParserEnhanced: Failed to parse categories", e)
        throw RuTrackerException.ParseException("Failed to parse categories: ${e.message}")
      }
    }

  /**
   * Parse magnet link with multiple fallback selectors
   */
  suspend fun parseMagnetLink(html: String): String? =
    withContext(Dispatchers.Default) {
      try {
        val document = parseHtml(html)

        val magnetElements = tryMultipleSelectors(
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

        val magnetLink = magnetElements.first()?.attr("href")
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
        val charset = detectCharset(html)

        val document = Jsoup.parse(html)
        document.outputSettings().charset(charset)
        document
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerParserEnhanced: Failed to parse HTML", e)
        throw RuTrackerException.ParseException("Failed to parse HTML: ${e.message}")
      }
    }

  /**
   * Detect charset from HTML
   */
  private fun detectCharset(html: String): Charset {
    try {
      val matcher = CHARSET_PATTERN.matcher(html)
      if (matcher.find()) {
        val charsetName = matcher.group(1)
        try { return Charset.forName(charsetName) } catch (_: Exception) {}
      }

      val metaMatcher = META_CHARSET_PATTERN.matcher(html)
      if (metaMatcher.find()) {
        val charsetName = metaMatcher.group(1)
        try { return Charset.forName(charsetName) } catch (_: Exception) {}
      }

      // Fallback to Windows-1251 (common for RuTracker)
      return Charset.forName("Windows-1251")
    } catch (_: Exception) {
      return StandardCharsets.UTF_8
    }
  }

  /**
   * Try multiple selectors and return first non-empty result
   */
  private fun tryMultipleSelectors(document: Document, selectors: List<String>): Elements {
    for (selector in selectors) {
      try {
        val elements = document.select(selector)
        if (elements.isNotEmpty()) {
          debugLogger.logDebug("RuTrackerParserEnhanced: Selector '$selector' found ${elements.size} elements")
          return elements
        }
      } catch (e: Exception) {
        debugLogger.logWarning("RuTrackerParserEnhanced: Selector '$selector' failed: ${e.message}")
      }
    }
    debugLogger.logWarning("RuTrackerParserEnhanced: No selectors found any elements")
    return Elements()
  }

  /**
   * Parse search result row
   */
  private fun parseSearchResultRow(row: Element): RuTrackerSearchResult? {
    return try {
      // Extract topic ID
      val topicLink = row.select("a[href*=\"viewtopic.php?\"]").firstOrNull() ?: return null
      val href = topicLink.attr("href")
      val topicId = extractTopicId(href) ?: return null

      // Extract title
      val title = topicLink.text().trim().takeIf { it.isNotEmpty() } ?: return null

      // Extract author
      val author =
        row.select("td:has(a[href*=\"profile.php\"])").firstOrNull()?.text()?.trim()
          ?: "Unknown"

      // Extract size
      val sizeText =
        row.select("td").find { td ->
          val t = td.text()
          t.contains("KB", true) || t.contains("MB", true) || t.contains("GB", true)
        }?.text()?.trim() ?: "0 KB"
      val size = parseSizeText(sizeText)

      val numericCells = row.select("td").map { it.text().trim() }.filter { it.matches(Regex("\\d+")) }
      val seeders = numericCells.getOrNull(0)?.toIntOrNull() ?: 0
      val leechers = numericCells.getOrNull(1)?.toIntOrNull() ?: 0
      val downloads = numericCells.getOrNull(2)?.toIntOrNull() ?: 0

      RuTrackerSearchResult(
        query = title,
        totalResults = 1,
        currentPage = 1,
        totalPages = 1,
        results = listOf(
          com.jabook.app.core.domain.model.RuTrackerAudiobook(
            id = topicId,
            title = title,
            author = author,
            description = "",
            category = "",
            categoryId = "",
            sizeBytes = size,
            size = "$size bytes",
            seeders = seeders,
            leechers = leechers,
            completed = downloads,
            addedDate = "",
            downloads = downloads,
            magnetUri = null,
            torrentUrl = null,
            state = com.jabook.app.core.domain.model.TorrentState.APPROVED
          )
        )
      )
    } catch (e: Exception) {
      debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse search result row: ${e.message}")
      null
    }
  }

  /** Title */
  private fun parseTitle(document: Document): String =
    tryMultipleTextSelectors(
      document,
      listOf("h1.maintitle", "h1", "title", ".post-title", ".topic-title"),
    ) ?: "Unknown Title"

  /** Author */
  private fun parseAuthor(document: Document): String =
    tryMultipleTextSelectors(
      document,
      listOf(".post_body:contains(Автор)", ".post_body:contains(Автор) + *", ".author-name", ".post-author"),
    ) ?: "Unknown Author"

  /** Description */
  private fun parseDescription(document: Document): String =
    tryMultipleTextSelectors(
      document,
      listOf(".post-i", ".post_body", ".description", ".topic-description"),
    ) ?: ""

  /** Category */
  private fun parseCategory(document: Document): String =
    tryMultipleTextSelectors(
      document,
      listOf(".nav:contains(Аудиокниги)", ".breadcrumb:contains(Аудиокниги)", ".category-name"),
    ) ?: "Аудиокниги"

  /** Size */
  private fun parseSize(document: Document): Long {
    val sizeText = tryMultipleTextSelectors(
      document,
      listOf(".tor-size", ".torrent-size", ".size:contains(MB)", ".size:contains(GB)"),
    ) ?: "0 MB"
    return parseSizeText(sizeText)
  }

  /** Seeders */
  private fun parseSeeders(document: Document): Int {
    val text = tryMultipleTextSelectors(document, listOf(".seedmed", ".seeders", ".seeds")) ?: "0"
    return text.toIntOrNull() ?: 0
  }

  /** Leechers */
  private fun parseLeechers(document: Document): Int {
    val text = tryMultipleTextSelectors(document, listOf(".leechmed", ".leechers", ".leeches")) ?: "0"
    return text.toIntOrNull() ?: 0
  }

  /** Downloads */
  private fun parseDownloads(document: Document): Int {
    val text = tryMultipleTextSelectors(document, listOf(".downloaded", ".downloads", ".dl-count")) ?: "0"
    return text.toIntOrNull() ?: 0
  }

  /** Added date */
  private fun parseAddedDate(document: Document): Date? {
    val dateText = tryMultipleTextSelectors(document, listOf(".posted", ".date", ".added-date")) ?: return null
    return parseDateText(dateText)
  }

  /** File list */
  private fun parseFileList(document: Document): List<String> {
    val fileElements = tryMultipleSelectors(
      document,
      listOf(".filelist .f-name", ".file-list .file-name", "#tor-filelist .f-name"),
    )
    return fileElements.map { it.text().trim() }.filter { it.isNotEmpty() }
  }

  /** Category link */
  private fun parseCategoryLink(link: Element): RuTrackerCategory? {
    return try {
      val href = link.attr("href")
      val categoryId = extractCategoryId(href) ?: return null
      val name = link.text().trim().takeIf { it.isNotEmpty() } ?: return null

      RuTrackerCategory(id = categoryId.toString(), name = name, description = "", parentId = null)
    } catch (e: Exception) {
      debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse category link: ${e.message}")
      null
    }
  }

  /** Try text selectors */
  private fun tryMultipleTextSelectors(document: Document, selectors: List<String>): String? {
    for (selector in selectors) {
      try {
        val text = document.select(selector).firstOrNull()?.text()?.trim()
        if (!text.isNullOrEmpty()) return text
      } catch (e: Exception) {
        debugLogger.logWarning("RuTrackerParserEnhanced: Text selector '$selector' failed: ${e.message}")
      }
    }
    return null
  }

  /** Topic ID */
  private fun extractTopicId(url: String): String? {
    val pattern = Pattern.compile("[?&]t=(\\d+)")
    val matcher = pattern.matcher(url)
    return if (matcher.find()) matcher.group(1) else null
  }

  /** Category ID */
  private fun extractCategoryId(url: String): Int? {
    val pattern = Pattern.compile("[?&]c=(\\d+)")
    val matcher = pattern.matcher(url)
    return if (matcher.find()) matcher.group(1)?.toIntOrNull() else null
  }

  /** Size text → bytes */
  private fun parseSizeText(sizeText: String): Long {
    try {
      val matcher = SIZE_PATTERN.matcher(sizeText)
      if (matcher.find()) {
        val size = matcher.group(1).toDouble()
        val unit = matcher.group(2).uppercase(Locale.ROOT)
        return when (unit) {
          "KB" -> (size * 1024).toLong()
          "MB" -> (size * 1024 * 1024).toLong()
          "GB" -> (size * 1024 * 1024 * 1024).toLong()
          "TB" -> (size * 1024 * 1024 * 1024 * 1024).toLong()
          else -> size.toLong()
        }
      }
    } catch (e: Exception) {
      debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse size text: $sizeText: ${e.message}")
    }
    return 0L
  }

  /** Date text → Date */
  private fun parseDateText(dateText: String): Date? {
    try {
      for (pattern in DATE_PATTERNS) {
        try {
          val sdf = SimpleDateFormat(pattern, Locale.getDefault())
          val parsed = sdf.parse(dateText)
          if (parsed != null) return parsed
        } catch (_: Exception) {
        }
      }
    } catch (e: Exception) {
      debugLogger.logWarning("RuTrackerParserEnhanced: Failed to parse date text: $dateText: ${e.message}")
    }
    return null
  }

  /** Validate result objects (на будущее) */
  private fun validateSearchResult(result: RuTrackerSearchResult): Boolean =
    result.query.isNotEmpty() &&
            result.results.isNotEmpty() &&
            result.totalResults >= 0

  private fun validateTorrentDetails(details: String): Boolean =
    details.isNotEmpty()

  /** Clean text */
  private fun cleanText(text: String): String =
    text.trim()
      .replace(Regex("\\s+"), " ")
      .replace(Regex("\\n\\s*\\n"), "\n")
      .replace(Regex("\\[\\d+\\]"), "")

  /** Extract text with fallback */
  private fun extractTextWithFallback(element: Element, vararg selectors: String): String {
    for (selector in selectors) {
      try {
        val text = element.select(selector).text().trim()
        if (text.isNotEmpty()) return cleanText(text)
      } catch (_: Exception) { }
    }
    return ""
  }
}
