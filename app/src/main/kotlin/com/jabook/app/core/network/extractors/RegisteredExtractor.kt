package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document
import java.util.Date

object RegisteredExtractor {
    fun extractRegistered(doc: Document): Date? {
        // Registration date is not typically shown on details page
        return null
    }
}
