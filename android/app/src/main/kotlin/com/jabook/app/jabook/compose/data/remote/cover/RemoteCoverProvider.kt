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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Silent remote cover lookup against public APIs (OpenLibrary, then Google Books).
 *
 * Exactly one HTTP GET per source, no HEAD probes. Network failures never throw —
 * callers get null and the book is simply left without a cover.
 */
public class RemoteCoverProvider(
    private val client: OkHttpClient,
    loggerFactory: LoggerFactory,
    olBaseUrl: String = OPENLIBRARY_BASE,
    googleBaseUrl: String = GOOGLE_BOOKS_BASE,
) {
    private val logger = loggerFactory.get(TAG)
    private val json = Json { ignoreUnknownKeys = true }
    private val openLibraryUrl = olBaseUrl.trimEnd('/') + "/search.json"
    private val googleBooksUrl = googleBaseUrl.trimEnd('/') + "/books/v1/volumes"

    /**
     * Look up a cover URL for the given title/author.
     *
     * @return direct image URL, or null when nothing matched accurately.
     */
    public suspend fun lookup(
        title: String,
        author: String,
    ): String? =
        withContext(Dispatchers.IO) {
            lookupOpenLibrary(title, author) ?: lookupGoogleBooks(title, author)
        }

    private fun lookupOpenLibrary(
        title: String,
        author: String,
    ): String? {
        val query = "title:${escape(title)} author:${escape(author)}"
        val url =
            "$openLibraryUrl?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&fields=cover_i,title,author_name&limit=3"
        val root = fetchJson(url) ?: return null
        val docs = root.jsonObject["docs"]?.jsonArray ?: return null
        for (doc in docs) {
            val obj = doc.jsonObject
            val coverId = obj["cover_i"]?.jsonPrimitive?.longOrNull ?: continue
            val candidateTitle = obj["title"]?.jsonPrimitive?.content
            val candidateAuthors =
                obj["author_name"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            if (CoverMatchPolicy.isAcceptable(candidateTitle, candidateAuthors, title, author)) {
                return "$OPENLIBRARY_COVER_BASE/b/id/$coverId-L.jpg"
            }
        }
        logger.d { "OpenLibrary: no acceptable cover for \"$title\"" }
        return null
    }

    private fun lookupGoogleBooks(
        title: String,
        author: String,
    ): String? {
        val query = "intitle:${escape(title)} inauthor:${escape(author)}"
        val url =
            "$googleBooksUrl?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&maxResults=5&printType=books"
        val root = fetchJson(url) ?: return null
        val items = root.jsonObject["items"]?.jsonArray ?: return null
        for (item in items) {
            val volumeInfo = item.jsonObject["volumeInfo"]?.jsonObject ?: continue
            val candidateTitle = volumeInfo["title"]?.jsonPrimitive?.content
            val candidateAuthors =
                volumeInfo["authors"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            if (!CoverMatchPolicy.isAcceptable(candidateTitle, candidateAuthors, title, author)) continue
            val thumbnail =
                volumeInfo["imageLinks"]
                    ?.jsonObject
                    ?.get("thumbnail")
                    ?.jsonPrimitive
                    ?.content
            if (!thumbnail.isNullOrBlank()) return thumbnail.replace("http://", "https://")
        }
        logger.d { "Google Books: no acceptable cover for \"$title\"" }
        return null
    }

    private fun fetchJson(url: String): kotlinx.serialization.json.JsonElement? =
        try {
            client
                .newCall(Request.Builder().url(url).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return null
                    response.body.string().let { json.parseToJsonElement(it) }
                }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.w({ "Cover lookup request failed: $url" }, e)
            null
        }

    private fun escape(raw: String): String = raw.trim().replace(':', ' ')

    private companion object {
        const val TAG = "RemoteCoverProvider"
        const val OPENLIBRARY_BASE = "https://openlibrary.org"
        const val OPENLIBRARY_COVER_BASE = "https://covers.openlibrary.org"
        const val GOOGLE_BOOKS_BASE = "https://www.googleapis.com"
    }
}
