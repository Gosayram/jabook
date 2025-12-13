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

package com.jabook.app.jabook.audio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chapter metadata.
 */
@Dao
interface ChapterMetadataDao {
    /**
     * Gets all chapter metadata for a book.
     */
    @Query("SELECT * FROM chapter_metadata WHERE bookId = :bookId ORDER BY fileIndex ASC")
    fun getChapters(bookId: String): Flow<List<ChapterMetadataEntity>>

    /**
     * Gets a specific chapter by ID.
     */
    @Query("SELECT * FROM chapter_metadata WHERE id = :id")
    fun getChapter(id: String): Flow<ChapterMetadataEntity?>

    /**
     * Inserts or updates chapter metadata.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(chapter: ChapterMetadataEntity)

    /**
     * Inserts or updates multiple chapters.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(chapters: List<ChapterMetadataEntity>)

    /**
     * Deletes all chapters for a book.
     */
    @Query("DELETE FROM chapter_metadata WHERE bookId = :bookId")
    suspend fun deleteChapters(bookId: String)

    /**
     * Deletes a specific chapter.
     */
    @Query("DELETE FROM chapter_metadata WHERE id = :id")
    suspend fun deleteChapter(id: String)
}
