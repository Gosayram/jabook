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
 * P-61: Data models for offline sync queue.
 *
 * Defines the types of operations that can be queued for sync
 * and the queue item structure for Room persistence.
 *
 * Used by SyncQueueManager to persist operations when offline
 * and drain them when connectivity is restored.
 */

/**
 * Types of sync operations.
 */
public enum class SyncType {
    /** Playback position update. */
    POSITION_UPDATE,

    /** Bookmark created. */
    BOOKMARK_CREATE,

    /** Bookmark deleted. */
    BOOKMARK_DELETE,

    /** Book completed. */
    BOOK_COMPLETED,

    /** Playback speed changed. */
    SPEED_CHANGED,

    /** Sleep timer state changed. */
    SLEEP_TIMER_CHANGED,
}

/**
 * A queued sync operation for offline persistence.
 *
 * @property id Unique identifier
 * @property type Type of sync operation
 * @property payload Serialized data (JSON)
 * @property createdAt Timestamp when the operation was queued
 * @property retryCount Number of failed retry attempts
 * @property lastError Last error message, if any
 */
public data class SyncQueueItem(
    val id: String,
    val type: SyncType,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
) {
    /** Whether this item has exceeded max retries. */
    val isExhausted: Boolean
        get() = retryCount >= MAX_RETRIES

    /** Whether this item is pending (not exhausted). */
    val isPending: Boolean
        get() = !isExhausted

    /**
     * Creates a copy with incremented retry count and error message.
     */
    public fun withRetry(error: String): SyncQueueItem =
        copy(
            retryCount = retryCount + 1,
            lastError = error,
        )

    public companion object {
        /** Maximum retry attempts before giving up. */
        internal const val MAX_RETRIES = 5
    }
}
