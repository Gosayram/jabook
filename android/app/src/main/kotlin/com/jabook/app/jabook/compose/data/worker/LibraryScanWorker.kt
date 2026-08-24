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
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.util.PerfTrace
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.data.storage.AtomicFileWriter
import com.jabook.app.jabook.crash.CrashDiagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
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

        public companion object {
            public const val WORK_NAME: String = "library_scan_work"
            public const val WORK_TAG: String = "library_scan"
            private const val NOTIFICATION_ID: Int = 3_105
            private const val NOTIFICATION_CHANNEL_ID: String = "library_scan_work"
            private const val MAX_SCAN_ATTEMPTS: Int = 3
        }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(
                android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    applicationContext.getString(R.string.scanningLibrary),
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val notification =
                androidx.core.app.NotificationCompat
                    .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle(applicationContext.getString(R.string.scanningLibrary))
                    .setOngoing(true)
                    .build()
            return ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }

        override suspend fun doWork(): ListenableWorker.Result =
            withContext(Dispatchers.IO) {
                val attempt = runAttemptCount + 1
                val stopReasonAtStart = runCatching { stopReason }.getOrDefault(-1)
                logger.i { "Library scan started attempt=$attempt stopReason=$stopReasonAtStart" }
                try {
                    try {
                        setForeground(getForegroundInfo())
                    } catch (e: Throwable) {
                        // Android 12+ FGS start may be denied (or an OEM throws another
                        // runtime/security variant); degrade to background rather than crash.
                        logger.w { "FGS start not allowed for library scan: ${e.message}" }
                    }
                    setProgress(workDataOf("status" to applicationContext.getString(R.string.scan_status_starting)))

                    // Watchdog: Cancel scan if no progress for 3 minutes
                    val scannerJob =
                        async {
                            PerfTrace.section(name = "LibraryScanWorker.scanAudiobooks") {
                                bookScanner.scanAudiobooks()
                            }
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
                            val existingBooks =
                                PerfTrace.section(name = "LibraryScanWorker.loadExistingBookPaths") {
                                    booksDao.getAllBookPaths()
                                }
                            var deletedCount = 0

                            PerfTrace.section(name = "LibraryScanWorker.cleanupDeletedBooks") {
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
                                                            AtomicFileWriter.writeWithLock(coverFile) { output ->
                                                                output.write(coverData)
                                                                coverData.size.toLong()
                                                            }
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
                                                    logger.e(
                                                        { "Failed to extract embedded cover for ${book.title}" },
                                                        e,
                                                    )
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
                                                // Embedded M4B chapters share one file path; fold the
                                                // in-file offset into the id so segments stay unique
                                                // while whole-file chapters keep their legacy ids.
                                                val idSeed =
                                                    if (chapter.startMs != null) {
                                                        chapter.filePath + "#" + chapter.startMs
                                                    } else {
                                                        chapter.filePath
                                                    }
                                                ChapterEntity(
                                                    id = "$bookId-chapter-${UUID.nameUUIDFromBytes(idSeed.toByteArray())}",
                                                    bookId = bookId,
                                                    title = chapter.title,
                                                    chapterIndex = chapter.index,
                                                    fileIndex = chapter.index,
                                                    duration = chapter.duration,
                                                    fileUrl = chapter.filePath,
                                                    isDownloaded = true,
                                                    startPositionMs = chapter.startMs,
                                                    endPositionMs = chapter.endMs,
                                                )
                                            },
                                        )
                                    } catch (e: Exception) {
                                        logger.e({ "Error processing book ${book.title}" }, e)
                                    }
                                }

                                // 3. Batch Upsert (insert or update) - faster for re-scans
                                if (bookEntities.isNotEmpty()) {
                                    PerfTrace.section(name = "LibraryScanWorker.upsertBatch") {
                                        booksDao.upsertScannedBooksWithChapters(bookEntities, chapterEntities)
                                    }
                                    booksSaved += bookEntities.size

                                    setProgress(
                                        workDataOf(
                                            "status" to
                                                applicationContext.resources.getQuantityString(
                                                    R.plurals.scan_status_completed_saving_plural,
                                                    booksSaved,
                                                    booksSaved,
                                                ),
                                        ),
                                    )
                                }
                            }

                            logger.i { "Library scan success attempt=$attempt booksFound=${books.size}" }
                            ListenableWorker.Result.success(
                                workDataOf("booksFound" to books.size),
                            )
                        }
                        is DomainResult.Error -> {
                            logger.w {
                                "Library scan failure result attempt=$attempt stopReason=${runCatching {
                                    stopReason
                                }.getOrDefault(
                                    -1,
                                )}"
                            }
                            ListenableWorker.Result.failure(
                                workDataOf("error" to result.error.message),
                            )
                        }
                        is DomainResult.Loading -> {
                            val currentStopReason = runCatching { stopReason }.getOrDefault(-1)
                            // Defensive: scanAudiobooks() never returns Loading today, but if it
                            // ever does, cap the retries so the worker can't loop forever.
                            if (attempt < MAX_SCAN_ATTEMPTS) {
                                logger.w {
                                    "Library scan returned loading, retrying attempt=$attempt stopReason=$currentStopReason"
                                }
                                ListenableWorker.Result.retry()
                            } else {
                                logger.e { "Library scan stuck in Loading after $attempt attempts" }
                                ListenableWorker.Result.failure(
                                    workDataOf("error" to "Library scan stuck in loading state"),
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        logger.w {
                            "Scan cancelled (Watchdog or User) attempt=$attempt stopReason=${runCatching {
                                stopReason
                            }.getOrDefault(
                                -1,
                            )}"
                        }
                        // Return failure so it doesn't retry automatically if cancelled by user/watchdog
                        return@withContext ListenableWorker.Result.failure()
                    }
                    logger.e({ "Scan failed" }, e)
                    CrashDiagnostics.reportNonFatal(
                        tag = "library_scan_failure",
                        throwable = e,
                        attributes =
                            mapOf(
                                "attempt" to attempt,
                                "stop_reason" to runCatching { stopReason }.getOrDefault(-1),
                            ),
                    )
                    ListenableWorker.Result.failure(
                        workDataOf(
                            "error" to (e.message ?: applicationContext.getString(R.string.libraryUnknownError)),
                        ),
                    )
                }
            }

        // Cover art removed - UI loads from book folder (cover.jpg/cover.jpeg)
    }
