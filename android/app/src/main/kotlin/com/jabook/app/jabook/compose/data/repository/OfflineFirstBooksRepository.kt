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
import com.jabook.app.jabook.compose.data.local.toDomainModel
import com.jabook.app.jabook.compose.data.local.toEntity
import com.jabook.app.jabook.compose.data.model.Book
import com.jabook.app.jabook.compose.data.model.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        override fun getAllBooks(): Flow<List<Book>> =
            booksDao.getAllBooksFlow().map { bookEntities ->
                // For each book, get its chapters and combine
                bookEntities.map { bookEntity ->
                    // Get chapters for this book (this will be executed for each book)
                    // In production, consider optimizing with a single query
                    val chapters = booksDao.getChaptersForBookFlow(bookEntity.id)

                    // For now, return book with empty chapters
                    // In a real implementation, you'd combine these flows
                    bookEntity.toDomainModel(emptyList())
                }
            }

        override fun getBook(bookId: String): Flow<Book?> =
            combine(
                booksDao.getBookFlow(bookId),
                booksDao.getChaptersForBookFlow(bookId),
            ) { bookEntity, chapterEntities ->
                bookEntity?.toDomainModel(chapterEntities)
            }

        override fun getChapters(bookId: String): Flow<List<Chapter>> =
            booksDao.getChaptersForBookFlow(bookId).map { entities ->
                entities.map { it.toDomainModel() }
            }

        override fun searchBooks(query: String): Flow<List<Book>> =
            booksDao.searchBooks(query).map { bookEntities ->
                bookEntities.map { it.toDomainModel(emptyList()) }
            }

        override suspend fun addBook(book: Book) {
            // Insert book entity
            booksDao.insertBook(book.toEntity())

            // Insert chapter entities
            val chapterEntities = book.chapters.map { it.toEntity() }
            booksDao.insertChapters(chapterEntities)
        }

        override suspend fun updateBook(book: Book) {
            booksDao.updateBook(book.toEntity())
        }

        override suspend fun updatePlaybackPosition(
            bookId: String,
            position: Long,
            chapterIndex: Int,
        ) {
            booksDao.updatePlaybackPosition(
                bookId = bookId,
                position = position,
                chapterIndex = chapterIndex,
            )
        }

        override suspend fun updateDownloadProgress(
            bookId: String,
            progress: Float,
            isComplete: Boolean,
        ) {
            booksDao.updateDownloadProgress(
                bookId = bookId,
                progress = progress,
                isComplete = isComplete,
            )
        }

        override suspend fun deleteBook(bookId: String) {
            booksDao.deleteBook(bookId)
        }

        override suspend fun refresh() {
            // No-op for offline-first implementation
            // In a network-enabled version, this would fetch from remote
        }
    }
