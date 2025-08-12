package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object SizeExtractor {
    fun extractSize(doc: Document): String {
        // Try to extract from magnet link info
        val magnetInfo = doc.select("div.attach_link li").text()
        val sizeMatch = Regex("(\\d+(\\.\\d+)?\\s*[KMGT]?B)").find(magnetInfo)
        if (sizeMatch != null) {
            return sizeMatch.value
        }

        return "0 MB"
    }
}
