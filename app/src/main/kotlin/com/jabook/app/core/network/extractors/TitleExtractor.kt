package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object TitleExtractor {
    fun extractTitle(doc: Document): String {
        // Try multiple selectors for title
        val selectors = listOf(
            "h1.maintitle a",
            "h1.maintitle",
            "title",
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            val title = element?.text()?.trim()?.takeIf { it.isNotBlank() }
            if (!title.isNullOrBlank()) {
                return title
            }
        }

        return ""
    }
}
