package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object DownloadsExtractor {
  // Download count is not typically shown on details page
  const val DEFAULT_DOWNLOADS = 0

  fun extractDownloads(doc: Document): Int {
    // Try to extract download count from various selectors
    val downloadSelectors =
      listOf(
        ".downloads",
        ".download-count",
        ".dl-count",
        ".times-downloaded",
      )

    for (selector in downloadSelectors) {
      val element = doc.selectFirst(selector)
      if (element != null) {
        val downloadText = element.text().trim()
        val downloads = downloadText.replace(Regex("[^0-9]"), "").toIntOrNull()
        if (downloads != null && downloads >= 0) {
          return downloads
        }
      }
    }

    return DEFAULT_DOWNLOADS
  }
}
