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

package com.jabook.app.jabook.audio

import android.net.Uri
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of re-validating a cached remote media URL.
 */
public enum class RemoteSourceStatus {
    /** Server confirmed the URL is reachable — safe to reuse. */
    ALIVE,

    /** Server explicitly rejected the URL (4xx/5xx) — must re-resolve. */
    DEAD,

    /** Network-level failure — inconclusive; callers must fail-open and keep the URL. */
    UNKNOWN,
}

/**
 * Re-validates cached remote media URLs with a cheap HEAD request before
 * reusing them on replay (ported from spotube's `sourced_track.refreshStream`).
 *
 * Semantics:
 * - HTTP 2xx/3xx → [RemoteSourceStatus.ALIVE]
 * - HTTP 4xx/5xx → [RemoteSourceStatus.DEAD]
 * - IOException (timeout, DNS, no route) → [RemoteSourceStatus.UNKNOWN]
 *   — a network error says nothing about the URL, so callers keep it
 *   (fail-open, never break playback on validator flakiness).
 *
 * Results are memoised for [ttlMs] so consecutive replays of the same chapter
 * don't re-HEAD the server. Uses the app's shared OkHttp client (DoH + Brotli
 * interceptors) — no new connection pools are created; only call/connect/read
 * timeouts are shortened on a derived client.
 *
 * ponytail: single flat TTL map capped at [MAX_CACHE_SIZE] with eldest-entry
 * eviction; swap for LRU+sharding only if profiling shows contention.
 *
 * @property okHttpClient Shared app-wide HTTP client.
 * @property ttlMs In-memory TTL for positive/negative results.
 * @property nowMs Injectable clock for tests.
 */
public class RemoteSourceRevalidator(
    okHttpClient: OkHttpClient,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /**
     * HEAD with a short timeout so validation never stalls playback prep.
     * Derived (not built) from the shared client to reuse its interceptors.
     */
    private val headClient: OkHttpClient =
        okHttpClient
            .newBuilder()
            .callTimeout(HEAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(HEAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HEAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val cache = HashMap<String, CacheEntry>()

    /**
     * Re-validates a remote media URI.
     *
     * @param uri Remote (http/https) URI to check.
     * @return [RemoteSourceStatus.ALIVE] / [DEAD] / [UNKNOWN]; non-http
     *  schemes are always [ALIVE] (not this validator's concern — fail-open).
     */
    public suspend fun revalidate(uri: Uri): RemoteSourceStatus =
        withContext(Dispatchers.IO) {
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@withContext RemoteSourceStatus.ALIVE

            val url = uri.toString()
            val now = nowMs()

            synchronized(cache) {
                val entry = cache[url]
                if (entry != null && now - entry.atMs < ttlMs) {
                    return@withContext entry.status
                }
                if (entry != null) cache.remove(url)
            }

            val status = head(url)
            synchronized(cache) {
                // Cap memory: drop stale entries, then eldest, before inserting.
                if (cache.size >= MAX_CACHE_SIZE) {
                    val cutoff = now - ttlMs
                    cache.entries.removeIf { it.value.atMs < cutoff }
                    while (cache.size >= MAX_CACHE_SIZE) {
                        cache.remove(cache.keys.first())
                    }
                }
                cache[url] = CacheEntry(status, now)
            }
            status
        }

    /** Clears memoised results (e.g. on connectivity regain). */
    public fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun head(url: String): RemoteSourceStatus =
        try {
            val request =
                okhttp3.Request
                    .Builder()
                    .url(url)
                    .head()
                    .build()
            headClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || (response.code in 300..399)) {
                    RemoteSourceStatus.ALIVE
                } else {
                    LogUtils.w(TAG, "Remote source dead (${response.code}): $url")
                    RemoteSourceStatus.DEAD
                }
            }
        } catch (e: IOException) {
            // Inconclusive — network flake, not proof the URL is dead. Fail open.
            LogUtils.w(TAG, "Remote source revalidation inconclusive: $url")
            RemoteSourceStatus.UNKNOWN
        }

    private data class CacheEntry(
        val status: RemoteSourceStatus,
        val atMs: Long,
    )

    private companion object {
        private const val TAG = "RemoteSourceRevalidator"
        private const val HEAD_TIMEOUT_SECONDS = 3L
        private const val DEFAULT_TTL_MS = 60_000L
        private const val MAX_CACHE_SIZE = 128
    }
}
