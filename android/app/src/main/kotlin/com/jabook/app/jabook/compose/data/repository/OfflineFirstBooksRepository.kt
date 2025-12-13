// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.model.toBook
import com.jabook.app.jabook.compose.domain.model.toBooks
import com.jabook.app.jabook.compose.domain.model.toChapters
import com.jabook.app.jabook.compose.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first implementation of BooksRepository.
 *
 * This implementation:
 * - Uses Room as the single source of truth
 * - Provides reactive Flow-based APIs
 * - Handles data mapping between entities and domain models
 *
 * @param booksDao Room DAO for database access
 */
@Singleton
class OfflineFirstBooksRepository
    @Inject
    constructor(
        private val booksDao: BooksDao,
    ) : BooksRepository {
        override fun getAllBooks(): Flow<List<Book>> = booksDao.getAllBooksFlow().map { it.toBooks() }

        override fun getBook(bookId: String): Flow<Book?> = booksDao.getBookFlow(bookId).map { it?.toBook() }

        override fun getChapters(bookId: String): Flow<List<Chapter>> = booksDao.getChaptersForBookFlow(bookId).map { it.toChapters() }

        override fun searchBooks(query: String): Flow<List<Book>> = booksDao.searchBooksFlow(query).map { it.toBooks() }

        override suspend fun addBook(book: Book) {
            booksDao.insertBook(book.toEntity())
        }

        override suspend fun updateBook(book: Book) {
            booksDao.updateBook(book.toEntity())
        }

        override suspend fun updatePlaybackPosition(
            bookId: String,
            position: Long,
            chapterIndex: Int,
        ) {
            // Calculate progress based on total duration
            val book = booksDao.getBookById(bookId)
            val progress =
                if (book != null && book.totalDuration > 0) {
                    (position.toFloat() / book.totalDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

            booksDao.updatePlaybackProgress(
                bookId = bookId,
                position = position,
                progress = progress,
                chapterIndex = chapterIndex,
                timestamp = System.currentTimeMillis(),
            )
        }

        override suspend fun updateDownloadProgress(
            bookId: String,
            progress: Float,
            isComplete: Boolean,
        ) {
            val status =
                when {
                    isComplete -> "DOWNLOADED"
                    progress > 0 -> "DOWNLOADING"
                    else -> "NOT_DOWNLOADED"
                }
            booksDao.updateDownloadStatus(
                bookId = bookId,
                status = status,
                progress = progress,
                isDownloaded = isComplete,
            )
        }

        override suspend fun deleteBook(bookId: String) {
            booksDao.deleteById(bookId)
        }

        override suspend fun refresh() {
            // No-op for offline-first implementation
            // In a network-enabled version, this would fetch from remote
        }
    }
