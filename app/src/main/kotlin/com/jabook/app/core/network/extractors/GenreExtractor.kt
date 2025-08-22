package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object GenreExtractor {
  val DEFAULT_GENRES = emptyList<String>()

  fun extractGenres(doc: Document): List<String> {
    val genreSelectors =
      listOf(
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

    return DEFAULT_GENRES
  }
}
