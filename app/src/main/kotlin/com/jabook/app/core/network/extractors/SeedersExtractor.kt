package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object SeedersExtractor {
    fun extractSeeders(doc: Document): Int {
        // Seeders/leechers info is not typically shown on details page
        return 0
    }

    fun extractLeechers(doc: Document): Int {
        // Seeders/leechers info is not typically shown on details page
        return 0
    }

    fun extractCompleted(doc: Document): Int {
        // Completed downloads info is not typically shown on details page
        return 0
    }
}
