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

import android.os.SystemClock

/**
 * P-84: Generic event deduplicator with time-window based filtering.
 *
 * Deduplicates events by key within a configurable time window.
 * Used by both WidgetActionDeduplicator and TrackTransitionCoordinator
 * to unify deduplication logic.
 *
 * @param T Event type
 * @param windowMs Time window in milliseconds for considering events as duplicates
 * @param maxEntries Maximum number of entries to track (LRU eviction)
 * @param keyExtractor Function to extract a unique key from the event
 */
internal class EventDeduplicator<T>(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val keyExtractor: (T) -> Any,
) {
    private val seen = HashMap<Any, Long>(maxEntries)

    /**
     * Checks if an event is a duplicate within the time window.
     *
     * @param event The event to check
     * @return true if the event is a duplicate, false otherwise
     */
    fun isDuplicate(event: T): Boolean {
        val key = keyExtractor(event)
        val now = SystemClock.elapsedRealtime()
        val lastSeen = seen[key]

        return if (lastSeen != null && now - lastSeen < windowMs) {
            true
        } else {
            seen[key] = now
            if (seen.size > maxEntries) evictOldest()
            false
        }
    }

    /**
     * Clears all tracked events.
     */
    fun clear() {
        seen.clear()
    }

    /**
     * Returns the number of tracked entries.
     */
    fun size(): Int = seen.size

    private fun evictOldest() {
        val now = SystemClock.elapsedRealtime()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= windowMs) {
                iterator.remove()
            }
        }
        if (seen.size > maxEntries) {
            val oldest = seen.minByOrNull { it.value }
            if (oldest != null) seen.remove(oldest.key)
        }
    }

    companion object {
        internal const val DEFAULT_WINDOW_MS = 100L
        internal const val DEFAULT_MAX_ENTRIES = 50
    }
}
