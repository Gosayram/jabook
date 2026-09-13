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

/**
 * In-memory failed-source memory for remote playback (ported from spotube's
 * `sourced_track` candidate skipping).
 *
 * For each mediaId it remembers the ordered candidate URIs resolved for that
 * chapter and which of them already failed, so on a playback error the next
 * sibling URI can be tried BEFORE an expensive full re-resolution. When all
 * candidates are exhausted, [nextCandidate] returns `null` and the caller
 * falls through to its existing re-resolution/fallback path unchanged.
 *
 * ponytail: in-memory only (no Room) — failed-source memory is per-session
 * heuristic state; persist only if repeat app-starts re-fail the same URLs.
 * Capped at [MAX_MEDIA_IDS] LRU entries.
 */
public class SourceAttemptCache {
    private data class Entry(
        val candidates: List<Uri>,
        val failed: MutableSet<Uri>,
    )

    // accessOrder=true → get() bumps recency; eldest evicted beyond cap.
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    /**
     * Records the ordered candidate URIs for a mediaId, resetting any prior
     * attempt state for it.
     *
     * @param mediaId Stable chapter/track identifier.
     * @param uris Ordered candidate URIs (first = preferred).
     */
    public fun rememberCandidates(
        mediaId: String,
        uris: List<Uri>,
    ) {
        synchronized(entries) {
            entries[mediaId] = Entry(uris.distinct(), mutableSetOf())
            evictIfNeeded()
        }
    }

    /**
     * Marks a candidate URI as failed for a mediaId.
     * No-op if the mediaId has no remembered candidates or the URI is unknown.
     */
    public fun markFailed(
        mediaId: String,
        uri: Uri,
    ) {
        synchronized(entries) {
            entries[mediaId]?.failed?.add(uri)
            evictIfNeeded()
        }
    }

    /**
     * Returns the first candidate URI that hasn't failed yet, or `null` when
     * every remembered candidate is exhausted (caller proceeds with its own
     * fallback / re-resolution).
     */
    public fun nextCandidate(mediaId: String): Uri? =
        synchronized(entries) {
            val entry = entries[mediaId]
            entry?.candidates?.firstOrNull { it !in entry.failed }
        }

    /** Drops all remembered state (e.g. on connectivity regain). */
    public fun clear() {
        synchronized(entries) { entries.clear() }
    }

    private fun evictIfNeeded() {
        while (entries.size > MAX_MEDIA_IDS) {
            val eldest = entries.keys.iterator()
            eldest.next()
            eldest.remove()
        }
    }

    private companion object {
        private const val MAX_MEDIA_IDS = 32
    }
}
