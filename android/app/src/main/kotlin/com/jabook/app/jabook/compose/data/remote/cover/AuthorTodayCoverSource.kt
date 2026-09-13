// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.remote.cover

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/** A single search-result candidate extracted from an Author.Today search page. */
public data class CoverCandidate(
    val url: String,
    val resultTitle: String,
    val resultAuthor: String,
)

/**
 * Cover source backed by author.today's server-rendered search page.
 *
 * The page requires a browser-like User-Agent — the injected client
 * (@Named("coverDownload")) supplies one via RutrackerHeadersInterceptor.
 * Cover URLs are kept exactly as served (153x200 thumbnails; height 200
 * clears the validator's 200px minimum exactly).
 *
 * Errors never propagate: any network/parse failure yields an empty list and
 * the lookup chain simply falls through to OpenLibrary/Google Books.
 */
public class AuthorTodayCoverSource(
    private val client: OkHttpClient,
    loggerFactory: LoggerFactory,
    internal val searchUrlBase: String = SEARCH_URL_BASE,
) {
    private val logger = loggerFactory.get(TAG)

    /**
     * Search author.today for "title author" and extract cover candidates.
     *
     * @return at most [MAX_CANDIDATES] candidates, empty on any failure.
     */
    public fun findCandidates(
        title: String,
        author: String,
    ): List<CoverCandidate> =
        try {
            val url =
                searchUrlBase.trimEnd('/') +
                    "?q=" + URLEncoder.encode("${title.trim()} ${author.trim()}", "UTF-8")
            val html =
                client
                    .newCall(Request.Builder().url(url).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) return emptyList()
                        response.body.string()
                    }
            parseCandidates(html, url)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.w({ "Author.Today search failed for \"$title\"" }, e)
            emptyList()
        }

    /** Visible for tests. */
    internal fun parseCandidates(
        html: String,
        baseUri: String,
    ): List<CoverCandidate> {
        val document = Jsoup.parse(html, baseUri)
        val candidates = mutableListOf<CoverCandidate>()
        val seen = mutableSetOf<String>()
        for (img in document.select("img[data-src]")) {
            if (candidates.size >= MAX_CANDIDATES) break
            val group = resultCardOf(img) ?: continue
            val url = img.absUrl("data-src").ifEmpty { img.attr("data-src") }
            if (url.isBlank() || !seen.add(url)) continue
            val titleAnchor =
                group
                    .select("a[href*=/work/]")
                    .firstOrNull { it.text().isNotBlank() } ?: continue
            val resultTitle = collapseSpaces(titleAnchor.text())
            val resultAuthor =
                collapseSpaces(
                    group
                        .selectFirst(".book-author, .author, [itemprop=author]")
                        ?.text()
                        .orEmpty(),
                )
            candidates.add(CoverCandidate(url, resultTitle, resultAuthor))
        }
        return candidates
    }

    /**
     * Walks up from the cover image to the smallest ancestor that also holds
     * a title anchor (Element.select matches self, so a non-blank anchor text
     * is required — the img's own wrapper anchor has none).
     */
    private fun resultCardOf(img: Element): Element? {
        var node: Element? = img.parent()
        while (node != null) {
            if (node.select("a[href*=/work/]").any { it.text().isNotBlank() }) return node
            node = node.parent()
        }
        return null
    }

    private fun collapseSpaces(raw: String): String = raw.replace(WHITESPACE, " ").trim()

    internal companion object {
        const val TAG = "AuthorTodayCoverSource"
        const val SEARCH_URL_BASE = "https://author.today/search"
        const val MAX_CANDIDATES = 5
        val WHITESPACE = Regex("\\s+")
    }
}
