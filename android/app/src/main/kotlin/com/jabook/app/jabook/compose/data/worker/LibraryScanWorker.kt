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
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import com.jabook.app.jabook.compose.data.model.ScanProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.jabook.app.jabook.compose.domain.model.Result as DomainResult

/**
 * WorkManager worker for scanning local audiobooks in background.
 */
@HiltWorker
public class LibraryScanWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val bookScanner: LocalBookScanner,
        private val booksDao: BooksDao,
        private val chaptersDao: ChaptersDao,
        private val loggerFactory: LoggerFactory,
    ) : CoroutineWorker(appContext, params) {
        private val logger = loggerFactory.get("LibraryScanWorker")
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
                                kotlinx.coroutines.delay(5000L) // Check every 5s

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
                                            logger.e { "Watchdog triggered: Scan stuck for 3 minutes" }
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

                            // CRITICAL FIX: Clean up books whose directories were deleted
                            // This ensures DB reflects actual filesystem state
                            logger.d { "Checking for deleted books..." }
                            val existingBooks = booksDao.getAllBookPaths()
                            var deletedCount = 0

                            for (book in existingBooks) {
                                if (book.localPath != null) {
                                    val bookDir = java.io.File(book.localPath)
                                    if (!bookDir.exists() || !bookDir.isDirectory) {
                                        logger.i {
                                            "Deleting book with non-existent path: ${book.localPath}"
                                        }
                                        booksDao.deleteById(book.id)
                                        deletedCount++
                                    }
                                }
                            }

                            if (deletedCount > 0) {
                                logger.i {
                                    "Cleaned up $deletedCount deleted books from database"
                                }
                            }

                            // Chunk processing to avoid UI hangs and memory spikes
                            // Increased from 20 to 50 for better performance
                            val batchSize = 50
                            val batches = books.chunked(batchSize)

                            var booksSaved = 0

                            batches.forEachIndexed { batchIndex, batch ->
                                if (isStopped) return@withContext ListenableWorker.Result.failure()

                                setProgress(
                                    workDataOf(
                                        "status" to
                                            "${applicationContext.getString(R.string.scan_status_saving)} " +
                                            "(${batchIndex + 1}/${batches.size})",
                                    ),
                                )

                                val bookEntities = mutableListOf<BookEntity>()
                                val chapterEntities = mutableListOf<ChapterEntity>()

                                val coversDir = File(applicationContext.filesDir, "covers")
                                if (!coversDir.exists()) coversDir.mkdirs()

                                for (book in batch) {
                                    // Extract cover from FIRST chapter only (fast!)
                                    // This is much faster than extracting from all files
                                    try {
                                        val bookId = "local-${book.directory.hashCode()}"
                                        val coverFile = File(coversDir, "$bookId.jpg")

                                        // Only extract if cover doesn't exist
                                        // Priority: Embedded covers from ID3 tags are most reliable
                                        if (!coverFile.exists() && book.chapters.isNotEmpty()) {
                                            val firstChapter = book.chapters.first()
                                            val audioFile = File(firstChapter.filePath)

                                            if (audioFile.exists()) {
                                                val retriever = android.media.MediaMetadataRetriever()
                                                try {
                                                    retriever.setDataSource(audioFile.absolutePath)
                                                    val coverData = retriever.embeddedPicture

                                                    // Validate cover data before saving
                                                    if (coverData != null && coverData.isNotEmpty()) {
                                                        // Minimum size check (at least 1KB to avoid corrupted/invalid images)
                                                        if (coverData.size >= 1024) {
                                                            coverFile.writeBytes(coverData)
                                                            logger.d {
                                                                "Extracted embedded cover from ID3 tags: ${book.title} (${coverData.size} bytes)"
                                                            }
                                                        } else {
                                                            logger.d {
                                                                "Skipped small/invalid cover data for ${book.title} (${coverData.size} bytes)"
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    logger.w(e) {
                                                        "Failed to extract embedded cover for ${book.title}"
                                                    }
                                                } finally {
                                                    retriever.release()
                                                }
                                            }
                                        }

                                        // Entity Creation
                                        bookEntities.add(
                                            BookEntity(
                                                id = bookId,
                                                title = book.title,
                                                author = book.author,
                                                coverUrl = null, // UI loads from folder or app dir via CoverUtils
                                                description = null,
                                                totalDuration = book.totalDuration,
                                                localPath = book.directory,
                                                addedDate = System.currentTimeMillis(),
                                                downloadStatus = "DOWNLOADED",
                                                isDownloaded = true,
                                            ),
                                        )

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
                                    } catch (e: Exception) {
                                        logger.e(e) { "Error processing book ${book.title}" }
                                    }
                                }

                                // 3. Batch Upsert (insert or update) - faster for re-scans
                                if (bookEntities.isNotEmpty()) {
                                    booksDao.upsertBooksWithChapters(bookEntities, chapterEntities)
                                    booksSaved += bookEntities.size

                                    setProgress(
                                        workDataOf(
                                            "status" to
                                                applicationContext.getString(
                                                    R.string.scan_status_completed_saving,
                                                    booksSaved,
                                                ),
                                        ),
                                    )
                                }
                            }

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
                        logger.w { "Scan cancelled (Watchdog or User)" }
                        // Return failure so it doesn't retry automatically if cancelled by user/watchdog
                        return@withContext ListenableWorker.Result.failure()
                    }
                    logger.e(e) { "Scan failed" }
                    ListenableWorker.Result.failure(
                        workDataOf("error" to (e.message ?: applicationContext.getString(R.string.libraryUnknownError))),
                    )
                }
            }

        // Cover art removed - UI loads from book folder (cover.jpg/cover.jpeg)
    }
