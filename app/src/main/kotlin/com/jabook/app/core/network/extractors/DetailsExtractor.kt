package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object DetailsExtractor {
  fun extractYear(doc: Document): Int? {
    val yearSelectors =
      listOf(
        "span.post-b:contains(Год выпуска:) + *",
        "span.post-b:contains(Год:)",
        "div.post_body:contains(Год выпуска:)",
        "div.post_body:contains(Год:)",
      )

    for (selector in yearSelectors) {
      val element = doc.selectFirst(selector)
      val text = element?.text() ?: ""
      val yearText = text.substringAfter("Год выпуска:").substringAfter("Год:").trim()
      val year = yearText.toIntOrNull()
      if (year != null) {
        return year
      }
    }

    return null
  }

  fun extractQuality(doc: Document): String? {
    val qualitySelectors =
      listOf(
        "span.post-b:contains(Битрейт аудио:) + *",
        "span.post-b:contains(Качество:)",
        "div.post_body:contains(Битрейт аудио:)",
        "div.post_body:contains(Качество:)",
      )

    for (selector in qualitySelectors) {
      val element = doc.selectFirst(selector)
      val text = element?.text() ?: ""
      val quality = text.substringAfter("Битрейт аудио:").substringAfter("Качество:").trim()
      if (quality.isNotBlank()) {
        return quality
      }
    }

    return null
  }

  fun extractDuration(doc: Document): String? {
    val durationSelectors =
      listOf(
        "span.post-b:contains(Продолжительность:) + *",
        "span.post-b:contains(Длительность:)",
        "div.post_body:contains(Продолжительность:)",
        "div.post_body:contains(Длительность:)",
      )

    for (selector in durationSelectors) {
      val element = doc.selectFirst(selector)
      val text = element?.text() ?: ""
      val duration = text.substringAfter("Продолжительность:").substringAfter("Длительность:").trim()
      if (duration.isNotBlank()) {
        return duration
      }
    }

    return null
  }
}
