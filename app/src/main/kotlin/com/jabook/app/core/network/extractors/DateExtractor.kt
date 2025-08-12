package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object DateExtractor {
    fun extractAddedDate(doc: Document): String {
        val dateSelectors = listOf(
            "a.p-link.small",
            "span.post-time a",
        )

        for (selector in dateSelectors) {
            val element = doc.selectFirst(selector)
            val date = element?.text()?.trim()
            if (!date.isNullOrBlank()) {
                return date
            }
        }

        return ""
    }

    fun extractLastUpdate(doc: Document): String? {
        // Last update info is not typically shown on details page
        return null
    }
}
