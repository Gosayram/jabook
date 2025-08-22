package com.jabook.app.core.network.extractors

import org.jsoup.nodes.Document

object VerificationExtractor {
  // Verification status is not typically shown on details page
  const val DEFAULT_IS_VERIFIED = false

  fun extractIsVerified(doc: Document): Boolean {
    // Try to extract verification status from various selectors
    val verificationSelectors =
      listOf(
        ".verified",
        ".verified-torrent",
        ".check-mark",
        ".status-verified",
        "img[src*='verified']",
        "img[alt*='verified']",
      )

    for (selector in verificationSelectors) {
      val element = doc.selectFirst(selector)
      if (element != null) {
        // Check if element exists and indicates verification
        val text = element.text().lowercase()
        val src = element.attr("src").lowercase()
        val alt = element.attr("alt").lowercase()

        if (isVerifiedByText(text) || isVerifiedByAttributes(src, alt)) {
          return true
        }
      }
    }

    return DEFAULT_IS_VERIFIED
  }

  private fun isVerifiedByText(text: String): Boolean = text.contains("verified") || text.contains("проверено")

  private fun isVerifiedByAttributes(
    src: String,
    alt: String,
  ): Boolean =
    src.contains("verified") ||
      src.contains("check") ||
      alt.contains("verified") ||
      alt.contains("проверено")
}
