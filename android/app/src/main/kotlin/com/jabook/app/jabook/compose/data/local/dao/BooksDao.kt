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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for books and chapters.
 *
 * Uses Flow for reactive updates - UI will automatically update
 * when database changes.
 */
@Dao
interface BooksDao {
    /**
     * Get all books ordered by most recently played first.
     * Returns a Flow that emits whenever the database changes.
     */
    @Query(
        """
        SELECT * FROM books 
        ORDER BY 
            CASE WHEN last_played_date IS NULL THEN 0 ELSE 1 END DESC,
            last_played_date DESC,
            added_date DESC
    """,
    )
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    /**
     * Get a single book by ID.
     */
    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBookFlow(bookId: String): Flow<BookEntity?>

    /**
     * Get all chapters for a book, ordered by chapter index.
     */
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index ASC")
    fun getChaptersForBookFlow(bookId: String): Flow<List<ChapterEntity>>

    /**
     * Insert or replace a book.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    /**
     * Insert or replace multiple books.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    /**
     * Insert or replace a chapter.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    /**
     * Insert or replace multiple chapters.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    /**
     * Update a book.
     */
    @Update
    suspend fun updateBook(book: BookEntity)

    /**
     * Update current playback position for a book.
     */
    @Query(
        """
        UPDATE books 
        SET current_position = :position,
            current_chapter_index = :chapterIndex,
            last_played_date = :timestamp
        WHERE id = :bookId
    """,
    )
    suspend fun updatePlaybackPosition(
        bookId: String,
        position: Long,
        chapterIndex: Int,
        timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * Update download progress for a book.
     */
    @Query("UPDATE books SET download_progress = :progress, is_downloaded = :isComplete WHERE id = :bookId")
    suspend fun updateDownloadProgress(
        bookId: String,
        progress: Float,
        isComplete: Boolean,
    )

    /**
     * Delete a book (chapters will be cascade deleted).
     */
    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    /**
     * Delete all books.
     */
    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()

    /**
     * Search books by title or author.
     */
    @Query(
        """
        SELECT * FROM books 
        WHERE title LIKE '%' || :query || '%' 
        OR author LIKE '%' || :query || '%'
        ORDER BY last_played_date DESC
    """,
    )
    fun searchBooks(query: String): Flow<List<BookEntity>>
}
