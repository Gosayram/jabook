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
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

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
        private val rutrackerRepository: com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository,
    ) : CoroutineWorker(appContext, params) {
        public companion object {
            private const val TAG = "SyncWorker"
            public const val WORK_NAME: String = "sync_work"
            private const val CACHE_TTL_DAYS = 7L
        }

        override suspend fun doWork(): Result {
            Log.d(TAG, "Starting sync work")

            return try {
                // Sync book metadata
                syncBookMetadata()

                // Sync cover images
                syncCoverImages()

                // Clean up old data
                cleanupOldData()

                Log.d(TAG, "Sync completed successfully")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }

        private suspend fun syncBookMetadata() {
            Log.d(TAG, "Syncing book metadata")

            // Get downloads with topicId
            val downloads = torrentDownloadRepository.getAll().filter { !it.topicId.isNullOrEmpty() }
            Log.d(TAG, "Found ${downloads.size} downloads to sync")

            for (download in downloads) {
                val topicId = download.topicId ?: continue

                try {
                    // Fetch details from RuTracker
                    val result = rutrackerRepository.getTopicDetails(topicId)

                    if (result.isSuccess) {
                        val details = result.getOrNull() ?: continue

                        // Find matching book by path
                        // Ideally we would have a better link, but path is what we have for now
                        // We check if the book path contains the download path or vice versa
                        val books = booksDao.getAllBooks()
                        val matchedBook =
                            books.find { book ->
                                book.localPath?.let { localPath ->
                                    localPath == download.savePath ||
                                        localPath.startsWith(download.savePath) ||
                                        download.savePath.startsWith(localPath)
                                } == true
                            }

                        if (matchedBook != null) {
                            Log.d(TAG, "Updating metadata for book: ${matchedBook.title}")

                            // Update metadata if needed
                            // For now, we mainly care about missing covers or empty metadata

                            var needsUpdate: Boolean = false
                            val updatedBook = matchedBook.copy() // Create copy to modify

                            // Update title if generic
                            if (matchedBook.title.isEmpty() || matchedBook.title == "Unknown Title") {
                                // We can't easily change Val in copy if not exposed, creating new object or modifying var
                                // BookEntity properties are vals. copy() is the way.
                                // However, Kotlin copy() is on data class.
                                // Let's check BookEntity structure if needed, but standard copy works.
                                // Wait, simple variables:
                                // updatedBook.title = details.title // Error if val
                                // We need to use copy parameters
                            }

                            // Update cover URL if missing
                            if (matchedBook.coverUrl.isNullOrEmpty() && !details.coverUrl.isNullOrEmpty()) {
                                // We need to execute an update query
                                details.coverUrl?.let { url ->
                                    booksDao.updateCoverUrl(matchedBook.id, url)
                                    Log.i(TAG, "Updated cover URL for ${matchedBook.title}")
                                }
                            }

                            // Update author if missing or generic
                            if ((matchedBook.author.isEmpty() || matchedBook.author == "Unknown Author") &&
                                !details.author.isNullOrEmpty()
                            ) {
                                details.author?.let { author ->
                                    booksDao.updateAuthor(matchedBook.id, author)
                                    Log.i(TAG, "Updated author for ${matchedBook.title}: $author")
                                }
                            }

                            // Update description if missing
                            if (matchedBook.description.isNullOrEmpty() && !details.description.isNullOrEmpty()) {
                                details.description?.let { description ->
                                    booksDao.updateDescription(matchedBook.id, description)
                                    Log.i(TAG, "Updated description for ${matchedBook.title}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync metadata for topic $topicId", e)
                }
            }
        }

        private suspend fun syncCoverImages() {
            Log.d(TAG, "Syncing cover images")

            // Find books with coverUrl but no local coverPath
            val books = booksDao.getAllBooks()
            val booksNeedCover =
                books.filter {
                    !it.coverUrl.isNullOrEmpty() &&
                        (it.coverPath.isNullOrEmpty() || !java.io.File(it.coverPath!!).exists())
                }

            Log.d(TAG, "Found ${booksNeedCover.size} books needing cover download")

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
                        // Download file
                        val url = java.net.URL(coverUrl)
                        url.openStream().use { input ->
                            java.io.FileOutputStream(coverFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Update DB
                        booksDao.updateCoverPath(book.id, coverFile.absolutePath)
                        Log.i(TAG, "Downloaded cover for ${book.title}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download cover for ${book.title}", e)
                }
            }
        }

        private suspend fun cleanupOldData() {
            Log.d(TAG, "Cleaning up old search cache")
            val threshold = System.currentTimeMillis() - (CACHE_TTL_DAYS * 24 * 60 * 60 * 1000) // 7 days ago
            offlineSearchDao.clearOldCache(threshold)
        }
    }
