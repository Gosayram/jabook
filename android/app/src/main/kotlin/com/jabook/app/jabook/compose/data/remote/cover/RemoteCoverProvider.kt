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
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/**
 * Silent remote cover lookup against public APIs (OpenLibrary, then Google Books).
 *
 * Two metadata GETs max per lookup; candidates from both sources are scored by
 * [CoverMatchPolicy], ranked, and the top ones are downloaded and validated by
 * [CoverImageValidator] (at most [MAX_IMAGE_DOWNLOADS] image GETs). Network
 * failures never throw — callers get null and the book stays without a cover.
 */
public class RemoteCoverProvider(
    private val client: OkHttpClient,
    loggerFactory: LoggerFactory,
    olBaseUrl: String = OPENLIBRARY_BASE,
    googleBaseUrl: String = GOOGLE_BOOKS_BASE,
    olCoverBaseUrl: String = OPENLIBRARY_COVER_BASE,
) {
    private val logger = loggerFactory.get(TAG)
    private val json = Json { ignoreUnknownKeys = true }
    private val openLibraryUrl = olBaseUrl.trimEnd('/') + "/search.json"
    private val googleBooksUrl = googleBaseUrl.trimEnd('/') + "/books/v1/volumes"
    private val openLibraryCoverUrl = olCoverBaseUrl.trimEnd('/')

    /**
     * Look up a cover URL for the given title/author.
     *
     * @return direct image URL (validated at byte level), or null when nothing matched accurately.
     */
    public suspend fun lookup(
        title: String,
        author: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val candidates =
                buildList {
                    addAll(collectOpenLibrary(title, author))
                    addAll(collectGoogleBooks(title, author))
                }.sortedByDescending { it.second }

            for ((index, candidate) in candidates.withIndex()) {
                if (index >= MAX_IMAGE_DOWNLOADS) break
                val bytes = fetchImageBytes(candidate.first) ?: continue
                if (CoverImageValidator.isPlausibleCover(bytes)) return@withContext candidate.first
            }
            logger.d { "No plausible cover for \"$title\"" }
            null
        }

    /** @return (image url, match score) pairs, highest evidence last — callers sort. */
    private fun collectOpenLibrary(
        title: String,
        author: String,
    ): List<Pair<String, Float>> {
        val query = "title:${escape(title)} author:${escape(author)}"
        val url =
            "$openLibraryUrl?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&fields=cover_i,title,author_name&limit=3"
        val root = fetchJson(url) ?: return emptyList()
        val docs = root.jsonObject["docs"]?.jsonArray ?: return emptyList()
        val candidates = mutableListOf<Pair<String, Float>>()
        for (doc in docs) {
            val obj = doc.jsonObject
            val coverId = obj["cover_i"]?.jsonPrimitive?.longOrNull ?: continue
            val candidateTitle = obj["title"]?.jsonPrimitive?.content
            val candidateAuthors =
                obj["author_name"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val match =
                CoverMatchPolicy.score(candidateTitle, candidateAuthors, title, author)
            if (match > 0f) candidates.add("$openLibraryCoverUrl/b/id/$coverId-L.jpg" to match)
        }
        return candidates
    }

    private fun collectGoogleBooks(
        title: String,
        author: String,
    ): List<Pair<String, Float>> {
        val query = "intitle:${escape(title)} inauthor:${escape(author)}"
        val url =
            "$googleBooksUrl?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&maxResults=5&printType=books"
        val root = fetchJson(url) ?: return emptyList()
        val items = root.jsonObject["items"]?.jsonArray ?: return emptyList()
        val candidates = mutableListOf<Pair<String, Float>>()
        for (item in items) {
            val volumeInfo = item.jsonObject["volumeInfo"]?.jsonObject ?: continue
            val candidateTitle = volumeInfo["title"]?.jsonPrimitive?.content
            val candidateAuthors =
                volumeInfo["authors"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val match =
                CoverMatchPolicy.score(candidateTitle, candidateAuthors, title, author)
            if (match <= 0f) continue
            val thumbnail =
                volumeInfo["imageLinks"]
                    ?.jsonObject
                    ?.get("thumbnail")
                    ?.jsonPrimitive
                    ?.content
            if (!thumbnail.isNullOrBlank()) {
                candidates.add(thumbnail.replace("http://", "https://") to match)
            }
        }
        return candidates
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

    private fun fetchImageBytes(url: String): ByteArray? =
        try {
            client
                .newCall(Request.Builder().url(url).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return null
                    readCappedBody(response.body)
                }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.w({ "Cover image download failed: $url" }, e)
            null
        }

    /**
     * Reads a response body with [MAX_IMAGE_BYTES] cap, mirroring
     * RutrackerParser.readCappedBody: the guard holds DURING the copy so an
     * unbounded body cannot materialize before the check runs.
     */
    private fun readCappedBody(body: ResponseBody): ByteArray {
        val contentLength = body.contentLength()
        check(contentLength <= MAX_IMAGE_BYTES) { "Image too large: $contentLength bytes" }
        return body.use { bounded ->
            val input = bounded.byteStream()
            val output = ByteArrayOutputStream(if (contentLength > 0) contentLength.toInt() else 16 * 1024)
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                check(total <= MAX_IMAGE_BYTES) { "Image too large: $total bytes" }
                output.write(chunk, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun escape(raw: String): String = raw.trim().replace(':', ' ')

    private companion object {
        const val TAG = "RemoteCoverProvider"
        const val OPENLIBRARY_BASE = "https://openlibrary.org"
        const val OPENLIBRARY_COVER_BASE = "https://covers.openlibrary.org"
        const val GOOGLE_BOOKS_BASE = "https://www.googleapis.com"
        const val MAX_IMAGE_DOWNLOADS = 3
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    }
}
