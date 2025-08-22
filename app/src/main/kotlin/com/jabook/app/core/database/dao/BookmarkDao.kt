package com.jabook.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jabook.app.core.database.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object for bookmark operations. */
@Dao
interface BookmarkDao {
    /** Get all bookmarks for an audiobook ordered by position. */
    @Query("SELECT * FROM bookmarks WHERE audiobook_id = :audiobookId ORDER BY position_ms ASC")
    fun getBookmarksByAudiobookId(audiobookId: String): Flow<List<BookmarkEntity>>

    /** Get bookmark by ID. */
    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: String): BookmarkEntity?

    /** Get all bookmarks ordered by creation date. */
    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    /** Get bookmarks count for an audiobook. */
    @Query("SELECT COUNT(*) FROM bookmarks WHERE audiobook_id = :audiobookId")
    suspend fun getBookmarksCountForAudiobook(audiobookId: String): Int

    /** Insert a new bookmark. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    /** Update an existing bookmark. */
    @Update suspend fun updateBookmark(bookmark: BookmarkEntity)

    /** Delete a bookmark. */
    @Delete suspend fun deleteBookmark(bookmark: BookmarkEntity)

    /** Delete bookmark by ID. */
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    /** Delete all bookmarks for an audiobook. */
    @Query("DELETE FROM bookmarks WHERE audiobook_id = :audiobookId")
    suspend fun deleteBookmarksForAudiobook(audiobookId: String)
}
