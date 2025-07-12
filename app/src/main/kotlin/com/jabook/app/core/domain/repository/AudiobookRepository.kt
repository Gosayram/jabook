package com.jabook.app.core.domain.repository

import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Bookmark
import com.jabook.app.core.domain.model.Chapter
import com.jabook.app.core.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for audiobook operations.
 * Defines the contract for data access in the domain layer.
 */
interface AudiobookRepository {
    /**
     * Get all audiobooks as a reactive stream.
     */
    fun getAllAudiobooks(): Flow<List<Audiobook>>

    /**
     * Get audiobook by ID.
     */
    suspend fun getAudiobookById(id: String): Audiobook?

    /**
     * Get audiobook by ID as a reactive stream.
     */
    fun getAudiobookByIdFlow(id: String): Flow<Audiobook?>

    /**
     * Get audiobooks by category.
     */
    fun getAudiobooksByCategory(category: String): Flow<List<Audiobook>>

    /**
     * Get favorite audiobooks.
     */
    fun getFavoriteAudiobooks(): Flow<List<Audiobook>>

    /**
     * Get currently playing audiobooks.
     */
    fun getCurrentlyPlayingAudiobooks(): Flow<List<Audiobook>>

    /**
     * Get completed audiobooks.
     */
    fun getCompletedAudiobooks(): Flow<List<Audiobook>>

    /**
     * Get downloaded audiobooks.
     */
    fun getDownloadedAudiobooks(): Flow<List<Audiobook>>

    /**
     * Get audiobooks by download status.
     */
    fun getAudiobooksByDownloadStatus(status: DownloadStatus): Flow<List<Audiobook>>

    /**
     * Search audiobooks by query.
     */
    fun searchAudiobooks(query: String): Flow<List<Audiobook>>

    /**
     * Get all categories.
     */
    fun getAllCategories(): Flow<List<String>>

    /**
     * Insert or update audiobook.
     */
    suspend fun upsertAudiobook(audiobook: Audiobook)

    /**
     * Insert or update multiple audiobooks.
     */
    suspend fun upsertAudiobooks(audiobooks: List<Audiobook>)

    /**
     * Update playback position.
     */
    suspend fun updatePlaybackPosition(
        id: String,
        positionMs: Long,
    )

    /**
     * Update download status.
     */
    suspend fun updateDownloadStatus(
        id: String,
        status: DownloadStatus,
        progress: Float,
        error: String? = null,
    )

    /**
     * Update favorite status.
     */
    suspend fun updateFavoriteStatus(
        id: String,
        isFavorite: Boolean,
    )

    /**
     * Update completion status.
     */
    suspend fun updateCompletionStatus(
        id: String,
        isCompleted: Boolean,
    )

    /**
     * Update user rating.
     */
    suspend fun updateUserRating(
        id: String,
        rating: Float?,
    )

    /**
     * Update playback speed.
     */
    suspend fun updatePlaybackSpeed(
        id: String,
        speed: Float,
    )

    /**
     * Delete audiobook.
     */
    suspend fun deleteAudiobook(id: String)

    /**
     * Delete failed downloads.
     */
    suspend fun deleteFailedDownloads()

    /**
     * Get audiobooks count.
     */
    suspend fun getAudiobooksCount(): Int

    /**
     * Get total downloaded size.
     */
    suspend fun getTotalDownloadedSize(): Long

    /**
     * Reset all playback positions.
     */
    suspend fun resetAllPlaybackPositions()

    // Chapter operations

    /**
     * Get chapters for audiobook.
     */
    fun getChaptersByAudiobookId(audiobookId: String): Flow<List<Chapter>>

    /**
     * Get chapter by ID.
     */
    suspend fun getChapterById(id: String): Chapter?

    /**
     * Get chapter by number.
     */
    suspend fun getChapterByNumber(
        audiobookId: String,
        chapterNumber: Int,
    ): Chapter?

    /**
     * Insert or update chapter.
     */
    suspend fun upsertChapter(chapter: Chapter)

    /**
     * Insert or update multiple chapters.
     */
    suspend fun upsertChapters(chapters: List<Chapter>)

    /**
     * Update chapter download status.
     */
    suspend fun updateChapterDownloadStatus(
        id: String,
        isDownloaded: Boolean,
        progress: Float,
    )

    /**
     * Delete chapter.
     */
    suspend fun deleteChapter(id: String)

    /**
     * Delete chapters for audiobook.
     */
    suspend fun deleteChaptersForAudiobook(audiobookId: String)

    // Bookmark operations

    /**
     * Get bookmarks for audiobook.
     */
    fun getBookmarksByAudiobookId(audiobookId: String): Flow<List<Bookmark>>

    /**
     * Get bookmark by ID.
     */
    suspend fun getBookmarkById(id: String): Bookmark?

    /**
     * Get all bookmarks.
     */
    fun getAllBookmarks(): Flow<List<Bookmark>>

    /**
     * Insert or update bookmark.
     */
    suspend fun upsertBookmark(bookmark: Bookmark)

    /**
     * Delete bookmark.
     */
    suspend fun deleteBookmark(id: String)

    /**
     * Delete bookmarks for audiobook.
     */
    suspend fun deleteBookmarksForAudiobook(audiobookId: String)
}
