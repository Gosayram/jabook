package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object SeedersExtractor {
    // Seeders/leechers info is not typically shown on details page
    const val DEFAULT_SEEDERS = 0
    const val DEFAULT_LEECHERS = 0
    const val DEFAULT_COMPLETED = 0

    fun extractSeeders(doc: Document): Int {
        // Try to extract seeders count from various selectors
        val seedersSelectors = listOf(
            ".seeders",
            ".seeds",
            ".seed-count",
            ".torrent-seeders",
        )

        for (selector in seedersSelectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val seedersText = element.text().trim()
                val seeders = seedersText.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (seeders != null && seeders >= 0) {
                    return seeders
                }
            }
        }

        return DEFAULT_SEEDERS
    }

    fun extractLeechers(doc: Document): Int {
        // Try to extract leechers count from various selectors
        val leechersSelectors = listOf(
            ".leechers",
            ".leech",
            ".leech-count",
            ".torrent-leechers",
        )

        for (selector in leechersSelectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val leechersText = element.text().trim()
                val leechers = leechersText.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (leechers != null && leechers >= 0) {
                    return leechers
                }
            }
        }

        return DEFAULT_LEECHERS
    }

    fun extractCompleted(doc: Document): Int {
        // Try to extract completed downloads count from various selectors
        val completedSelectors = listOf(
            ".completed",
            ".complete",
            ".completed-downloads",
            ".torrent-completed",
        )

        for (selector in completedSelectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val completedText = element.text().trim()
                val completed = completedText.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (completed != null && completed >= 0) {
                    return completed
                }
            }
        }

        return DEFAULT_COMPLETED
    }
}
