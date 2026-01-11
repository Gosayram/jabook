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

/**
 * Manages audio file duration caching and database retrieval fallback.
 *
 * According to best practices: cache duration after getting it from player (primary source)
 * or MediaMetadataRetriever (fallback). This avoids repeated calls and improves performance.
 * This cache is synchronized with database via MethodChannel (Flutter side).
 */
public class DurationManager {
    // Cache for file durations (filePath -> duration in ms)
    private val durationCache = mutableMapOf<String, Long>()

    /**
     * returns a read-only view of the duration cache.
     */
    public fun getDurationCacheMap(): Map<String, Long> = durationCache

    // Callback for getting duration from database (set from Flutter via MethodChannel)
    // This allows PlayerStateHelper to request durations from database when cache miss
    private var getDurationFromDbCallback: ((String) -> Long?)? = null

    /**
     * Sets callback for getting duration from database.
     * This is called from Flutter via MethodChannel to enable database lookup.
     *
     * @param callback Callback that takes file path and returns duration in ms, or null
     */
    public fun setGetDurationFromDbCallback(callback: ((String) -> Long?)?) {
        getDurationFromDbCallback = callback
    }

    /**
     * Gets duration for file path.
     * Checks cache first, then database via callback, then returns null.
     *
     * @param filePath Absolute path to the audio file
     * @return Duration in milliseconds, or null if not found
     */
    public fun getDurationForFile(filePath: String): Long? {
        // Check cache first (fast path)
        val cached = durationCache[filePath]
        if (cached != null && cached > 0) {
            return cached
        }

        // Cache miss - try database via callback
        val dbDuration = getDurationFromDbCallback?.invoke(filePath)
        if (dbDuration != null && dbDuration > 0) {
            // Cache it for future use
            durationCache[filePath] = dbDuration
            return dbDuration
        }

        return null
    }

    /**
     * Gets cached duration for file path.
     *
     * @param filePath Absolute path to the audio file
     * @return Cached duration in milliseconds, or null if not cached
     */
    public fun getCachedDuration(filePath: String): Long? = durationCache[filePath]

    /**
     * Saves duration to cache.
     *
     * @param filePath Absolute path to the audio file
     * @param durationMs Duration in milliseconds
     */
    public fun saveDurationToCache(
        filePath: String,
        durationMs: Long,
    ) {
        durationCache[filePath] = durationMs
    }

    /**
     * Clears all cached durations.
     */
    public fun clearCache() {
        durationCache.clear()
    }
}
