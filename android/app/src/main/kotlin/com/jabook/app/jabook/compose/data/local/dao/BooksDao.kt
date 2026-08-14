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
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

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
    public fun getAllBooksFlowInternal(): Flow<List<BookEntity>>

    public fun getAllBooksFlow(): Flow<List<BookEntity>> = getAllBooksFlowInternal().distinctUntilChanged()

    /**
     * Get a single book by ID.
     */
    @Query("SELECT * FROM books WHERE id = :bookId")
    public fun getBookFlowInternal(bookId: String): Flow<BookEntity?>

    public fun getBookFlow(bookId: String): Flow<BookEntity?> = getBookFlowInternal(bookId).distinctUntilChanged()

    /**
     * Gets a book by ID (one-shot, not Flow).
     */
    @Query("SELECT * FROM books WHERE id = :bookId")
    public suspend fun getBookById(bookId: String): BookEntity?

    /**
     * Gets all books (one-shot).
     */
    @Query("SELECT * FROM books ORDER BY title ASC")
    public suspend fun getAllBooks(): List<BookEntity>

    /**
     * Get all chapters for a book, ordered by chapter index.
     */
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index ASC")
    public fun getChaptersForBookFlowInternal(bookId: String): Flow<List<ChapterEntity>>

    public fun getChaptersForBookFlow(bookId: String): Flow<List<ChapterEntity>> =
        getChaptersForBookFlowInternal(bookId).distinctUntilChanged()

    /**
     * Observes favorite books, ordered by title.
     */
    @Query("SELECT * FROM books WHERE is_favorite = 1 ORDER BY title ASC")
    public fun getFavoriteBooksFlowInternal(): Flow<List<BookEntity>>

    public fun getFavoriteBooksFlow(): Flow<List<BookEntity>> = getFavoriteBooksFlowInternal().distinctUntilChanged()

    /**
     * Observes books by download status.
     *
     * @param status Download status string (e.g., "DOWNLOADED", "DOWNLOADING")
     */
    @Query("SELECT * FROM books WHERE download_status = :status ORDER BY title ASC")
    public fun getBooksByDownloadStatusFlowInternal(status: String): Flow<List<BookEntity>>

    public fun getBooksByDownloadStatusFlow(status: String): Flow<List<BookEntity>> =
        getBooksByDownloadStatusFlowInternal(status).distinctUntilChanged()

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
    public fun getRecentlyPlayedBooksFlowInternal(limit: Int = 10): Flow<List<BookEntity>>

    public fun getRecentlyPlayedBooksFlow(limit: Int = 10): Flow<List<BookEntity>> =
        getRecentlyPlayedBooksFlowInternal(limit).distinctUntilChanged()

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
    public fun getInProgressBooksFlowInternal(): Flow<List<BookEntity>>

    public fun getInProgressBooksFlow(): Flow<List<BookEntity>> = getInProgressBooksFlowInternal().distinctUntilChanged()

    /**
     * Updates the favorite status of a book.
     *
     * NOTE: Room Flow will automatically emit new values when this UPDATE completes.
     * The Flow returned by getAllBooksFlow() will be invalidated and re-emit.
     */
    @Query("UPDATE books SET is_favorite = :isFavorite WHERE id = :bookId")
    public suspend fun updateFavoriteStatus(
        bookId: String,
        isFavorite: Boolean,
    ): Int

    /**
     * Updates the author of a book.
     */
    @Query("UPDATE books SET author = :author WHERE id = :bookId")
    public suspend fun updateAuthor(
        bookId: String,
        author: String,
    )

    /**
     * Updates the description of a book.
     */
    @Query("UPDATE books SET description = :description WHERE id = :bookId")
    public suspend fun updateDescription(
        bookId: String,
        description: String,
    )

    /**
     * Insert or replace a book.
     */
    @Upsert
    public suspend fun insertBook(book: BookEntity)

    /**
     * Insert or replace multiple books.
     */
    @Upsert
    public suspend fun insertBooks(books: List<BookEntity>)

    /**
     * Insert or replace a chapter.
     */
    @Upsert
    public suspend fun insertChapter(chapter: ChapterEntity)

    /**
     * Insert or replace multiple chapters.
     */
    @Upsert
    public suspend fun insertChapters(chapters: List<ChapterEntity>)

    /**
     * Insert books and chapters in a single transaction.
     * efficient for batch updates.
     */
    @Transaction
    public suspend fun insertBooksWithChapters(
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
    public suspend fun upsertBooks(books: List<BookEntity>)

    /**
     * Upsert (insert or update) chapters.
     * Faster than INSERT OR REPLACE, avoids conflicts on re-scans.
     */
    @Upsert
    public suspend fun upsertChapters(chapters: List<ChapterEntity>)

    /**
     * Upsert books and chapters in a single transaction.
     * Preferred for re-scans to avoid conflicts and improve performance.
     */
    @Transaction
    public suspend fun upsertBooksWithChapters(
        books: List<BookEntity>,
        chapters: List<ChapterEntity>,
    ) {
        upsertBooks(books)
        upsertChapters(chapters)
    }

    /**
     * Persists filesystem metadata found by a library scan without overwriting playback
     * progress or per-book settings.
     */
    @Transaction
    public suspend fun upsertScannedBooksWithChapters(
        books: List<BookEntity>,
        chapters: List<ChapterEntity>,
    ) {
        insertScannedBooks(books)
        books.forEach { book ->
            updateScannedBook(
                id = book.id,
                title = book.title,
                author = book.author,
                totalDuration = book.totalDuration,
                downloadStatus = book.downloadStatus,
                isDownloaded = book.isDownloaded,
                localPath = book.localPath,
            )
        }

        val chaptersByBook = chapters.groupBy(ChapterEntity::bookId)
        books.forEach { scannedBook ->
            val existingChapters = getChaptersForScan(scannedBook.id)
            // Embedded M4B chapters share one file path, so merge keys must include the
            // in-file start offset; a whole-file row (null start) never matches a segment.
            val existingByKey =
                existingChapters
                    .filter { !it.fileUrl.isNullOrBlank() }
                    .associateBy { chapterMergeKey(it.fileUrl, it.startPositionMs) }
            val scannedChapters = chaptersByBook[scannedBook.id].orEmpty()
            val newIndexByKey =
                scannedChapters
                    .filter { !it.fileUrl.isNullOrBlank() }
                    .associate { chapterMergeKey(it.fileUrl, it.startPositionMs) to it.chapterIndex }
            val indexMapping =
                existingChapters
                    .mapNotNull { existing ->
                        newIndexByKey[chapterMergeKey(existing.fileUrl, existing.startPositionMs)]
                            ?.let { newIndex -> existing.chapterIndex to newIndex }
                    }.toMap()
            val obsoleteChapterIndexes =
                existingChapters
                    .filter { chapterMergeKey(it.fileUrl, it.startPositionMs) !in newIndexByKey }
                    .map(ChapterEntity::chapterIndex)
            val mergedChapters =
                scannedChapters.map { scanned ->
                    existingByKey[chapterMergeKey(scanned.fileUrl, scanned.startPositionMs)]?.let { existing ->
                        scanned.copy(
                            id = existing.id,
                            position = existing.position,
                            isCompleted = existing.isCompleted,
                            lufsValue = existing.lufsValue,
                            startPositionMs = scanned.startPositionMs,
                            endPositionMs = scanned.endPositionMs,
                        )
                    } ?: scanned
                }

            deleteScannedChapters(scannedBook.id)
            insertScannedChapters(mergedChapters)

            if (obsoleteChapterIndexes.isNotEmpty()) {
                deleteBookmarksForChapterIndexes(scannedBook.id, obsoleteChapterIndexes)
            }

            val currentChapterIndex = getBookById(scannedBook.id)?.currentChapterIndex
            indexMapping[currentChapterIndex]?.let { updateCurrentChapterIndex(scannedBook.id, it) }
                ?: resetPlaybackProgress(scannedBook.id)
            indexMapping.forEach { (oldIndex, _) ->
                updateBookmarkChapterIndex(scannedBook.id, oldIndex, -oldIndex - 1)
            }
            indexMapping.forEach { (oldIndex, newIndex) ->
                updateBookmarkChapterIndex(scannedBook.id, -oldIndex - 1, newIndex)
            }
        }
    }

    private fun chapterMergeKey(
        fileUrl: String?,
        startPositionMs: Long?,
    ): String = "$fileUrl#${startPositionMs ?: -1L}"

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertScannedBooks(books: List<BookEntity>)

    @Query(
        """
        UPDATE books
        SET title = :title,
            author = :author,
            total_duration = :totalDuration,
            download_status = :downloadStatus,
            download_progress = CASE WHEN :isDownloaded THEN 1.0 ELSE download_progress END,
            local_path = :localPath,
            is_downloaded = :isDownloaded
        WHERE id = :id
        """,
    )
    public suspend fun updateScannedBook(
        id: String,
        title: String,
        author: String,
        totalDuration: Long,
        downloadStatus: String,
        isDownloaded: Boolean,
        localPath: String?,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertScannedChapters(chapters: List<ChapterEntity>)

    @Query(
        "SELECT * FROM chapters WHERE book_id = :bookId",
    )
    public suspend fun getChaptersForScan(bookId: String): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE book_id = :bookId")
    public suspend fun deleteScannedChapters(bookId: String)

    @Query("UPDATE books SET current_chapter_index = :chapterIndex WHERE id = :bookId")
    public suspend fun updateCurrentChapterIndex(
        bookId: String,
        chapterIndex: Int,
    )

    @Query(
        "UPDATE books SET current_position = 0, total_progress = 0, current_chapter_index = 0 WHERE id = :bookId",
    )
    public suspend fun resetPlaybackProgress(bookId: String)

    @Query("UPDATE bookmarks SET chapter_index = :newIndex WHERE book_id = :bookId AND chapter_index = :oldIndex")
    public suspend fun updateBookmarkChapterIndex(
        bookId: String,
        oldIndex: Int,
        newIndex: Int,
    )

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId AND chapter_index IN (:chapterIndexes)")
    public suspend fun deleteBookmarksForChapterIndexes(
        bookId: String,
        chapterIndexes: List<Int>,
    )

    /**
     * Update a book.
     */
    @Update
    public suspend fun updateBook(book: BookEntity)

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
    public suspend fun updatePlaybackProgress(
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
    public suspend fun updateDownloadStatus(
        bookId: String,
        status: String,
        progress: Float,
        isDownloaded: Boolean,
    )

    /**
     * Sets the local path where book files are stored.
     */
    @Query("UPDATE books SET local_path = :path WHERE id = :bookId")
    public suspend fun updateLocalPath(
        bookId: String,
        path: String,
    )

    /**
     * Counts total number of books.
     */
    @Query("SELECT COUNT(*) FROM books")
    public suspend fun getBookCount(): Int

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
    public fun searchBooksFlowInternal(query: String): Flow<List<BookEntity>>

    public fun searchBooksFlow(query: String): Flow<List<BookEntity>> = searchBooksFlowInternal(query).distinctUntilChanged()

    /**
     * Searches books by title or author with optional transliterated fallback query.
     */
    @Query(
        """
        SELECT * FROM books
        WHERE title LIKE '%' || :query || '%'
           OR author LIKE '%' || :query || '%'
           OR title LIKE '%' || :fallbackQuery || '%'
           OR author LIKE '%' || :fallbackQuery || '%'
        ORDER BY title ASC
        """,
    )
    public fun searchBooksFlowWithFallbackInternal(
        query: String,
        fallbackQuery: String,
    ): Flow<List<BookEntity>>

    public fun searchBooksFlowWithFallback(
        query: String,
        fallbackQuery: String,
    ): Flow<List<BookEntity>> = searchBooksFlowWithFallbackInternal(query, fallbackQuery).distinctUntilChanged()

    /**
     * Searches books via FTS5 index.
     *
     * @param ftsQuery Query in SQLite FTS5 MATCH format
     */
    @RawQuery(observedEntities = [BookEntity::class])
    public fun searchBooksByFtsFlowInternal(query: SupportSQLiteQuery): Flow<List<BookEntity>>

    public fun searchBooksByFtsFlow(query: SupportSQLiteQuery): Flow<List<BookEntity>> =
        searchBooksByFtsFlowInternal(query).distinctUntilChanged()

    /**
     * Updates per-book playback settings.
     */
    @Query("UPDATE books SET rewind_duration = :rewindDuration, forward_duration = :forwardDuration WHERE id = :bookId")
    public suspend fun updateBookSettings(
        bookId: String,
        rewindDuration: Int?,
        forwardDuration: Int?,
    )

    /**
     * Resets all per-book playback settings to global defaults (NULL).
     */
    @Query("UPDATE books SET rewind_duration = NULL, forward_duration = NULL")
    public suspend fun resetAllBookSettings()

    /**
     * Gets all book IDs and local paths for validation.
     * Used to check which books still exist on filesystem during scan.
     */
    @Query("SELECT id, local_path FROM books")
    public suspend fun getAllBookPaths(): List<BookPathInfo>

    /**
     * Deletes a book by ID.
     * Chapters will be cascade deleted due to foreign key constraint.
     */
    @Query("DELETE FROM books WHERE id = :bookId")
    public suspend fun deleteById(bookId: String)

    /**
     * Finds a book by its source URL (e.g. RuTracker topic link).
     */
    @Query("SELECT * FROM books WHERE source_url = :sourceUrl LIMIT 1")
    public fun getBookBySourceUrlFlowInternal(sourceUrl: String): Flow<BookEntity?>

    public fun getBookBySourceUrlFlow(sourceUrl: String): Flow<BookEntity?> =
        getBookBySourceUrlFlowInternal(sourceUrl).distinctUntilChanged()

    /**
     * Finds a book by its source URL (one-shot).
     */
    @Query("SELECT * FROM books WHERE source_url = :sourceUrl LIMIT 1")
    public suspend fun getBookBySourceUrl(sourceUrl: String): BookEntity?

    /**
     * Updates cover URL.
     */
    @Query("UPDATE books SET cover_url = :url WHERE id = :bookId")
    public suspend fun updateCoverUrl(
        bookId: String,
        url: String,
    )

    /**
     * Updates cover local path.
     */
    @Query("UPDATE books SET cover_path = :path WHERE id = :bookId")
    public suspend fun updateCoverPath(
        bookId: String,
        path: String,
    )

    /**
     * Atomically updates both cover URL and cover path for a book.
     *
     * Prevents inconsistent state where one field is updated but the other is not
     * (e.g., app crash between two separate UPDATE statements).
     */
    @Transaction
    public suspend fun updateCoverPathAndUrl(
        bookId: String,
        path: String,
    ) {
        updateCoverPath(bookId, path)
        updateCoverUrl(bookId, path)
    }

    /**
     * Updates the LUFS loudness estimate for a book.
     *
     * This value is computed by [LufsAnalysisWorker] during background loudness analysis
     * and consumed by [LufsLoudnessCompensationPolicy] during book transitions.
     *
     * @param bookId target book ID
     * @param lufsValue estimated LUFS value (negative, e.g. -20.0), or null to clear
     */
    @Query("UPDATE books SET lufs_value = :lufsValue WHERE id = :bookId")
    public suspend fun updateLufsValue(
        bookId: String,
        lufsValue: Double?,
    )

    /**
     * Updates the per-book preferred playback speed.
     *
     * When set, this overrides the global speed setting for this specific book.
     *
     * @param bookId target book ID
     * @param speed playback speed multiplier (e.g. 1.5f), or null to use global default
     */
    @Query("UPDATE books SET preferred_speed = :speed WHERE id = :bookId")
    public suspend fun updatePreferredSpeed(
        bookId: String,
        speed: Float?,
    )

    /**
     * Returns the per-book preferred playback speed.
     */
    @Query("SELECT preferred_speed FROM books WHERE id = :bookId LIMIT 1")
    public suspend fun getPreferredSpeed(bookId: String): Float?

    /**
     * Returns the average preferred speed for books by the same author
     * as [bookId], when at least one per-book preference exists.
     */
    @Query(
        """
        SELECT AVG(preferred_speed)
        FROM books
        WHERE author = (SELECT author FROM books WHERE id = :bookId LIMIT 1)
          AND preferred_speed IS NOT NULL
    """,
    )
    public suspend fun getAveragePreferredSpeedForAuthorOfBook(bookId: String): Double?

    /**
     * Returns all book IDs that do not yet have a LUFS analysis value.
     * Used by [LufsAnalysisWorker] to find books that need background analysis.
     */
    @Query("SELECT id FROM books WHERE lufs_value IS NULL AND local_path IS NOT NULL")
    public suspend fun getBookIdsWithoutLufs(): List<String>

    /**
     * Returns the local path for a given book ID.
     * Used by [LufsAnalysisWorker] to locate audio files for analysis.
     */
    @Query("SELECT local_path FROM books WHERE id = :bookId LIMIT 1")
    public suspend fun getBookLocalPath(bookId: String): String?

    /**
     * Updates the per-book EQ preset override.
     *
     * When set, this overrides the global EQ preset for this specific book.
     * Set to null to clear the override and use the global preset.
     *
     * @param bookId target book ID
     * @param preset EQ preset name (e.g., "MALE_NARRATOR", "NIGHT_LISTENING"), or null to clear
     */
    @Query("UPDATE books SET eq_preset_override = :preset WHERE id = :bookId")
    public suspend fun updateEqPresetOverride(
        bookId: String,
        preset: String?,
    )

    /**
     * Returns the per-book EQ preset override, or null if not set.
     *
     * @param bookId target book ID
     * @return preset name string, or null if using global default
     */
    @Query("SELECT eq_preset_override FROM books WHERE id = :bookId LIMIT 1")
    public suspend fun getEqPresetOverride(bookId: String): String?
}

/**
 * Lightweight data class for book path validation.
 * Only contains fields needed to check if book still exists.
 */
public data class BookPathInfo(
    val id: String,
    @androidx.room.ColumnInfo(name = "local_path") val localPath: String?,
)
