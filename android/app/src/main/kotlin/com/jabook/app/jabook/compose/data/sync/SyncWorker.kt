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

package com.jabook.app.jabook.compose.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.network.CoverDownloadNetworkPolicy
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.storage.AtomicFileWriter
import com.jabook.app.jabook.crash.CrashDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Worker for periodic data synchronization.
 *
 * Runs in background to:
 * - Update book metadata from remote sources
 * - Sync cover images
 * - Check for book updates
 * - Clean up old data
 *
 * Note: This is a minimal implementation that will be enhanced
 * with proper dependency injection when kapt is replaced with KSP.
 */
@androidx.hilt.work.HiltWorker
public class SyncWorker
    @dagger.assisted.AssistedInject
    constructor(
        @dagger.assisted.Assisted appContext: Context,
        @dagger.assisted.Assisted params: WorkerParameters,
        private val offlineSearchDao: com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao,
        private val torrentDownloadRepository: com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository,
        private val booksDao: com.jabook.app.jabook.compose.data.local.dao.BooksDao,
        private val rutrackerRepository: com.jabook.app.jabook.compose.data.repository.RutrackerRepository,
        private val settingsRepository: SettingsRepository,
        private val networkMonitor: NetworkMonitor,
        @param:javax.inject.Named("coverDownload") private val coverDownloadClient: OkHttpClient,
        private val loggerFactory: LoggerFactory,
    ) : CoroutineWorker(appContext, params) {
        private val logger = loggerFactory.get("SyncWorker")

        public companion object {
            public const val WORK_NAME: String = "sync_work"
            private const val CACHE_TTL_DAYS = 7L
            private const val MAX_COVER_BYTES = 5L * 1024 * 1024
        }

        override suspend fun doWork(): Result {
            val attempt = runAttemptCount + 1
            val stopReasonAtStart = runCatching { stopReason }.getOrDefault(-1)
            logger.i { "Starting sync work attempt=$attempt stopReason=$stopReasonAtStart" }

            return try {
                // Sync book metadata
                syncBookMetadata()

                // Sync cover images
                syncCoverImages()

                // Clean up old data
                cleanupOldData()

                logger.i { "Sync completed successfully attempt=$attempt" }
                Result.success()
            } catch (e: CancellationException) {
                logger.i { "Sync cancelled attempt=$attempt" }
                throw e
            } catch (e: Exception) {
                logger.e({ "Sync failed" }, e)
                if (runAttemptCount < 3) {
                    logger.w {
                        "Sync scheduled for retry attempt=$attempt stopReason=${runCatching { stopReason }.getOrDefault(
                            -1,
                        )}"
                    }
                    CrashDiagnostics.reportNonFatal(
                        tag = "sync_worker_retry",
                        throwable = e,
                        attributes =
                            mapOf(
                                "attempt" to attempt,
                                "stop_reason" to runCatching { stopReason }.getOrDefault(-1),
                            ),
                    )
                    Result.retry()
                } else {
                    CrashDiagnostics.reportNonFatal(
                        tag = "sync_worker_failure",
                        throwable = e,
                        attributes =
                            mapOf(
                                "attempt" to attempt,
                                "stop_reason" to runCatching { stopReason }.getOrDefault(-1),
                            ),
                    )
                    Result.failure()
                }
            }
        }

        private suspend fun syncBookMetadata() {
            logger.d { "Syncing book metadata" }

            // Get downloads with topicId
            val downloads = torrentDownloadRepository.getAll().filter { !it.topicId.isNullOrEmpty() }
            val books = if (downloads.isEmpty()) emptyList() else booksDao.getAllBooks()
            logger.d { "Found ${downloads.size} downloads to sync" }

            // Batch field-level updates into one Room transaction at the end.
            val pendingUpdates = mutableListOf<BooksDao.BookMetadataUpdate>()

            for (download in downloads) {
                val topicId = download.topicId ?: continue

                try {
                    // Fetch details from RuTracker
                    val result = rutrackerRepository.getTopicDetails(topicId)

                    if (result is com.jabook.app.jabook.compose.domain.model.Result.Success) {
                        val details = result.data

                        // Find matching book by path
                        // Ideally we would have a better link, but path is what we have for now
                        // Prefix matches need a path-separator boundary: /Books must not match /Books2
                        val matchedBook =
                            books.find { book ->
                                book.localPath?.let { localPath ->
                                    localPath == download.savePath ||
                                        localPath.startsWith(download.savePath + java.io.File.separator) ||
                                        download.savePath.startsWith(localPath + java.io.File.separator)
                                } == true
                            }

                        if (matchedBook != null) {
                            logger.d { "Updating metadata for book: ${matchedBook.title}" }

                            var update: BooksDao.BookMetadataUpdate? = null

                            // Update cover URL if missing
                            if (matchedBook.coverUrl.isNullOrEmpty() && !details.coverUrl.isNullOrEmpty()) {
                                update =
                                    (update ?: BooksDao.BookMetadataUpdate(matchedBook.id)).copy(
                                        coverUrl = details.coverUrl,
                                    )
                                logger.i { "Updated cover URL for ${matchedBook.title}" }
                            }

                            // Update author if missing or generic
                            if ((matchedBook.author.isEmpty() || matchedBook.author == "Unknown Author") &&
                                !details.author.isNullOrEmpty()
                            ) {
                                update =
                                    (update ?: BooksDao.BookMetadataUpdate(matchedBook.id)).copy(
                                        author = details.author,
                                    )
                                logger.i { "Updated author for ${matchedBook.title}: ${details.author}" }
                            }

                            // Update description if missing
                            if (matchedBook.description.isNullOrEmpty() && !details.description.isNullOrEmpty()) {
                                update =
                                    (update ?: BooksDao.BookMetadataUpdate(matchedBook.id)).copy(
                                        description = details.description,
                                    )
                                logger.i { "Updated description for ${matchedBook.title}" }
                            }

                            update?.let { pendingUpdates.add(it) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Failed to sync metadata for topic $topicId" }, e)
                }
            }

            if (pendingUpdates.isNotEmpty()) {
                booksDao.applyMetadataSync(pendingUpdates)
                logger.i { "Applied ${pendingUpdates.size} batched metadata updates" }
            }
        }

        private suspend fun syncCoverImages() {
            logger.d { "Syncing cover images" }
            val prefs = settingsRepository.userPreferences.first()
            val networkType = networkMonitor.networkType.first()
            if (
                !CoverDownloadNetworkPolicy.canAutoLoadCovers(
                    networkType = networkType,
                    allowOnCellular = prefs.autoLoadCoversOnCellular,
                )
            ) {
                logger.i {
                    "Skipping cover sync on $networkType network (auto_load_covers_on_cellular=${prefs.autoLoadCoversOnCellular})"
                }
                return
            }

            // Find books with coverUrl but no local coverPath
            val books = booksDao.getAllBooks()
            val booksNeedCover =
                books.filter {
                    !it.coverUrl.isNullOrEmpty() &&
                        (it.coverPath.isNullOrEmpty() || !java.io.File(it.coverPath).exists())
                }

            logger.d { "Found ${booksNeedCover.size} books needing cover download" }

            val coverPathUpdates = mutableListOf<BooksDao.BookMetadataUpdate>()

            for (book in booksNeedCover) {
                try {
                    val coverUrl = book.coverUrl ?: continue

                    // Simple download to cache dir
                    // Note: Ideally we use a dedicated ImageDownloader or Coil's loader
                    // But here we want a persistent file path to save to DB

                    val coverDir = java.io.File(applicationContext.filesDir, "covers")
                    if (!coverDir.exists()) coverDir.mkdirs()

                    val fileName: String = "cover_${book.id}.jpg"
                    val coverFile = java.io.File(coverDir, fileName)

                    if (!coverFile.exists()) {
                        val uriScheme =
                            android.net.Uri
                                .parse(coverUrl)
                                .scheme
                                ?.lowercase()
                        if (uriScheme != "http" && uriScheme != "https") {
                            logger.w { "Skipping cover for ${book.title}: unsupported scheme '$uriScheme' in $coverUrl" }
                            continue
                        }
                        val request = Request.Builder().url(coverUrl).build()
                        withContext(Dispatchers.IO) {
                            coverDownloadClient.newCall(request).execute().use { response ->
                                check(response.isSuccessful) { "Cover request failed: HTTP ${response.code}" }
                                // ponytail: 5MB cap — coverUrl is parser-supplied; a hostile
                                // URL must not fill storage. Oversize covers are useless anyway.
                                val declaredLength = response.body.contentLength()
                                check(declaredLength <= MAX_COVER_BYTES) {
                                    "Cover too large: $declaredLength bytes (limit $MAX_COVER_BYTES)"
                                }
                                response.body.byteStream().use { input ->
                                    AtomicFileWriter.writeWithLock(coverFile) { output ->
                                        val buffer = ByteArray(8192)
                                        var copied = 0L
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read == -1) break
                                            copied += read
                                            check(copied <= MAX_COVER_BYTES) {
                                                "Cover exceeded $MAX_COVER_BYTES bytes while streaming"
                                            }
                                            output.write(buffer, 0, read)
                                        }
                                        copied
                                    }
                                }
                            }
                        }

                        // Update DB only after the complete file has been written.
                        if (coverFile.exists()) {
                            coverPathUpdates.add(
                                BooksDao.BookMetadataUpdate(bookId = book.id, coverPath = coverFile.absolutePath),
                            )
                            logger.i { "Downloaded cover for ${book.title}" }
                        } else {
                            error("Cover file was not created")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Failed to download cover for ${book.title}" }, e)
                }
            }

            if (coverPathUpdates.isNotEmpty()) {
                booksDao.applyMetadataSync(coverPathUpdates)
                logger.i { "Applied ${coverPathUpdates.size} batched cover-path updates" }
            }
        }

        private suspend fun cleanupOldData() {
            logger.d { "Cleaning up old search cache" }
            val threshold = System.currentTimeMillis() - (CACHE_TTL_DAYS * 24 * 60 * 60 * 1000) // 7 days ago
            offlineSearchDao.clearOldCache(threshold)
        }
    }
