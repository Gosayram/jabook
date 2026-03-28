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

package com.jabook.app.jabook.audio.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_sessions",
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["started_at"]),
        Index(value = ["ended_at"]),
    ],
)
public data class ListeningSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,
    @ColumnInfo(name = "position_start_ms")
    val positionStartMs: Long,
    @ColumnInfo(name = "position_end_ms")
    val positionEndMs: Long? = null,
    @ColumnInfo(name = "speed_factor")
    val speedFactor: Float,
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

public data class ListeningDayStatEntity(
    val day: String,
    val playTimeMs: Long,
    val contentTimeMs: Long,
    val sessionsCount: Int,
)
