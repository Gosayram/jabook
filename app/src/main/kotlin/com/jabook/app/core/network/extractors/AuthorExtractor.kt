package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object AuthorExtractor {
    fun extractAuthor(doc: Document): String {
        // Try multiple approaches for author extraction
        val authorSelectors =
            listOf(
                "span.post-b:contains(Автор:) + br + *",
                "span.post-b:contains(Автор:)",
                "span.post-b:contains(Автор)",
                "div.post_body:contains(Автор:)",
                "div.post_body:contains(Автор)",
            )

        for (selector in authorSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val author = text.substringAfter("Автор:").trim()
            if (author.isNotBlank()) {
                return author
            }
        }

        // Fallback: try to extract from title
        val title = TitleExtractor.extractTitle(doc)
        return title.split(" - ").firstOrNull()?.trim() ?: ""
    }

    fun extractNarrator(doc: Document): String? {
        val narratorSelectors =
            listOf(
                "span.post-b:contains(Читает:) + br + *",
                "span.post-b:contains(Читает:)",
                "div.post_body:contains(Читает:)",
                "div.post_body:contains(Читает)",
            )

        for (selector in narratorSelectors) {
            val element = doc.selectFirst(selector)
            val text = element?.text() ?: ""
            val narrator = text.substringAfter("Читает:").trim()
            if (narrator.isNotBlank()) {
                return narrator
            }
        }

        return null
    }
}
