package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object TagsExtractor {
  // Tags are not typically shown on details page
  val DEFAULT_TAGS = emptyList<String>()

  fun extractTags(doc: Document): List<String> {
    // Try to extract tags from various selectors
    val tagSelectors =
      listOf(
        ".tags",
        ".tag",
        ".keywords",
        ".labels",
        ".categories",
      )

    val tags = mutableListOf<String>()

    for (selector in tagSelectors) {
      val elements = doc.select(selector)
      for (element in elements) {
        val tagText = element.text().trim()
        if (tagText.isNotBlank() && !tags.contains(tagText)) {
          // Split by common separators
          val splitTags =
            tagText
              .split(",", ";", "|", "•")
              .map { it.trim() }
              .filter { it.isNotBlank() }

          tags.addAll(splitTags)
        }
      }
    }

    return if (tags.isNotEmpty()) tags.distinct() else DEFAULT_TAGS
  }
}
