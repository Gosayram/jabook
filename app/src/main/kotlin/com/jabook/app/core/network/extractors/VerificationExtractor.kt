package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object VerificationExtractor {
    fun extractIsVerified(doc: Document): Boolean {
        // Verification status is not typically shown on details page
        return false
    }
}
