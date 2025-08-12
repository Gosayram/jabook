package com.jabook.app.core.data.repository

import com.jabook.app.core.data.mapper.AudiobookMapper.toBookmarkDomainList
import com.jabook.app.core.data.mapper.AudiobookMapper.toDomain
import com.jabook.app.core.database.dao.BookmarkDao
import com.jabook.app.core.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookmarkHelper
@Inject
constructor(private val bookmarkDao: BookmarkDao) {

    fun getBookmarksByAudiobookId(audiobookId: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByAudiobookId(audiobookId).map { it.toBookmarkDomainList() }
    }

    suspend fun getBookmarkById(id: String): Bookmark? {
        return bookmarkDao.getBookmarkById(id)?.toDomain()
    }

    fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { it.toBookmarkDomainList() }
    }

    suspend fun upsertBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(bookmark.toEntity())
    }

    suspend fun deleteBookmark(id: String) {
        bookmarkDao.deleteBookmarkById(id)
    }

    suspend fun deleteBookmarksForAudiobook(audiobookId: String) {
        bookmarkDao.deleteBookmarksForAudiobook(audiobookId)
    }
}
