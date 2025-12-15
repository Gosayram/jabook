// Copyright 2025 Jabook Contributors
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
 * Download queue entry for persistent queue management.
 */
@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey
    val bookId: String,
    /**
     * Priority value (0=LOW, 1=NORMAL, 2=HIGH, 3=URGENT).
     */
    val priority: Int,
    /**
     * Position in queue (lower = earlier).
     */
    val queuePosition: Int,
    /**
     * Current status: "queued", "active", "paused", "completed", "cancelled", "failed".
     */
    val status: String,
    /**
     * When download was added to queue.
     */
    val createdAt: Long,
    /**
     * Last update timestamp.
     */
    val updatedAt: Long,
)
