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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
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
public interface BooksDao {
    /**
     * Get all books ordered by favorites first, then most recently played.
     * Returns a Flow that emits whenever the database changes.
     */
    @Query(
        """
        SELECT * FROM books 
        ORDER BY 
            is_favorite DESC,
            CASE WHEN last_played_date IS NULL THEN 0 ELSE 1 END DESC,
            last_played_date DESC,
            added_date DESC
    """,
    )
    public fun getAllBooksFlow(): Flow<List<BookEntity>>

    /**
     * Get a single book by ID.
     */
    @Query("SELECT * FROM books WHERE id = :bookId")
    public fun getBookFlow(bookId: String): Flow<BookEntity?>

    /**
     * Gets a book by ID (one-shot, not Flow).
     */
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: String): BookEntity?

    /**
     * Gets all books (one-shot).
     */
    @Query("SELECT * FROM books ORDER BY title ASC")
    suspend fun getAllBooks(): List<BookEntity>

    /**
     * Get all chapters for a book, ordered by chapter index.
     */
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index ASC")
    public fun getChaptersForBookFlow(bookId: String): Flow<List<ChapterEntity>>

    /**
     * Observes favorite books, ordered by title.
     */
    @Query("SELECT * FROM books WHERE is_favorite = 1 ORDER BY title ASC")
    public fun getFavoriteBooksFlow(): Flow<List<BookEntity>>

    /**
     * Observes books by download status.
     *
     * @param status Download status string (e.g., "DOWNLOADED", "DOWNLOADING")
     */
    @Query("SELECT * FROM books WHERE download_status = :status ORDER BY title ASC")
    public fun getBooksByDownloadStatusFlow(status: String): Flow<List<BookEntity>>

    /**
     * Observes recently played books, ordered by last played date (most recent first).
     *
     * @param limit Maximum number of books to return
     */
    @Query(
        """
        SELECT * FROM books 
        WHERE last_played_date IS NOT NULL 
        ORDER BY last_played_date DESC 
        LIMIT :limit
        """,
    )
    public fun getRecentlyPlayedBooksFlow(limit: Int = 10): Flow<List<BookEntity>>

    /**
     * Observes books that have been started but not completed.
     */
    @Query(
        """
        SELECT * FROM books 
        WHERE current_position > 0 AND total_progress < 0.98
        ORDER BY last_played_date DESC
        """,
    )
    public fun getInProgressBooksFlow(): Flow<List<BookEntity>>

    /**
     * Updates the favorite status of a book.
     *
     * NOTE: Room Flow will automatically emit new values when this UPDATE completes.
     * The Flow returned by getAllBooksFlow() will be invalidated and re-emit.
     */
    @Query("UPDATE books SET is_favorite = :isFavorite WHERE id = :bookId")
    suspend fun updateFavoriteStatus(
        bookId: String,
        isFavorite: Boolean,
    )

    /**
     * Updates the author of a book.
     */
    @Query("UPDATE books SET author = :author WHERE id = :bookId")
    suspend fun updateAuthor(
        bookId: String,
        author: String,
    )

    /**
     * Updates the description of a book.
     */
    @Query("UPDATE books SET description = :description WHERE id = :bookId")
    suspend fun updateDescription(
        bookId: String,
        description: String,
    )

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
     * Insert books and chapters in a single transaction.
     * efficient for batch updates.
     */
    @Transaction
    suspend fun insertBooksWithChapters(
        books: List<BookEntity>,
        chapters: List<ChapterEntity>,
    ) {
        insertBooks(books)
        insertChapters(chapters)
    }

    /**
     * Upsert (insert or update) books.
     * Faster than INSERT OR REPLACE, avoids conflicts on re-scans.
     */
    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    /**
     * Upsert (insert or update) chapters.
     * Faster than INSERT OR REPLACE, avoids conflicts on re-scans.
     */
    @Upsert
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    /**
     * Upsert books and chapters in a single transaction.
     * Preferred for re-scans to avoid conflicts and improve performance.
     */
    @Transaction
    suspend fun upsertBooksWithChapters(
        books: List<BookEntity>,
        chapters: List<ChapterEntity>,
    ) {
        upsertBooks(books)
        upsertChapters(chapters)
    }

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
            total_progress = :progress,
            current_chapter_index = :chapterIndex,
            last_played_date = :timestamp
        WHERE id = :bookId
        """,
    )
    suspend fun updatePlaybackProgress(
        bookId: String,
        position: Long,
        progress: Float,
        chapterIndex: Int,
        timestamp: Long,
    )

    /**
     * Updates download status and progress.
     */
    @Query(
        """
        UPDATE books 
        SET download_status = :status,
            download_progress = :progress,
            is_downloaded = :isDownloaded
        WHERE id = :bookId
        """,
    )
    suspend fun updateDownloadStatus(
        bookId: String,
        status: String,
        progress: Float,
        isDownloaded: Boolean,
    )

    /**
     * Sets the local path where book files are stored.
     */
    @Query("UPDATE books SET local_path = :path WHERE id = :bookId")
    suspend fun updateLocalPath(
        bookId: String,
        path: String,
    )

    /**
     * Counts total number of books.
     */
    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    /**
     * Searches books by title or author.
     */
    @Query(
        """
        SELECT * FROM books 
        WHERE title LIKE '%' || :query || '%' 
           OR author LIKE '%' || :query || '%'
        ORDER BY title ASC
        """,
    )
    public fun searchBooksFlow(query: String): Flow<List<BookEntity>>

    /**
     * Updates per-book playback settings.
     */
    @Query("UPDATE books SET rewind_duration = :rewindDuration, forward_duration = :forwardDuration WHERE id = :bookId")
    suspend fun updateBookSettings(
        bookId: String,
        rewindDuration: Int?,
        forwardDuration: Int?,
    )

    /**
     * Resets all per-book playback settings to global defaults (NULL).
     */
    @Query("UPDATE books SET rewind_duration = NULL, forward_duration = NULL")
    suspend fun resetAllBookSettings()

    /**
     * Gets all book IDs and local paths for validation.
     * Used to check which books still exist on filesystem during scan.
     */
    @Query("SELECT id, local_path FROM books")
    suspend fun getAllBookPaths(): List<BookPathInfo>

    /**
     * Deletes a book by ID.
     * Chapters will be cascade deleted due to foreign key constraint.
     */
    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    /**
     * Finds a book by its source URL (e.g. RuTracker topic link).
     */
    @Query("SELECT * FROM books WHERE source_url = :sourceUrl LIMIT 1")
    public fun getBookBySourceUrlFlow(sourceUrl: String): Flow<BookEntity?>

    /**
     * Finds a book by its source URL (one-shot).
     */
    @Query("SELECT * FROM books WHERE source_url = :sourceUrl LIMIT 1")
    suspend fun getBookBySourceUrl(sourceUrl: String): BookEntity?

    /**
     * Updates cover URL.
     */
    @Query("UPDATE books SET cover_url = :url WHERE id = :bookId")
    suspend fun updateCoverUrl(
        bookId: String,
        url: String,
    )

    /**
     * Updates cover local path.
     */
    @Query("UPDATE books SET cover_path = :path WHERE id = :bookId")
    suspend fun updateCoverPath(
        bookId: String,
        path: String,
    )
}

/**
 * Lightweight data class for book path validation.
 * Only contains fields needed to check if book still exists.
 */
public data class BookPathInfo(
    public val id: String,
    @androidx.room.ColumnInfo(name = "local_path") val localPath: String?,
)
