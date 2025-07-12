package com.jabook.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jabook.app.core.database.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object for chapter operations. */
@Dao
interface ChapterDao {
    /** Get all chapters for an audiobook ordered by chapter number. */
    @Query("SELECT * FROM chapters WHERE audiobook_id = :audiobookId ORDER BY chapter_number ASC")
    fun getChaptersByAudiobookId(audiobookId: String): Flow<List<ChapterEntity>>

    /** Get chapter by ID. */
    @Query("SELECT * FROM chapters WHERE id = :id") suspend fun getChapterById(id: String): ChapterEntity?

    /** Get chapter by audiobook ID and chapter number. */
    @Query("SELECT * FROM chapters WHERE audiobook_id = :audiobookId AND chapter_number = :chapterNumber")
    suspend fun getChapterByNumber(audiobookId: String, chapterNumber: Int): ChapterEntity?

    /** Get downloaded chapters for an audiobook. */
    @Query("SELECT * FROM chapters WHERE audiobook_id = :audiobookId AND is_downloaded = 1 ORDER BY chapter_number ASC")
    fun getDownloadedChapters(audiobookId: String): Flow<List<ChapterEntity>>

    /** Get total duration of all chapters for an audiobook. */
    @Query("SELECT SUM(duration_ms) FROM chapters WHERE audiobook_id = :audiobookId")
    suspend fun getTotalDurationForAudiobook(audiobookId: String): Long

    /** Get count of chapters for an audiobook. */
    @Query("SELECT COUNT(*) FROM chapters WHERE audiobook_id = :audiobookId")
    suspend fun getChapterCountForAudiobook(audiobookId: String): Int

    /** Insert a new chapter. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChapter(chapter: ChapterEntity)

    /** Insert multiple chapters. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChapters(chapters: List<ChapterEntity>)

    /** Update an existing chapter. */
    @Update suspend fun updateChapter(chapter: ChapterEntity)

    /** Update chapter download status. */
    @Query(
        """
        UPDATE chapters 
        SET is_downloaded = :isDownloaded,
            download_progress = :progress
        WHERE id = :id
    """
    )
    suspend fun updateDownloadStatus(id: String, isDownloaded: Boolean, progress: Float)

    /** Delete a chapter. */
    @Delete suspend fun deleteChapter(chapter: ChapterEntity)

    /** Delete all chapters for an audiobook. */
    @Query("DELETE FROM chapters WHERE audiobook_id = :audiobookId") suspend fun deleteChaptersForAudiobook(audiobookId: String)
}
