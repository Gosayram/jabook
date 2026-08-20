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
 * P-62: Sync status model for UI display.
 *
 * Represents the current synchronization state between device and cloud.
 * Used by SyncStatusIcon composable in PlayerScreen.
 */
public sealed class SyncStatus {
    /** All data is synchronized. */
    public data object Synced : SyncStatus()

    /** Synchronization is in progress. */
    public data object Syncing : SyncStatus()

    /** Operations queued for sync when online. */
    public data class Pending(
        val count: Int,
    ) : SyncStatus()

    /** Synchronization failed. */
    public data class Error(
        val message: String,
    ) : SyncStatus()

    /**
     * Whether the status indicates the user should be notified.
     */
    public fun requiresAttention(): Boolean = this is Error || (this is Pending && count > 10)

    /**
     * Human-readable label for accessibility.
     */
    public fun toLabel(): String =
        when (this) {
            is Synced -> "Синхронизировано"
            is Syncing -> "Синхронизация…"
            is Pending -> "Ожидает: $count"
            is Error -> "Ошибка: $message"
        }
}
