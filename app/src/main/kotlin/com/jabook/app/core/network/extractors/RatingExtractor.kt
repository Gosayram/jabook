package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object RatingExtractor {
    // Rating info is not typically shown on details page
    val DEFAULT_RATING: Float? = null

    fun extractRating(doc: Document): Float? {
        // Try to extract rating from various selectors
        val ratingSelectors =
            listOf(
                ".rating",
                ".stars",
                ".score",
                ".rating-value",
            )

        for (selector in ratingSelectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val ratingText = element.text().trim()
                val rating = ratingText.toFloatOrNull()
                if (rating != null && rating >= 0f && rating <= 5f) {
                    return rating
                }
            }
        }

        return DEFAULT_RATING
    }
}
