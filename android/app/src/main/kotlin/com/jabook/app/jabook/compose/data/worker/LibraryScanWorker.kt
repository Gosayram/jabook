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

package com.jabook.app.jabook.compose.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import com.jabook.app.jabook.compose.data.model.ScanProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.jabook.app.jabook.compose.domain.model.Result as DomainResult

/**
 * WorkManager worker for scanning local audiobooks in background.
 */
@HiltWorker
class LibraryScanWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val bookScanner: LocalBookScanner,
        private val booksDao: BooksDao,
        private val chaptersDao: ChaptersDao,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): ListenableWorker.Result =
            withContext(Dispatchers.IO) {
                try {
                    setProgress(workDataOf("status" to applicationContext.getString(R.string.scan_status_starting)))

                    // Watchdog: Cancel scan if no progress for 3 minutes
                    val scannerJob =
                        async {
                            bookScanner.scanAudiobooks()
                        }

                    val watchdogJob =
                        launch {
                            var lastProgress = bookScanner.scanProgress.value
                            var lastUpdate = System.currentTimeMillis()

                            while (true) {
                                kotlinx.coroutines.delay(5000) // Check every 5s

                                val currentProgress = bookScanner.scanProgress.value
                                if (currentProgress != lastProgress) {
                                    lastProgress = currentProgress
                                    lastUpdate = System.currentTimeMillis()

                                    // Update WorkManager progress for system visibility
                                    val status =
                                        when (currentProgress) {
                                            is ScanProgress.Discovery ->
                                                applicationContext.getString(
                                                    R.string.scan_status_discovery,
                                                    currentProgress.fileCount,
                                                )
                                            is ScanProgress.Parsing ->
                                                applicationContext.getString(
                                                    R.string.scan_status_parsing,
                                                    currentProgress.currentBook,
                                                    currentProgress.progress,
                                                    currentProgress.total,
                                                )
                                            else -> applicationContext.getString(R.string.scanningLibrary)
                                        }
                                    setProgress(workDataOf("status" to status))
                                } else {
                                    // No progress change
                                    if (System.currentTimeMillis() - lastUpdate > 3 * 60 * 1000) {
                                        // 3 minutes timeout!
                                        if (currentProgress is ScanProgress.Parsing ||
                                            currentProgress is ScanProgress.Discovery
                                        ) {
                                            android.util.Log.e("LibraryScanWorker", "Watchdog triggered: Scan stuck for 3 minutes")
                                            scannerJob.cancel(
                                                kotlinx.coroutines.CancellationException(
                                                    "Scan timeout: watchdog detected hang",
                                                ),
                                            )
                                            break
                                        }
                                    }
                                }
                            }
                        }

                    val result = scannerJob.await()
                    watchdogJob.cancel()

                    when (result) {
                        is DomainResult.Success -> {
                            val books = result.data
                            setProgress(workDataOf("status" to applicationContext.getString(R.string.scan_status_preparing, books.size)))

                            // Prepare all entities and cover art jobs
                            val bookEntities = mutableListOf<BookEntity>()
                            val chapterEntities = mutableListOf<ChapterEntity>()
                            val coverArtJobs = mutableListOf<kotlinx.coroutines.Deferred<Pair<String, String?>>>()

                            // Phase 1: Prepare entities and start cover art saving in parallel
                            for (book in books) {
                                val bookId = "local-${book.directory.hashCode()}"

                                // Start cover art saving asynchronously (non-blocking)
                                val coverArtJob =
                                    async(Dispatchers.IO) {
                                        bookId to saveCoverArt(bookId, book.coverArt)
                                    }
                                coverArtJobs.add(coverArtJob)

                                // Prepare book entity (cover URL will be set later)
                                bookEntities.add(
                                    BookEntity(
                                        id = bookId,
                                        title = book.title,
                                        author = book.author,
                                        coverUrl = null, // Will update after cover art is saved
                                        description = null,
                                        totalDuration = book.totalDuration,
                                        localPath = book.directory,
                                        addedDate = System.currentTimeMillis(),
                                        downloadStatus = "DOWNLOADED",
                                        isDownloaded = true,
                                    ),
                                )

                                // Prepare chapters
                                chapterEntities.addAll(
                                    book.chapters.map { chapter ->
                                        ChapterEntity(
                                            id = "$bookId-chapter-${chapter.index}",
                                            bookId = bookId,
                                            title = chapter.title,
                                            chapterIndex = chapter.index,
                                            fileIndex = chapter.index,
                                            duration = chapter.duration,
                                            fileUrl = chapter.filePath,
                                            isDownloaded = true,
                                        )
                                    },
                                )
                            }

                            // Phase 2: Batch insert all books and chapters
                            setProgress(workDataOf("status" to applicationContext.getString(R.string.scan_status_saving)))
                            booksDao.insertBooksWithChapters(bookEntities, chapterEntities)

                            // Phase 3: Wait for cover art and update book records
                            setProgress(workDataOf("status" to applicationContext.getString(R.string.scan_status_processing_covers)))
                            val coverResults = coverArtJobs.awaitAll()

                            // Update books with cover URLs
                            for ((bookId, coverUrl) in coverResults) {
                                if (coverUrl != null) {
                                    val bookEntity = bookEntities.find { it.id == bookId }
                                    bookEntity?.let {
                                        booksDao.updateBook(it.copy(coverUrl = coverUrl))
                                    }
                                }
                            }

                            setProgress(
                                workDataOf(
                                    "status" to applicationContext.getString(R.string.scan_status_completed_saving, books.size),
                                ),
                            )

                            ListenableWorker.Result.success(
                                workDataOf("booksFound" to books.size),
                            )
                        }
                        is DomainResult.Error -> {
                            ListenableWorker.Result.failure(
                                workDataOf("error" to (result.message ?: applicationContext.getString(R.string.libraryUnknownError))),
                            )
                        }
                        is DomainResult.Loading -> {
                            ListenableWorker.Result.retry()
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        android.util.Log.w("LibraryScanWorker", "Scan cancelled (Watchdog or User)")
                        // Return failure so it doesn't retry automatically if cancelled by user/watchdog
                        return@withContext ListenableWorker.Result.failure()
                    }
                    android.util.Log.e("LibraryScanWorker", "Scan failed", e)
                    ListenableWorker.Result.failure(
                        workDataOf("error" to (e.message ?: applicationContext.getString(R.string.libraryUnknownError))),
                    )
                }
            }

        private suspend fun saveCoverArt(
            bookId: String,
            coverArt: ByteArray?,
        ): String? {
            if (coverArt == null) return null

            return withContext(Dispatchers.IO) {
                try {
                    val coversDir = File(applicationContext.filesDir, "covers")
                    coversDir.mkdirs()

                    val coverFile = File(coversDir, "$bookId.jpg")
                    coverFile.writeBytes(coverArt)

                    coverFile.absolutePath
                } catch (e: Exception) {
                    android.util.Log.e("LibraryScanWorker", "Failed to save cover art", e)
                    null
                }
            }
        }
    }
