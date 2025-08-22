package com.jabook.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jabook.app.core.database.entities.AudiobookEntity
import com.jabook.app.core.database.entities.DownloadStatus
import kotlinx.coroutines.flow.Flow

/** Data Access Object for audiobook operations. Provides reactive database access using Flow for UI updates. */
@Dao
interface AudiobookDao {
  /** Get all audiobooks ordered by last played date. */
  @Query("SELECT * FROM audiobooks ORDER BY last_played_at DESC, added_at DESC")
  fun getAllAudiobooks(): Flow<List<AudiobookEntity>>

  /** Get audiobook by ID. */
  @Query("SELECT * FROM audiobooks WHERE id = :id")
  suspend fun getAudiobookById(id: String): AudiobookEntity?

  /** Get audiobook by ID as Flow for reactive updates. */
  @Query("SELECT * FROM audiobooks WHERE id = :id")
  fun getAudiobookByIdFlow(id: String): Flow<AudiobookEntity?>

  /** Get audiobooks by category. */
  @Query("SELECT * FROM audiobooks WHERE category = :category ORDER BY title ASC")
  fun getAudiobooksByCategory(category: String): Flow<List<AudiobookEntity>>

  /** Get favorite audiobooks. */
  @Query("SELECT * FROM audiobooks WHERE is_favorite = 1 ORDER BY title ASC")
  fun getFavoriteAudiobooks(): Flow<List<AudiobookEntity>>

  /** Get currently playing audiobooks (with progress). */
  @Query(
    """
        SELECT * FROM audiobooks 
        WHERE current_position_ms > 0 AND is_completed = 0 
        ORDER BY last_played_at DESC
    """,
  )
  fun getCurrentlyPlayingAudiobooks(): Flow<List<AudiobookEntity>>

  /** Get completed audiobooks. */
  @Query("SELECT * FROM audiobooks WHERE is_completed = 1 ORDER BY last_played_at DESC")
  fun getCompletedAudiobooks(): Flow<List<AudiobookEntity>>

  /** Get downloaded audiobooks. */
  @Query("SELECT * FROM audiobooks WHERE download_status = 'COMPLETED' ORDER BY title ASC")
  fun getDownloadedAudiobooks(): Flow<List<AudiobookEntity>>

  /** Get audiobooks by download status. */
  @Query("SELECT * FROM audiobooks WHERE download_status = :status ORDER BY updated_at DESC")
  fun getAudiobooksByDownloadStatus(status: DownloadStatus): Flow<List<AudiobookEntity>>

  /** Search audiobooks by title or author. */
  @Query(
    """
        SELECT * FROM audiobooks 
        WHERE title LIKE '%' || :query || '%' 
        OR author LIKE '%' || :query || '%'
        OR narrator LIKE '%' || :query || '%'
        ORDER BY title ASC
    """,
  )
  fun searchAudiobooks(query: String): Flow<List<AudiobookEntity>>

  /** Get all distinct categories. */
  @Query("SELECT DISTINCT category FROM audiobooks ORDER BY category ASC")
  fun getAllCategories(): Flow<List<String>>

  /** Insert a new audiobook. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAudiobook(audiobook: AudiobookEntity)

  /** Insert multiple audiobooks. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>)

  /** Update an existing audiobook. */
  @Update suspend fun updateAudiobook(audiobook: AudiobookEntity)

  /** Update playback position. */
  @Query(
    """
        UPDATE audiobooks 
        SET current_position_ms = :positionMs, 
            last_played_at = datetime('now'),
            updated_at = datetime('now')
        WHERE id = :id
    """,
  )
  suspend fun updatePlaybackPosition(
    id: String,
    positionMs: Long,
  )

  /** Update download status and progress. */
  @Query(
    """
        UPDATE audiobooks 
        SET download_status = :status, 
            download_progress = :progress,
            download_error = :error,
            updated_at = datetime('now')
        WHERE id = :id
    """,
  )
  suspend fun updateDownloadStatus(
    id: String,
    status: DownloadStatus,
    progress: Float,
    error: String? = null,
  )

  /** Mark audiobook as favorite/unfavorite. */
  @Query(
    """
        UPDATE audiobooks 
        SET is_favorite = :isFavorite,
            updated_at = datetime('now')
        WHERE id = :id
    """,
  )
  suspend fun updateFavoriteStatus(
    id: String,
    isFavorite: Boolean,
  )

  /** Mark audiobook as completed. */
  @Query(
    """
        UPDATE audiobooks 
        SET is_completed = :isCompleted,
            current_position_ms = CASE WHEN :isCompleted = 1 THEN duration_ms ELSE current_position_ms END,
            updated_at = datetime('now')
        WHERE id = :id
    """,
  )
  suspend fun updateCompletionStatus(
    id: String,
    isCompleted: Boolean,
  )

  /** Update user rating. */
  @Query(
    """
        UPDATE audiobooks 
        SET user_rating = :rating,
            updated_at = datetime('now')
        WHERE id = :id
    """,
  )
  suspend fun updateUserRating(
    id: String,
    rating: Float?,
  )

  /** Update playback speed. */
  @Query(
    """
        UPDATE audiobooks 
        SET playback_speed = :speed,
            updated_at = datetime('now')
        WHERE id = :id
    """,
  )
  suspend fun updatePlaybackSpeed(
    id: String,
    speed: Float,
  )

  /** Delete an audiobook. */
  @Delete suspend fun deleteAudiobook(audiobook: AudiobookEntity)

  /** Delete audiobook by ID. */
  @Query("DELETE FROM audiobooks WHERE id = :id")
  suspend fun deleteAudiobookById(id: String)

  /** Delete all audiobooks with failed downloads. */
  @Query("DELETE FROM audiobooks WHERE download_status = 'FAILED'")
  suspend fun deleteFailedDownloads()

  /** Get total count of audiobooks. */
  @Query("SELECT COUNT(*) FROM audiobooks")
  suspend fun getAudiobooksCount(): Int

  /** Get total size of downloaded audiobooks. */
  @Query("SELECT SUM(file_size) FROM audiobooks WHERE download_status = 'COMPLETED'")
  suspend fun getTotalDownloadedSize(): Long

  /** Reset all playback positions (for testing/debug). */
  @Query(
    """
        UPDATE audiobooks 
        SET current_position_ms = 0, 
            is_completed = 0,
            updated_at = datetime('now')
    """,
  )
  suspend fun resetAllPlaybackPositions()
}
