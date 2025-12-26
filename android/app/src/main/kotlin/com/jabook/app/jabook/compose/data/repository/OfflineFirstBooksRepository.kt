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
import com.jabook.app.jabook.compose.data.model.BookSortOrder
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
        private val chaptersDao: com.jabook.app.jabook.compose.data.local.dao.ChaptersDao,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val playerPersistenceManager: com.jabook.app.jabook.audio.PlayerPersistenceManager,
        private val localBookScanner: com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner,
    ) : BooksRepository {
        override fun getScanProgress(): Flow<com.jabook.app.jabook.compose.data.model.ScanProgress> = localBookScanner.scanProgress

        override fun getAllBooks(sortOrder: BookSortOrder): Flow<List<Book>> =
            booksDao.getAllBooksFlow().map { entities ->
                val books = entities.toBooks()
                when (sortOrder) {
                    BookSortOrder.BY_ACTIVITY -> {
                        // Gather player state for all books
                        // Since this is inside a suspend map, we can call suspend functions
                        val booksWithStatus =
                            books.map { book ->
                                val state = playerPersistenceManager.getPlayerState(book.id)
                                val status =
                                    if (state != null) {
                                        val percentage =
                                            com.jabook.app.jabook.audio.CompletionStatusHelper.calculateCompletionPercentage(
                                                state.positionMs,
                                                state.durationMs,
                                            )
                                        com.jabook.app.jabook.audio.CompletionStatusHelper
                                            .getCompletionStatus(percentage)
                                    } else {
                                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                                    }

                                BookWithStatus(
                                    book = book,
                                    completionStatus = status,
                                    lastPlayedTimestamp = state?.lastPlayedTimestamp ?: 0L,
                                    completedTimestamp = state?.completedTimestamp ?: 0L,
                                )
                            }
                        booksWithStatus.sortByActivity()
                    }
                    BookSortOrder.TITLE_ASC -> {
                        // Use locale-aware collator for proper alphabetical sorting
                        val collator =
                            java.text.Collator.getInstance(java.util.Locale.getDefault()).apply {
                                strength = java.text.Collator.PRIMARY // Ignore case
                            }
                        books.sortedWith(compareBy(collator) { it.title })
                    }
                    BookSortOrder.TITLE_DESC -> {
                        val collator =
                            java.text.Collator.getInstance(java.util.Locale.getDefault()).apply {
                                strength = java.text.Collator.PRIMARY
                            }
                        books.sortedWith(compareByDescending(collator) { it.title })
                    }
                    BookSortOrder.AUTHOR_ASC -> {
                        val collator =
                            java.text.Collator.getInstance(java.util.Locale.getDefault()).apply {
                                strength = java.text.Collator.PRIMARY
                            }
                        books.sortedWith(compareBy(collator) { it.author })
                    }
                    BookSortOrder.AUTHOR_DESC -> {
                        val collator =
                            java.text.Collator.getInstance(java.util.Locale.getDefault()).apply {
                                strength = java.text.Collator.PRIMARY
                            }
                        books.sortedWith(compareByDescending(collator) { it.author })
                    }
                    BookSortOrder.RECENTLY_ADDED -> books.sortedByDescending { it.addedDate }
                    BookSortOrder.OLDEST_FIRST -> books.sortedBy { it.addedDate }
                }
            }

        private data class BookWithStatus(
            val book: Book,
            val completionStatus: Int,
            val lastPlayedTimestamp: Long,
            val completedTimestamp: Long,
        )

        private fun List<BookWithStatus>.sortByActivity(): List<Book> =
            this
                .sortedWith(
                    compareBy<BookWithStatus> { book ->
                        // Primary: Completion status priority
                        when (book.completionStatus) {
                            // In Progress - first
                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED -> 0
                            // Not Started - middle
                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED -> 1
                            // Completed - last
                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED -> 2
                            else -> 3
                        }
                    }.thenByDescending { book ->
                        // Secondary: For In Progress - most recent first
                        if (book.completionStatus ==
                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                        ) {
                            book.lastPlayedTimestamp
                        } else {
                            0L
                        }
                    }.thenBy { book ->
                        // Tertiary: For Not Started - alphabetical by title
                        if (book.completionStatus == androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED) {
                            book.book.title.lowercase()
                        } else {
                            ""
                        }
                    }.thenBy { book ->
                        // Quaternary: For Completed - most recent completed LAST (oldest completed first in the group?)
                        // Request said: "последние завершённые ниже" (completed recently -> bottom)
                        // If we want "recently completed" at the very bottom, we sort by completedTimestamp ASCENDING.
                        // If A completed today (large TS) and B completed yesterday (small TS).
                        // We want A below B. So Ascending TS.
                        if (book.completionStatus == androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED) {
                            book.completedTimestamp
                        } else {
                            0L
                        }
                    },
                ).map { it.book }

        override fun getBook(bookId: String): Flow<Book?> = booksDao.getBookFlow(bookId).map { it?.toBook() }

        override fun getChapters(bookId: String): Flow<List<Chapter>> = booksDao.getChaptersForBookFlow(bookId).map { it.toChapters() }

        override fun searchBooks(query: String): Flow<List<Book>> = booksDao.searchBooksFlow(query).map { it.toBooks() }

        override suspend fun addBook(book: Book) {
            booksDao.insertBook(book.toEntity())
        }

        override suspend fun addBooks(booksWithChapters: List<Pair<Book, List<Chapter>>>) {
            // Process in batches to avoid large transactions that can lock the UI/DB
            // Batch size of 50 is a good balance for SQLite
            val batchSize = 50

            booksWithChapters.chunked(batchSize).forEach { batch ->
                val bookEntities = batch.map { it.first.toEntity() }
                val chapterEntities =
                    batch.flatMap { (_, chapters) ->
                        chapters.map { it.toEntity() }
                    }

                // Use Upsert instead of Insert(REPLACE) to avoid unnecessary deletions/re-insertions
                // which is safer for foreign keys and generally more performant
                booksDao.upsertBooksWithChapters(bookEntities, chapterEntities)
            }
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

        override suspend fun updateBookSettings(
            bookId: String,
            rewindDuration: Int?,
            forwardDuration: Int?,
        ) {
            booksDao.updateBookSettings(bookId, rewindDuration, forwardDuration)
        }

        override suspend fun resetAllBookSettings() {
            booksDao.resetAllBookSettings()
        }

        override suspend fun refresh() {
            // No-op for offline-first implementation
            // In a network-enabled version, this would fetch from remote
        }

        override fun getScanPaths(): Flow<List<String>> =
            scanPathDao.getAllPaths().map { entities ->
                entities.map { it.path }
            }

        override suspend fun addScanPath(path: String) {
            scanPathDao.insertPath(
                com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity(
                    path = path,
                ),
            )
        }

        override suspend fun removeScanPath(path: String) {
            scanPathDao.deletePathByString(path)
        }

        override suspend fun normalizeAllChapters() {
            val books = booksDao.getAllBooks()

            for (book in books) {
                val chapters = chaptersDao.getChaptersByBookId(book.id)
                if (chapters.isEmpty()) continue

                val titles = chapters.map { it.title }
                val normalizedTitles =
                    com.jabook.app.jabook.compose.domain.util.ChapterNormalizer
                        .normalizeTitles(titles)

                // Only update if changes found
                var changed = false
                val updatedChapters =
                    chapters.mapIndexed { index, chapter ->
                        if (chapter.title != normalizedTitles[index]) {
                            changed = true
                            chapter.copy(title = normalizedTitles[index])
                        } else {
                            chapter
                        }
                    }

                if (changed) {
                    chaptersDao.insertAll(updatedChapters)
                }
            }
        }

        override suspend fun updateChapterOrder(
            bookId: String,
            newOrderedIds: List<String>,
        ) {
            val chapters = chaptersDao.getChaptersByBookId(bookId)
            if (chapters.isEmpty()) return

            val chapterMap = chapters.associateBy { it.id }
            val updatedChapters = mutableListOf<com.jabook.app.jabook.compose.data.local.entity.ChapterEntity>()

            newOrderedIds.forEachIndexed { index, id ->
                val chapter = chapterMap[id]
                if (chapter != null && chapter.chapterIndex != index) {
                    updatedChapters.add(chapter.copy(chapterIndex = index))
                }
            }

            if (updatedChapters.isNotEmpty()) {
                chaptersDao.insertAll(updatedChapters)
            }
        }

        override fun getBookBySourceUrlFlow(sourceUrl: String): Flow<Book?> =
            booksDao.getBookBySourceUrlFlow(sourceUrl).map { it?.toBook() }

        override suspend fun getBookBySourceUrl(sourceUrl: String): Book? = booksDao.getBookBySourceUrl(sourceUrl)?.toBook()
    }
