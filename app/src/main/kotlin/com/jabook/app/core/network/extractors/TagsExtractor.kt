package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object TagsExtractor {
    fun extractTags(doc: Document): List<String> {
        // Tags are not typically shown on details page
        return emptyList()
    }
}
