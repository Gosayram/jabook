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

package com.jabook.app.jabook.audio.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing chapter metadata for a book.
 */
@Entity(tableName = "chapter_metadata")
data class ChapterMetadataEntity(
    @PrimaryKey
    val id: String, // bookId + "_" + fileIndex
    val bookId: String,
    val fileIndex: Int,
    val title: String,
    val filePath: String?,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val duration: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)
