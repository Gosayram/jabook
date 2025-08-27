package com.jabook.core.parse

import android.util.Log
import com.jabook.core.endpoints.EndpointResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * HTML parser for RuTracker content
 * Handles search results, topic details, and character encoding detection
 */
class Parser(
    private val endpointResolver: EndpointResolver
) {
    
    private val TAG = "Parser"
    
    /**
     * Search result data class
     */
    data class SearchResult(
        val id: String,
        val title: String,
        val author: String?,
        val size: Long,
        val seeds: Int,
        val leeches: Int,
        val magnetUrl: String?,
        val torrentUrl: String?,
        val description: String?
    )
    
    /**
     * Topic details data class
     */
    data class TopicDetails(
        val id: String,
        val title: String,
        val author: String,
        val description: String,
        val files: List<TorrentFile>,
        val magnetUrl: String?,
        val torrentUrl: String?,
        val totalSize: Long,
        val postDate: String,
        var viewCount: Int,
        var replyCount: Int
    )
    
    /**
     * Torrent file data class
     */
    data class TorrentFile(
        val name: String,
        val size: Long,
        val type: String, // "audio", "video", "ebook", etc.
        val selected: Boolean = true
    )
    
    /**
     * Parses search results from RuTracker
     */
    suspend fun parseSearchResults(html: String, page: Int = 1): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val doc = parseHtmlWithEncodingDetection(html)
                val results = mutableListOf<SearchResult>()
                
                // Select topic links using multiple selectors for robustness
                val topicLinks = doc.select("a[href*=\"viewtopic.php?t=\"]")
                    .ifEmpty { doc.select("a.tLink") }
                
                for (link in topicLinks) {
                    try {
                        val href = link.attr("href")
                        val topicId = extractTopicId(href)
                        
                        if (topicId != null) {
                            val title = link.text().trim()
                            val parent = link.closest("tr") ?: link.parent()
                            
                            // Extract other information from the table row
                            val size = extractSize(parent)
                            val (seeds, leeches) = extractSeedsAndLeeches(parent)
                            val magnetUrl = extractMagnetUrl(parent)
                            val torrentUrl = extractTorrentUrl(parent)
                            val author = extractAuthor(parent)
                            
                            results.add(SearchResult(
                                id = topicId,
                                title = title,
                                author = author,
                                size = size,
                                seeds = seeds,
                                leeches = leeches,
                                magnetUrl = magnetUrl,
                                torrentUrl = torrentUrl,
                                description = null
                            ))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse search result", e)
                        continue
                    }
                }
                
                Log.i(TAG, "Parsed ${results.size} search results from page $page")
                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse search results", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Parses topic details from RuTracker
     */
    suspend fun parseTopicDetails(html: String): Result<TopicDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val doc = parseHtmlWithEncodingDetection(html)
                
                // Extract topic title
                val titleElement = doc.selectFirst("h1, .post-title, .topic-title") 
                    ?: doc.selectFirst("title")
                val title = titleElement?.text()?.trim() ?: "Unknown Title"
                
                // Extract topic ID from URL or meta tags
                val topicId = extractTopicId(doc.location() ?: "") 
                    ?: doc.selectFirst("meta[name=\"topic_id\"]")?.attr("content")
                
                if (topicId == null) {
                    return@withContext Result.failure(Exception("Could not extract topic ID"))
                }
                
                // Extract author
                val author = doc.selectFirst(".post-author, .author-name, .username")?.text()?.trim()
                    ?: "Unknown Author"
                
                // Extract description
                val description = doc.selectFirst(".post-content, .topic-content, .message")?.text()?.trim()
                    ?: ""
                
                // Extract files from the download table
                val files = parseDownloadTable(doc)
                
                // Extract magnet and torrent URLs
                val magnetUrl = extractMagnetUrl(doc)
                val torrentUrl = extractTorrentUrl(doc)
                
                // Extract metadata
                val totalSize = extractTotalSize(doc)
                val postDate = extractPostDate(doc)
                val viewCount = extractViewCount(doc)
                val replyCount = extractReplyCount(doc)
                
                Result.success(TopicDetails(
                    id = topicId,
                    title = title,
                    author = author,
                    description = description,
                    files = files,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    totalSize = totalSize,
                    postDate = postDate,
                    viewCount = viewCount,
                    replyCount = replyCount
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse topic details", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Detects character encoding from HTML
     */
    private fun detectCharset(html: String): Charset {
        // Try to get charset from meta tag
        val metaCharsetRegex = Regex("""<meta\s+[^>]*charset\s*=\s*["']?([^"'\s>]+)""")
        val match = metaCharsetRegex.find(html)
        
        if (match != null) {
            val charset = match.groupValues[1].lowercase()
            return try {
                Charset.forName(charset)
            } catch (e: Exception) {
                StandardCharsets.UTF_8
            }
        }
        
        // Default to UTF-8, fallback to windows-1251 for Russian content
        return if (html.contains("windows-1251", ignoreCase = true)) {
            Charset.forName("windows-1251")
        } else {
            StandardCharsets.UTF_8
        }
    }
    
    /**
     * Parses HTML with encoding detection
     */
    private fun parseHtmlWithEncodingDetection(html: String): Document {
        val charset = detectCharset(html)
        
        return try {
            // Try with detected charset first
            Jsoup.parse(html, charset.name())
        } catch (e: Exception) {
            // Fallback to UTF-8
            try {
                Jsoup.parse(html, StandardCharsets.UTF_8.name())
            } catch (e2: Exception) {
                // Last resort - parse without charset
                Jsoup.parse(html)
            }
        }
    }
    
    /**
     * Extracts topic ID from various URL formats
     */
    private fun extractTopicId(url: String): String? {
        val patterns = listOf(
            Regex("""t=(\d+)"""),
            Regex("""topic_id=(\d+)"""),
            Regex("""/viewtopic\.php\?t=(\d+)"""),
            Regex("""/t(\d+)""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    /**
     * Extracts file size from text
     */
    private fun extractSize(element: Element?): Long {
        return try {
            val sizeText = element?.selectFirst("td, span")?.text()?.trim()
                ?: return 0L
            
            // Parse size formats: "1.2 GB", "1234 MB", "567 KB", "890 B"
            val sizePattern = Regex("""(\d+(?:\.\d+)?)\s*([KMG]?B)""", RegexOption.IGNORE_CASE)
            val match = sizePattern.find(sizeText)
            
            if (match != null) {
                val value = match.groupValues[1].toDouble()
                val unit = match.groupValues[2].uppercase()
                
                val bytes = when (unit) {
                    "KB" -> value * 1024
                    "MB" -> value * 1024 * 1024
                    "GB" -> value * 1024 * 1024 * 1024
                    "B" -> value
                    else -> value
                }
                
                return bytes.toLong()
            }
            
            0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract size", e)
            0L
        }
    }
    
    /**
     * Extracts seeds and leeches count
     */
    private fun extractSeedsAndLeeches(element: Element?): Pair<Int, Int> {
        return try {
            val seedText = element?.selectFirst(".seedmed, .seeders, [title*=\"seed\"]")?.text()?.trim()
            var leechText = element?.selectFirst(".leechmed, .leechers, [title*=\"leech\"]")?.text()?.trim()
            
            // If leech text not found, try to find it in the same row
            if (leechText == null) {
                val cells = element?.select("td")
                if (cells?.size ?: 0 >= 4) {
                    leechText = cells[3].text().trim()
                }
            }
            
            val seeds = extractNumber(seedText) ?: 0
            val leeches = extractNumber(leechText) ?: 0
            
            seeds to leeches
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract seeds/leeches", e)
            0 to 0
        }
    }
    
    /**
     * Extracts magnet URL
     */
    private fun extractMagnetUrl(element: Element?): String? {
        return element?.selectFirst("a[href^=\"magnet:?\"]")?.attr("href")
    }
    
    /**
     * Extracts torrent download URL
     */
    private fun extractTorrentUrl(element: Element?): String? {
        return element?.selectFirst("a[href*=\"download.php?id=\"]")?.attr("href")
    }
    
    /**
     * Extracts author name
     */
    private fun extractAuthor(element: Element?): String? {
        return element?.selectFirst(".author, .username, [data-author]")?.text()?.trim()
    }
    
    /**
     * Parses download table for files
     */
    private fun parseDownloadTable(doc: Document): List<TorrentFile> {
        val files = mutableListOf<TorrentFile>()
        
        try {
            val downloadTable = doc.selectFirst("table:has(a[href*=\"download.php\"]), .download-table, .file-list")
            
            if (downloadTable != null) {
                val rows = downloadTable.select("tr")
                
                for (row in rows) {
                    val cells = row.select("td")
                    if (cells.size >= 2) {
                        val fileName = cells[0].text().trim()
                        val fileSize = extractSize(cells[1])
                        val fileType = detectFileType(fileName)
                        
                        files.add(TorrentFile(
                            name = fileName,
                            size = fileSize,
                            type = fileType
                        ))
                    }
                }
            } else {
                // Fallback: look for individual download links
                val downloadLinks = doc.select("a[href*=\"download.php?id=\"]")
                for (link in downloadLinks) {
                    val fileName = link.text().trim()
                    val fileSize = extractSize(link)
                    val fileType = detectFileType(fileName)
                    
                    files.add(TorrentFile(
                        name = fileName,
                        size = fileSize,
                        type = fileType
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse download table", e)
        }
        
        return files
    }
    
    /**
     * Detects file type from name
     */
    private fun detectFileType(fileName: String): String {
        val name = fileName.lowercase()
        return when {
            name.contains("audio") || name.contains("music") || name.endsWith("mp3") -> "audio"
            name.contains("video") || name.endsWith("mp4") || name.endsWith("avi") -> "video"
            name.contains("book") || name.contains("epub") || name.endsWith("pdf") -> "ebook"
            name.contains("game") -> "game"
            else -> "other"
        }
    }
    
    /**
     * Extracts total size from topic
     */
    private fun extractTotalSize(doc: Document): Long {
        return try {
            val sizeText = doc.selectFirst(".total-size, .topic-size, [title*=\"total size\"]")?.text()?.trim()
            extractSize(sizeText)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract total size", e)
            0L
        }
    }
    
    /**
     * Extracts post date
     */
    private fun extractPostDate(doc: Document): String {
        return try {
            doc.selectFirst(".post-date, .post-time, [title*=\"posted\"], [datetime]")?.attr("datetime")
                ?: doc.selectFirst(".post-date, .post-time")?.text()?.trim()
                ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract post date", e)
            ""
        }
    }
    
    /**
     * Extracts view count
     */
    private fun extractViewCount(doc: Document): Int {
        return try {
            val viewText = doc.selectFirst(".views, .view-count, [title*=\"view\"]")?.text()?.trim()
            extractNumber(viewText) ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract view count", e)
            0
        }
    }
    
    /**
     * Extracts reply count
     */
    private fun extractReplyCount(doc: Document): Int {
        return try {
            val replyText = doc.selectFirst(".replies, .reply-count, [title*=\"reply\"]")?.text()?.trim()
            extractNumber(replyText) ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract reply count", e)
            0
        }
    }
    
    /**
     * Extracts number from text
     */
    private fun extractNumber(text: String?): Int? {
        return text?.let {
            val numberPattern = Regex("""(\d+)""")
            val match = numberPattern.find(it)
            match?.value?.toInt()
        }
    }
}