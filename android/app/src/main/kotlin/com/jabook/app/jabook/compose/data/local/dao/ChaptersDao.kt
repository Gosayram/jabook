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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chapters.
 *
 * Provides reactive queries for chapter data with Flow-based observations.
 */
@Dao
interface ChaptersDao {
    /**
     * Observes all chapters for a specific book, ordered by chapter index.
     */
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index ASC")
    fun getChaptersByBookIdFlow(bookId: String): Flow<List<ChapterEntity>>

    /**
     * Gets a single chapter by ID (one-shot).
     */
    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: String): ChapterEntity?

    /**
     * Gets all chapters for a book (one-shot).
     */
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index ASC")
    suspend fun getChaptersByBookId(bookId: String): List<ChapterEntity>

    /**
     * Gets a chapter by book ID and chapter index.
     */
    @Query("SELECT * FROM chapters WHERE book_id = :bookId AND chapter_index = :chapterIndex")
    suspend fun getChapterByIndex(
        bookId: String,
        chapterIndex: Int,
    ): ChapterEntity?

    /**
     * Inserts or replaces a chapter.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    /**
     * Inserts or replaces multiple chapters.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    /**
     * Updates an existing chapter.
     */
    @Update
    suspend fun update(chapter: ChapterEntity)

    /**
     * Deletes a chapter.
     */
    @Delete
    suspend fun delete(chapter: ChapterEntity)

    /**
     * Deletes all chapters for a book.
     */
    @Query("DELETE FROM chapters WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)

    /**
     * Updates chapter playback progress.
     *
     * @param chapterId Chapter identifier
     * @param position Current position within chapter (milliseconds)
     * @param isCompleted Whether chapter is completed
     */
    @Query(
        """
        UPDATE chapters 
        SET position = :position,
            is_completed = :isCompleted
        WHERE id = :chapterId
        """,
    )
    suspend fun updateChapterProgress(
        chapterId: String,
        position: Long,
        isCompleted: Boolean,
    )

    /**
     * Updates download status for a chapter.
     */
    @Query("UPDATE chapters SET is_downloaded = :isDownloaded WHERE id = :chapterId")
    suspend fun updateDownloadStatus(
        chapterId: String,
        isDownloaded: Boolean,
    )

    /**
     * Marks all chapters in a book as downloaded.
     */
    @Query("UPDATE chapters SET is_downloaded = 1 WHERE book_id = :bookId")
    suspend fun markAllAsDownloaded(bookId: String)

    /**
     * Gets count of downloaded chapters for a book.
     */
    @Query("SELECT COUNT(*) FROM chapters WHERE book_id = :bookId AND is_downloaded = 1")
    suspend fun getDownloadedCount(bookId: String): Int

    /**
     * Gets total count of chapters for a book.
     */
    @Query("SELECT COUNT(*) FROM chapters WHERE book_id = :bookId")
    suspend fun getTotalCount(bookId: String): Int
}
