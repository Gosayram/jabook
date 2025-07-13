package com.jabook.app.core.data.repository

import com.jabook.app.core.data.mapper.AudiobookMapper.toBookmarkDomainList
import com.jabook.app.core.data.mapper.AudiobookMapper.toChapterDomainList
import com.jabook.app.core.data.mapper.AudiobookMapper.toDomain
import com.jabook.app.core.data.mapper.AudiobookMapper.toDomainList
import com.jabook.app.core.data.mapper.AudiobookMapper.toEntity
import com.jabook.app.core.database.dao.AudiobookDao
import com.jabook.app.core.database.dao.BookmarkDao
import com.jabook.app.core.database.dao.ChapterDao
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Bookmark
import com.jabook.app.core.domain.model.Chapter
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
constructor(private val audiobookDao: AudiobookDao, private val chapterDao: ChapterDao, private val bookmarkDao: BookmarkDao) :
    AudiobookRepository {
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

    // Chapter operations

    override fun getChaptersByAudiobookId(audiobookId: String): Flow<List<Chapter>> {
        return chapterDao.getChaptersByAudiobookId(audiobookId).map { it.toChapterDomainList() }
    }

    override suspend fun getChapterById(id: String): Chapter? {
        return chapterDao.getChapterById(id)?.toDomain()
    }

    override suspend fun getChapterByNumber(audiobookId: String, chapterNumber: Int): Chapter? {
        return chapterDao.getChapterByNumber(audiobookId, chapterNumber)?.toDomain()
    }

    override suspend fun upsertChapter(chapter: Chapter) {
        chapterDao.insertChapter(chapter.toEntity())
    }

    override suspend fun upsertChapters(chapters: List<Chapter>) {
        chapterDao.insertChapters(chapters.map { it.toEntity() })
    }

    override suspend fun updateChapterDownloadStatus(id: String, isDownloaded: Boolean, progress: Float) {
        chapterDao.updateDownloadStatus(id, isDownloaded, progress)
    }

    override suspend fun deleteChapter(id: String) {
        chapterDao.getChapterById(id)?.let { chapter -> chapterDao.deleteChapter(chapter) }
    }

    override suspend fun deleteChaptersForAudiobook(audiobookId: String) {
        chapterDao.deleteChaptersForAudiobook(audiobookId)
    }

    // Bookmark operations

    override fun getBookmarksByAudiobookId(audiobookId: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByAudiobookId(audiobookId).map { it.toBookmarkDomainList() }
    }

    override suspend fun getBookmarkById(id: String): Bookmark? {
        return bookmarkDao.getBookmarkById(id)?.toDomain()
    }

    override fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { it.toBookmarkDomainList() }
    }

    override suspend fun upsertBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(id: String) {
        bookmarkDao.deleteBookmarkById(id)
    }

    override suspend fun deleteBookmarksForAudiobook(audiobookId: String) {
        bookmarkDao.deleteBookmarksForAudiobook(audiobookId)
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
