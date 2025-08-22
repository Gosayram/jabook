package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object CategoryExtractor {
    fun extractCategory(doc: Document): String {
        val categorySelectors = listOf(
            "td.nav a[href*='c=']",
            "td.nav a[href*='f=']",
            "div.t-breadcrumb-top a",
        )

        for (selector in categorySelectors) {
            val element = doc.selectFirst(selector)
            val category = element?.text()?.trim()
            if (!category.isNullOrBlank()) {
                return category
            }
        }

        return "Audiobooks"
    }

    fun extractCategoryId(doc: Document): String {
        val categorySelectors = listOf(
            "td.nav a[href*='c=']",
            "td.nav a[href*='f=']",
            "div.t-breadcrumb-top a",
        )

        for (selector in categorySelectors) {
            val element = doc.selectFirst(selector)
            val href = element?.attr("href") ?: ""
            val categoryId = href.substringAfter("c=").substringAfter("f=").substringBefore("&").ifBlank { null }
            if (!categoryId.isNullOrBlank()) {
                return categoryId
            }
        }

        return "33"
    }
}
