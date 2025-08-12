package com.jabook.app.core.data.repository

import com.jabook.app.core.data.mapper.AudiobookMapper.toChapterDomainList
import com.jabook.app.core.data.mapper.AudiobookMapper.toDomain
import com.jabook.app.core.database.dao.ChapterDao
import com.jabook.app.core.domain.model.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChapterHelper
@Inject
constructor(private val chapterDao: ChapterDao) {

    fun getChaptersByAudiobookId(audiobookId: String): Flow<List<Chapter>> {
        return chapterDao.getChaptersByAudiobookId(audiobookId).map { it.toChapterDomainList() }
    }

    suspend fun getChapterById(id: String): Chapter? {
        return chapterDao.getChapterById(id)?.toDomain()
    }

    suspend fun getChapterByNumber(audiobookId: String, chapterNumber: Int): Chapter? {
        return chapterDao.getChapterByNumber(audiobookId, chapterNumber)?.toDomain()
    }

    suspend fun upsertChapter(chapter: Chapter) {
        chapterDao.insertChapter(chapter.toEntity())
    }


    suspend fun upsertChapters(chapters: List<Chapter>) {
        chapterDao.insertChapters(chapters.map { it.toEntity() })
    }


    suspend fun updateChapterDownloadStatus(id: String, isDownloaded: Boolean, progress: Float) {
        chapterDao.updateDownloadStatus(id, isDownloaded, progress)
    }

    suspend fun deleteChapter(id: String) {
        chapterDao.getChapterById(id)?.let { chapter -> chapterDao.deleteChapter(chapter) }
    }

    suspend fun deleteChaptersForAudiobook(audiobookId: String) {
        chapterDao.deleteChaptersForAudiobook(audiobookId)
    }
}
