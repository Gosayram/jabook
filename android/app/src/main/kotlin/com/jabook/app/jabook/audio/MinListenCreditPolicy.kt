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
 * Minimum-listen floor for stats credit (inspired by spotube's min-listen
 * scrobble floor: credit a listen only after `min(50% of duration, 4 minutes)`).
 *
 * Pure policy: decides whether a finished listening session earns stats/habit
 * credit. This gate applies ONLY to stats (session/habit records) — it must not
 * change the 95% completion semantics used for bookmarks/position/completion badges
 * ([CompletionStatusHelper]).
 */
internal object MinListenCreditPolicy {
    internal const val MAX_FLOOR_MS = 240_000L
    internal const val DURATION_FRACTION = 0.5

    /**
     * Minimum listened time required for credit.
     *
     * @param durationMs Chapter duration; `<= 0` (unknown) falls back to the 4-minute cap
     */
    internal fun floorMs(durationMs: Long): Long {
        if (durationMs <= 0) return MAX_FLOOR_MS
        return (durationMs * DURATION_FRACTION).toLong().coerceAtMost(MAX_FLOOR_MS)
    }

    /**
     * @param listenedMs Time actually listened in this session (end - start position)
     * @param durationMs Chapter duration; `<= 0` means unknown
     * @param alreadyCredited Whether this chapter item already earned its one-time credit
     */
    internal fun shouldCredit(
        listenedMs: Long,
        durationMs: Long,
        alreadyCredited: Boolean,
    ): Boolean {
        if (alreadyCredited) return false
        return listenedMs >= floorMs(durationMs)
    }
}
