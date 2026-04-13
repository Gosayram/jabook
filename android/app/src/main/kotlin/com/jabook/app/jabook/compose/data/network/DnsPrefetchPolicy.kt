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

package com.jabook.app.jabook.compose.data.network

/**
 * Policy for DNS prefetching of mirror hosts to reduce TTFB on first real request.
 *
 * Warms the DNS cache by performing a background lookup for configured mirror hosts
 * after the first frame is rendered. This is a best-effort optimization and should
 * never block the UI or crash on network failures.
 *
 * BP-15.5 reference: DNS prefetch — фиктивный DNS lookup для mirror хоста через
 * `InetAddress.getByName(...)` в background coroutine.
 *
 * Usage:
 * ```
 * // In Application.onCreate or after first frame
 * CoroutineScope(Dispatchers.IO).launch {
 *     DnsPrefetchPolicy.prefetch(mirrorHost)
 * }
 * ```
 */
public object DnsPrefetchPolicy {
    /**
     * Minimum interval between prefetch attempts for the same host.
     * Prevents excessive DNS lookups in a short time window.
     */
    public const val PREFETCH_COOLDOWN_MS: Long = 60_000L // 1 minute

    /**
     * Maximum number of hosts to track for cooldown purposes.
     * Prevents unbounded memory growth.
     */
    public const val MAX_TRACKED_HOSTS: Int = 20

    /**
     * Result of a DNS prefetch attempt.
     *
     * @property host The hostname that was resolved.
     * @property success Whether the resolution succeeded.
     * @property addresses Resolved addresses (if successful).
     * @property elapsedMs Time taken for the lookup.
     * @property error Error message (if failed).
     */
    public data class PrefetchResult(
        public val host: String,
        public val success: Boolean,
        public val addresses: List<String> = emptyList(),
        public val elapsedMs: Long = 0L,
        public val error: String? = null,
    )

    /**
     * Determines whether a prefetch should be attempted for the given host.
     *
     * @param host The hostname to check.
     * @param lastPrefetchTimestamps Map of hostnames to their last prefetch timestamps.
     * @param currentTimeMs Current timestamp in milliseconds.
     * @return true if prefetch should be attempted.
     */
    public fun shouldPrefetch(
        host: String,
        lastPrefetchTimestamps: Map<String, Long>,
        currentTimeMs: Long,
    ): Boolean {
        if (host.isBlank()) return false

        val lastTime = lastPrefetchTimestamps[host] ?: return true
        val elapsed = currentTimeMs - lastTime
        return elapsed >= PREFETCH_COOLDOWN_MS
    }

    /**
     * Returns the pruned timestamps map after adding a new host.
     * Removes oldest entries when the map exceeds [MAX_TRACKED_HOSTS].
     */
    public fun pruneTimestamps(
        timestamps: Map<String, Long>,
        newHost: String,
        currentTimeMs: Long,
    ): Map<String, Long> {
        val updated = timestamps.toMutableMap()
        updated[newHost] = currentTimeMs

        if (updated.size > MAX_TRACKED_HOSTS) {
            // Remove oldest entries
            val sorted = updated.entries.sortedBy { it.value }
            val toKeep = sorted.takeLast(MAX_TRACKED_HOSTS)
            updated.clear()
            toKeep.forEach { updated[it.key] = it.value }
        }

        return updated
    }

    /**
     * Extracts the hostname from a full URL for prefetch purposes.
     *
     * @param url Full URL (e.g., "https://rutracker.org/forum/...")
     * @return Hostname (e.g., "rutracker.org") or null if parsing fails.
     */
    public fun extractHost(url: String): String? {
        return try {
            val afterScheme = url.substringAfter("://")
            if (afterScheme == url) return null // No scheme found
            val beforePath = afterScheme.substringBefore('/')
            val beforePort = beforePath.substringBefore(':')
            beforePort.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
