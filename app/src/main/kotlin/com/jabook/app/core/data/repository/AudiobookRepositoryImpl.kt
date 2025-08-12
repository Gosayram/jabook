package com.jabook.app.core.data.repository

import com.jabook.app.core.data.mapper.AudiobookMapper.toDomain
import com.jabook.app.core.data.mapper.AudiobookMapper.toDomainList
import com.jabook.app.core.data.mapper.AudiobookMapper.toEntity
import com.jabook.app.core.database.dao.AudiobookDao
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Chapter
import com.jabook.app.core.domain.model.Bookmark
import com.jabook.app.core.domain.repository.AudiobookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.jabook.app.core.database.entities.DownloadStatus as EntityDownloadStatus
import com.jabook.app.core.domain.model.DownloadStatus as DomainDownloadStatus

/** Implementation of AudiobookRepository using Room database. Provides concrete data access operations for audiobooks. */
@Singleton
class AudiobookRepositoryImpl
@Inject
constructor(
    private val audiobookDao: AudiobookDao,
    private val chapterHelper: ChapterHelper,
    private val bookmarkHelper: BookmarkHelper,
) : AudiobookRepository {
    override suspend fun upsertChapter(chapter: Chapter) {
        chapterHelper.upsertChapter(chapter)
    }

    override suspend fun upsertChapters(chapters: List<Chapter>) {
        chapterHelper.upsertChapters(chapters)
    }

    override suspend fun upsertBookmark(bookmark: Bookmark) {
        bookmarkHelper.upsertBookmark(bookmark)
    }

    override fun getAllAudiobooks(): Flow<List<Audiobook>> {
        return audiobookDao.getAllAudiobooks().map { it.toDomainList() }
    }

    override suspend fun getAudiobookById(id: String): Audiobook? {
        return audiobookDao.getAudiobookById(id)?.toDomain()
    }

    override fun getAudiobookByIdFlow(id: String): Flow<Audiobook?> {
        return audiobookDao.getAudiobookByIdFlow(id).map { it?.toDomain() }
    }

    override fun getAudiobooksByCategory(category: String): Flow<List<Audiobook>> {
        return audiobookDao.getAudiobooksByCategory(category).map { it.toDomainList() }
    }

    override fun getFavoriteAudiobooks(): Flow<List<Audiobook>> {
        return audiobookDao.getFavoriteAudiobooks().map { it.toDomainList() }
    }

    override fun getCurrentlyPlayingAudiobooks(): Flow<List<Audiobook>> {
        return audiobookDao.getCurrentlyPlayingAudiobooks().map { it.toDomainList() }
    }

    override fun getCompletedAudiobooks(): Flow<List<Audiobook>> {
        return audiobookDao.getCompletedAudiobooks().map { it.toDomainList() }
    }

    override fun getDownloadedAudiobooks(): Flow<List<Audiobook>> {
        return audiobookDao.getDownloadedAudiobooks().map { it.toDomainList() }
    }

    override fun getAudiobooksByDownloadStatus(status: DomainDownloadStatus): Flow<List<Audiobook>> {
        val entityStatus = status.toEntityStatus()
        return audiobookDao.getAudiobooksByDownloadStatus(entityStatus).map { it.toDomainList() }
    }

    override fun searchAudiobooks(query: String): Flow<List<Audiobook>> {
        return audiobookDao.searchAudiobooks(query).map { it.toDomainList() }
    }

    override fun getAllCategories(): Flow<List<String>> {
        return audiobookDao.getAllCategories()
    }

    override suspend fun upsertAudiobook(audiobook: Audiobook) {
        audiobookDao.insertAudiobook(audiobook.toEntity())
    }

    override suspend fun upsertAudiobooks(audiobooks: List<Audiobook>) {
        audiobookDao.insertAudiobooks(audiobooks.map { it.toEntity() })
    }

    override suspend fun updatePlaybackPosition(id: String, positionMs: Long) {
        audiobookDao.updatePlaybackPosition(id, positionMs)
    }

    override suspend fun updateDownloadStatus(id: String, status: DomainDownloadStatus, progress: Float, error: String?) {
        audiobookDao.updateDownloadStatus(id, status.toEntityStatus(), progress, error)
    }

    override suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) {
        audiobookDao.updateFavoriteStatus(id, isFavorite)
    }

    override suspend fun updateCompletionStatus(id: String, isCompleted: Boolean) {
        audiobookDao.updateCompletionStatus(id, isCompleted)
    }

    override suspend fun updateUserRating(id: String, rating: Float?) {
        audiobookDao.updateUserRating(id, rating)
    }

    override suspend fun updatePlaybackSpeed(id: String, speed: Float) {
        audiobookDao.updatePlaybackSpeed(id, speed)
    }

    override suspend fun deleteAudiobook(id: String) {
        audiobookDao.deleteAudiobookById(id)
    }

    override suspend fun deleteFailedDownloads() {
        audiobookDao.deleteFailedDownloads()
    }

    override suspend fun getAudiobooksCount(): Int {
        return audiobookDao.getAudiobooksCount()
    }

    override suspend fun getTotalDownloadedSize(): Long {
        return audiobookDao.getTotalDownloadedSize()
    }

    override suspend fun resetAllPlaybackPositions() {
        audiobookDao.resetAllPlaybackPositions()
    }

    override fun getChaptersByAudiobookId(audiobookId: String): Flow<List<com.jabook.app.core.domain.model.Chapter>> {
        return chapterHelper.getChaptersByAudiobookId(audiobookId)
    }

    override suspend fun getChapterById(id: String): com.jabook.app.core.domain.model.Chapter? {
        return chapterHelper.getChapterById(id)
    }

    override suspend fun getChapterByNumber(audiobookId: String, chapterNumber: Int): com.jabook.app.core.domain.model.Chapter? {
        return chapterHelper.getChapterByNumber(audiobookId, chapterNumber)
    }


    override suspend fun updateChapterDownloadStatus(id: String, isDownloaded: Boolean, progress: Float) {
        chapterHelper.updateChapterDownloadStatus(id, isDownloaded, progress)
    }


    override suspend fun deleteChapter(id: String) {
        chapterHelper.deleteChapter(id)
    }

    override suspend fun deleteChaptersForAudiobook(audiobookId: String) {
        chapterHelper.deleteChaptersForAudiobook(audiobookId)
    }

    override fun getBookmarksByAudiobookId(audiobookId: String): Flow<List<com.jabook.app.core.domain.model.Bookmark>> {
        return bookmarkHelper.getBookmarksByAudiobookId(audiobookId)
    }

    override suspend fun getBookmarkById(id: String): com.jabook.app.core.domain.model.Bookmark? {
        return bookmarkHelper.getBookmarkById(id)
    }

    override fun getAllBookmarks(): Flow<List<com.jabook.app.core.domain.model.Bookmark>> {
        return bookmarkHelper.getAllBookmarks()
    }


    override suspend fun deleteBookmark(id: String) {
        bookmarkHelper.deleteBookmark(id)
    }

    override suspend fun deleteBookmarksForAudiobook(audiobookId: String) {
        bookmarkHelper.deleteBookmarksForAudiobook(audiobookId)
    }

    /** Convert domain DownloadStatus to entity DownloadStatus. */
    private fun DomainDownloadStatus.toEntityStatus(): EntityDownloadStatus {
        return when (this) {
            DomainDownloadStatus.NOT_DOWNLOADED -> EntityDownloadStatus.NOT_DOWNLOADED
            DomainDownloadStatus.QUEUED -> EntityDownloadStatus.QUEUED
            DomainDownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
            DomainDownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
            DomainDownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
            DomainDownloadStatus.FAILED -> EntityDownloadStatus.FAILED
            DomainDownloadStatus.CANCELLED -> EntityDownloadStatus.CANCELLED
        }
    }
}
