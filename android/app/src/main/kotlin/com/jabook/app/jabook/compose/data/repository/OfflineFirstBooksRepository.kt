// Copyright 2026 Jabook Contributors
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

import androidx.sqlite.db.SimpleSQLiteQuery
import com.jabook.app.jabook.audio.CompletionStatusHelper
import com.jabook.app.jabook.audio.processors.SpeedMemoryHierarchy
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.QueryResultSizeGuardPolicy
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.search.TransliterationSearchPolicy
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.model.toBook
import com.jabook.app.jabook.compose.domain.model.toBooks
import com.jabook.app.jabook.compose.domain.model.toChapters
import com.jabook.app.jabook.compose.domain.model.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
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
public class OfflineFirstBooksRepository
    @Inject
    constructor(
        private val booksDao: BooksDao,
        private val chaptersDao: com.jabook.app.jabook.compose.data.local.dao.ChaptersDao,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val playerPersistenceManager: com.jabook.app.jabook.audio.PlayerPersistenceManager,
        private val localBookScanner: com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner,
        private val loggerFactory: LoggerFactory,
    ) : BooksRepository {
        private val logger = loggerFactory.get("OfflineFirstBooksRepository")
        private val localeCollator =
            java.text.Collator.getInstance(java.util.Locale.getDefault()).apply {
                strength = java.text.Collator.PRIMARY
            }

        // Chapter durations per book don't change between saves — cache them so the
        // 5-second playback-position save doesn't re-read every chapter on the hot path.
        // Access-order LRU, bounded, thread-safe (repository is a singleton).
        private val chapterDurationsCache: MutableMap<String, List<Long>> =
            java.util.Collections.synchronizedMap(
                object : java.util.LinkedHashMap<String, List<Long>>(16, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Long>>): Boolean =
                        size > MAX_CACHED_BOOK_DURATIONS
                },
            )

        public companion object {
            private const val MAX_CACHED_BOOK_DURATIONS = 32
        }

        override fun getScanProgress(): Flow<com.jabook.app.jabook.compose.data.model.ScanProgress> = localBookScanner.scanProgress

        override fun getAllBooks(sortOrder: BookSortOrder): Flow<List<Book>> =
            booksDao.getAllBooksFlow().map { entities ->
                warnOnLargeResult(path = "getAllBooksFlow", rowCount = entities.size)
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
                                            com.jabook.app.jabook.audio.CompletionStatusHelper
                                                .calculateCompletionPercentage(
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
                        books.sortedWith(compareBy(localeCollator) { it.title })
                    }
                    BookSortOrder.TITLE_DESC -> {
                        books.sortedWith(compareByDescending(localeCollator) { it.title })
                    }
                    BookSortOrder.AUTHOR_ASC -> {
                        books.sortedWith(compareBy(localeCollator) { it.author })
                    }
                    BookSortOrder.AUTHOR_DESC -> {
                        books.sortedWith(compareByDescending(localeCollator) { it.author })
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
                        if (book.completionStatus ==
                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                        ) {
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
                        if (book.completionStatus ==
                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                        ) {
                            book.completedTimestamp
                        } else {
                            0L
                        }
                    },
                ).map { it.book }

        override fun getBook(bookId: String): Flow<Book?> =
            booksDao
                .getBookFlow(bookId)
                .distinctUntilChanged()
                .map { it?.toBook() }

        override fun getChapters(bookId: String): Flow<List<Chapter>> =
            booksDao
                .getChaptersForBookFlow(bookId)
                .distinctUntilChanged()
                .map {
                    warnOnLargeResult(path = "getChaptersForBookFlow", rowCount = it.size)
                    it.toChapters()
                }

        override fun searchBooks(query: String): Flow<List<Book>> {
            val variants = TransliterationSearchPolicy.buildVariants(query)
            val primary = variants.firstOrNull().orEmpty()
            val fallback = variants.getOrNull(1).orEmpty()
            val ftsMatchQuery = TransliterationSearchPolicy.buildFtsMatchQuery(variants)

            // Fall back to LIKE queries if FTS is not available or query is blank
            if (ftsMatchQuery.isBlank()) {
                return booksDao
                    .searchBooksFlowWithFallback(
                        query = primary,
                        fallbackQuery = fallback,
                    ).map {
                        warnOnLargeResult(path = "searchBooksFlowWithFallback", rowCount = it.size)
                        it.toBooks()
                    }
            }

            // Try FTS query; fall back to LIKE if FTS table doesn't exist.
            // Room flows are cold, so FTS errors surface only when collected.
            return booksDao
                .searchBooksByFtsFlow(
                    SimpleSQLiteQuery(
                        """
                        SELECT b.*
                        FROM books b
                        JOIN books_fts f ON b.rowid = f.rowid
                        WHERE books_fts MATCH ?
                        ORDER BY bm25(books_fts, 10.0, 5.0, 1.0) ASC
                        """.trimIndent(),
                        arrayOf(ftsMatchQuery),
                    ),
                ).map {
                    warnOnLargeResult(path = "searchBooksByFtsFlow", rowCount = it.size)
                    it.toBooks()
                }.catch { e ->
                    if (e is CancellationException) throw e
                    logger.w({ "FTS search failed, falling back to LIKE query" }, e)
                    emitAll(
                        booksDao
                            .searchBooksFlowWithFallback(
                                query = primary,
                                fallbackQuery = fallback,
                            ).map {
                                warnOnLargeResult(path = "searchBooksFlowWithFallback", rowCount = it.size)
                                it.toBooks()
                            },
                    )
                }
        }

        private fun warnOnLargeResult(
            path: String,
            rowCount: Int,
        ) {
            if (QueryResultSizeGuardPolicy.shouldWarn(rowCount)) {
                logger.w {
                    "Large query result detected: path=$path rowCount=$rowCount threshold=${QueryResultSizeGuardPolicy.WARN_THRESHOLD_ROWS}"
                }
            }
        }

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
                // Chapters (and their durations) may have changed — drop stale cache entries
                batch.forEach { chapterDurationsCache.remove(it.first.id) }
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
            try {
                // Improved progress calculation considering all tracks (inspired by Easybook)
                val book = booksDao.getBookById(bookId)

                val progress =
                    if (book != null && book.totalDuration > 0) {
                        // Track-duration-aware calculation: load chapter durations from cache
                        // (they don't change between saves) to avoid reading ALL chapters
                        // on the every-5-seconds hot path.
                        val trackDurations =
                            chapterDurationsCache.getOrPut(bookId) {
                                chaptersDao
                                    .getChaptersByBookId(bookId)
                                    .sortedBy { it.chapterIndex }
                                    .map { it.duration }
                            }
                        if (trackDurations.isNotEmpty()) {
                            CompletionStatusHelper
                                .calculateCompletionPercentageWithTracks(
                                    currentTrackIndex = chapterIndex,
                                    currentPositionMs = position,
                                    trackDurations = trackDurations,
                                ).toFloat()
                        } else {
                            // Fallback to simple calculation if chapters are not available
                            (position.toFloat() / book.totalDuration.toFloat()).coerceIn(0f, 1f)
                        }
                    } else {
                        0f
                    }

                val chapter = chaptersDao.getChapterByIndex(bookId, chapterIndex)
                booksDao.updatePlaybackPositionAtomic(
                    bookId = bookId,
                    position = position,
                    progress = progress,
                    chapterIndex = chapterIndex,
                    timestamp = System.currentTimeMillis(),
                    chapterId = chapter?.id,
                    chapterPosition = position.coerceIn(0L, chapter?.duration ?: position),
                    chapterCompleted = chapter != null && position >= chapter.duration,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e({ "Failed to update playback position for book=$bookId" }, e)
            }
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
            chapterDurationsCache.remove(bookId)
            booksDao.deleteById(bookId)
        }

        override fun getFavoriteBooks(): Flow<List<Book>> = booksDao.getFavoriteBooksFlow().map { it.toBooks() }

        override fun getInProgressBooks(): Flow<List<Book>> = booksDao.getInProgressBooksFlow().map { it.toBooks() }

        override fun getRecentlyPlayedBooks(limit: Int): Flow<List<Book>> = booksDao.getRecentlyPlayedBooksFlow(limit).map { it.toBooks() }

        override suspend fun setFavorite(
            bookId: String,
            isFavorite: Boolean,
        ) {
            booksDao.updateFavoriteStatus(bookId, isFavorite)
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
            val allChapters = chaptersDao.getAllChapters()
            val grouped = allChapters.groupBy { it.bookId }

            for ((_, chapters) in grouped) {
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

        override suspend fun resolvePreferredPlaybackSpeed(
            bookId: String,
            globalSpeed: Float,
        ): Float {
            val perBookSpeed = booksDao.getPreferredSpeed(bookId)
            val perAuthorSpeed = booksDao.getAveragePreferredSpeedForAuthorOfBook(bookId)?.toFloat()
            return SpeedMemoryHierarchy.resolveSpeed(
                perBookSpeed = perBookSpeed,
                perAuthorSpeed = perAuthorSpeed,
                globalSpeed = globalSpeed,
            )
        }

        override suspend fun updatePreferredPlaybackSpeed(
            bookId: String,
            speed: Float,
        ) {
            if (!speed.isFinite() || speed <= 0f) {
                logger.w { "Ignoring invalid preferred playback speed=$speed for bookId=$bookId" }
                return
            }
            val previous = booksDao.getPreferredSpeed(bookId)
            if (
                !SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(
                    previousSpeed = previous,
                    newSpeed = speed,
                )
            ) {
                return
            }
            booksDao.updatePreferredSpeed(bookId, speed)
        }

        override fun getBookBySourceUrlFlow(sourceUrl: String): Flow<Book?> =
            booksDao.getBookBySourceUrlFlow(sourceUrl).map { it?.toBook() }

        override suspend fun getChapterLufsValue(
            bookId: String,
            chapterIndex: Int,
        ): Double? = chaptersDao.getChapterByIndex(bookId, chapterIndex)?.lufsValue

        override suspend fun getBookBySourceUrl(sourceUrl: String): Book? = booksDao.getBookBySourceUrl(sourceUrl)?.toBook()
    }
