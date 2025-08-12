package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object CoverExtractor {
    val DEFAULT_COVER_URL: String? = null

    fun extractCoverUrl(doc: Document): String? {
        val coverSelectors = listOf(
            "var.postImg[title*='.jpg']",
            "var.postImg[title*='.png']",
            "img[src*='fastpic.ru']",
            "img[src*='covers']",
        )

        for (selector in coverSelectors) {
            val element = doc.selectFirst(selector)
            val src = element?.attr("src")
            val title = element?.attr("title")
            val url = src ?: title
            if (!url.isNullOrBlank()) {
                return url
            }
        }

        return DEFAULT_COVER_URL
    }
}
