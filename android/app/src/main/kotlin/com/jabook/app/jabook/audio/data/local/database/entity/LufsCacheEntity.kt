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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache entry for LUFS (Loudness Units Full Scale) analysis results per audio file.
 *
 * Stores the measured loudness of a file so that [LoudnessNormalizer] can apply
 * the correct gain without re-analysing on every playback start.
 *
 * **Invalidation strategy:** Before using a cached value, the repository checks
 * [fileSize] and [fileLastModified] against the current file on disk. If either
 * differs, the entry is considered stale and a re-analysis is triggered.
 *
 * P-02: LUFS cache with content-hash-based invalidation.
 */
@Entity(
    tableName = "lufs_cache",
    indices = [
        Index("bookId", name = "index_lufs_cache_book_id"),
    ],
)
public data class LufsCacheEntity(
    /** Absolute path to the audio file — natural primary key. */
    @PrimaryKey
    val filePath: String,
    /** Parent book identifier for bulk lookups. */
    val bookId: String,
    /** Measured loudness in LUFS (typically -30..0). */
    val lufsValue: Float,
    /** File size in bytes at the time of analysis — fast staleness check. */
    val fileSize: Long,
    /** File last-modified timestamp — detects content replacement. */
    val fileLastModified: Long,
    /** Epoch millis when the analysis was performed. */
    val analyzedAt: Long = System.currentTimeMillis(),
)
