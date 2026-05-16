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

import kotlin.random.Random

/**
 * Exponential backoff with jitter for playback error retries.
 *
 * Calculates retry delay using: `base * 2^attempt + random(0, jitterMs)`
 * which produces intervals like: ~1s, ~2s, ~4s (with jitter).
 *
 * Jitter prevents thundering herd when multiple components retry simultaneously
 * after a shared failure (e.g., network outage, SD card remount).
 *
 * @param baseMs Base delay in milliseconds for the first retry (default: 1000ms).
 * @param maxRetries Maximum number of retry attempts (default: 3).
 * @param jitterMs Maximum random jitter added to each delay (default: 500ms).
 * @param maxDelayMs Hard cap on total delay to avoid excessive waits (default: 10_000ms).
 */
internal class RetryBackoffPolicy(
    private val baseMs: Long = DEFAULT_BASE_MS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val jitterMs: Long = DEFAULT_JITTER_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
) {
    /**
     * Calculates the backoff delay for the given [attempt] (0-indexed).
     *
     * Returns `null` if [attempt] exceeds [maxRetries], signalling that retries
     * are exhausted and the caller should fall through to the next recovery action
     * (skip track, rescan, notify user, etc.).
     *
     * @param attempt 0-indexed retry attempt number.
     * @return Delay in milliseconds including jitter, or `null` if exhausted.
     */
    fun calculateDelay(attempt: Int): Long? {
        if (attempt < 0 || attempt >= maxRetries) return null

        val exponentialDelay = baseMs * (1L shl attempt) // base * 2^attempt
        val jitter = if (jitterMs > 0) Random.nextLong(0, jitterMs + 1) else 0L
        return (exponentialDelay + jitter).coerceAtMost(maxDelayMs)
    }

    /** Whether [attempt] is within the allowed retry window. */
    fun hasRetriesLeft(attempt: Int): Boolean = attempt in 0 until maxRetries

    companion object {
        const val DEFAULT_BASE_MS = 1_000L
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_JITTER_MS = 500L
        const val DEFAULT_MAX_DELAY_MS = 10_000L
    }
}
