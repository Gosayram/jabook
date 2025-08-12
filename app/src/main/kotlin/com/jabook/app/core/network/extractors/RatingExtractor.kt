package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object RatingExtractor {
    fun extractRating(doc: Document): Float? {
        // Rating info is not typically shown on details page
        return null
    }
}
