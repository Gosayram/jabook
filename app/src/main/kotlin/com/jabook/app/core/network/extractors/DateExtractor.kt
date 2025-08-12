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

    // Last update info is not typically shown on details page
    val DEFAULT_LAST_UPDATE: String? = null

    fun extractLastUpdate(doc: Document): String? {
        // Try to extract last update date from various selectors
        val lastUpdateSelectors = listOf(
            ".last-update",
            ".updated-date",
            ".modification-date",
            ".edit-date",
        )

        for (selector in lastUpdateSelectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val updateText = element.text().trim()
                if (updateText.isNotBlank()) {
                    return updateText
                }
            }
        }

        return DEFAULT_LAST_UPDATE
    }
}
