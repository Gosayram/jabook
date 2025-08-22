package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object TopicIdExtractor {
    fun extractTopicId(doc: Document): String {
        // Try multiple selectors for topic ID
        val selectors =
            listOf(
                "link[rel=canonical]",
                "a[href*='viewtopic.php?t=']",
                "h1.maintitle a",
            )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            val href = element?.attr("href") ?: ""
            val topicId = href.substringAfter("t=").substringBefore("&").takeIf { it.isNotBlank() }
            if (!topicId.isNullOrBlank()) {
                return topicId
            }
        }

        return ""
    }
}
