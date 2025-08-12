package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object DownloadsExtractor {
    fun extractDownloads(doc: Document): Int {
        // Download count is not typically shown on details page
        return 0
    }
}
