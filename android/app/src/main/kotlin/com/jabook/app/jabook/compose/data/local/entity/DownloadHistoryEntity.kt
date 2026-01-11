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

package com.jabook.app.jabook.compose.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing download history.
 *
 * Stores information about completed, failed, or cancelled downloads.
 */
@Entity(tableName = "download_history")
public data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    public val id: Int = 0,
    /**
     * Book ID from catalog.
     */
    public val bookId: String,
    /**
     * Book title for display.
     */
    public val bookTitle: String,
    /**
     * Final status: "completed", "failed", "cancelled".
     */
    public val status: String,
    /**
     * When download started (timestamp).
     */
    public val startedAt: Long,
    /**
     * When download finished (timestamp).
     */
    public val completedAt: Long,
    /**
     * Total bytes downloaded (if available).
     */
    public val totalBytes: Long?,
    /**
     * Error message for failed downloads.
     */
    public val errorMessage: String?,
)
