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

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem

/**
 * Room entity representing download history.
 *
 * Stores information about completed, failed, or cancelled downloads.
 */
@Keep
@Entity(
    tableName = "download_history",
    indices = [
        Index(value = ["status"]),
        Index(value = ["completedAt"]),
    ],
)
public data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val bookId: String,
    val bookTitle: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long,
    val totalBytes: Long?,
    val errorMessage: String?,
)

public fun DownloadHistoryEntity.toDownloadHistoryItem(): DownloadHistoryItem =
    DownloadHistoryItem(
        id = id,
        bookId = bookId,
        bookTitle = bookTitle,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        totalBytes = totalBytes,
        errorMessage = errorMessage,
    )

public fun DownloadHistoryItem.toDownloadHistoryEntity(): DownloadHistoryEntity =
    DownloadHistoryEntity(
        id = id,
        bookId = bookId,
        bookTitle = bookTitle,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        totalBytes = totalBytes,
        errorMessage = errorMessage,
    )
