package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object DescriptionExtractor {
    fun extractDescription(doc: Document): String {
        val descriptionSelectors =
            listOf(
                "div.post_body",
                "div.post_body span.post-i",
                "div.post_body span.post-b:contains(Описание:) + *",
            )

        for (selector in descriptionSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text()?.trim() ?: ""
            if (!text.isNullOrBlank()) {
                return text
            }
        }

        return ""
    }
}
