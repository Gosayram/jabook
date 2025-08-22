package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document
import java.util.Date

object RegisteredExtractor {
  // Registration date is not typically shown on details page
  val DEFAULT_REGISTERED: Date? = null

  fun extractRegistered(doc: Document): Date? {
    // Try to extract registration date from various selectors
    val registeredSelectors =
      listOf(
        ".registered-date",
        ".user-registered",
        ".reg-date",
      )

    for (selector in registeredSelectors) {
      val element = doc.selectFirst(selector)
      if (element != null) {
        val dateText = element.text().trim()
        // Parse date if found
        // For now, return null as parsing logic would be complex
        if (dateText.isNotBlank()) {
          return null // Date parsing not implemented yet
        }
      }
    }

    return DEFAULT_REGISTERED
  }
}
