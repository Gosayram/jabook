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

package com.jabook.app.jabook.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for download queue using WorkManager.
 *
 * Handles:
 * - Queuing downloads
 * - Managing download lifecycle
 * - Tracking download progress
 * - Retry logic
 */
@Singleton
class DownloadQueueManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val workManager = WorkManager.getInstance(context)

        /**
         * Enqueue a download.
         *
         * @param bookId Unique ID for the book
         * @param bookTitle Human-readable book title
         * @param magnetUri Magnet link
         * @param savePath Directory to save files
         * @return Work ID for tracking
         */
        fun enqueueDownload(
            bookId: String,
            bookTitle: String,
            magnetUri: String,
            savePath: String,
        ): UUID {
            val inputData =
                Data
                    .Builder()
                    .putString(DownloadWorker.KEY_BOOK_ID, bookId)
                    .putString(DownloadWorker.KEY_BOOK_TITLE, bookTitle)
                    .putString(DownloadWorker.KEY_MAGNET_URI, magnetUri)
                    .putString(DownloadWorker.KEY_SAVE_PATH, savePath)
                    .build()

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Require network
                    .setRequiresBatteryNotLow(false) // Allow on low battery
                    .build()

            val downloadRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("download")
                    .addTag("book_$bookId")
                    .build()

            // Enqueue with unique work policy (replace existing)
            workManager.enqueueUniqueWork(
                "download_$bookId",
                ExistingWorkPolicy.REPLACE,
                downloadRequest,
            )

            return downloadRequest.id
        }

        /**
         * Cancel a download by book ID.
         */
        fun cancelDownload(bookId: String) {
            workManager.cancelUniqueWork("download_$bookId")
        }

        /**
         * Cancel a download by work ID.
         */
        fun cancelDownload(workId: UUID) {
            workManager.cancelWorkById(workId)
        }

        /**
         * Get download progress as a Flow.
         */
        fun getDownloadProgress(workId: UUID): Flow<DownloadProgressInfo?> =
            workManager
                .getWorkInfoByIdFlow(workId)
                .map { workInfo ->
                    workInfo?.let { mapWorkInfoToProgress(it) }
                }

        /**
         * Get all active downloads.
         */
        fun getActiveDownloads(): Flow<List<DownloadProgressInfo>> =
            workManager
                .getWorkInfosByTagFlow("download")
                .map { workInfos ->
                    workInfos
                        .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                        .mapNotNull { mapWorkInfoToProgress(it) }
                }

        /**
         * Map WorkInfo to DownloadProgressInfo.
         */
        private fun mapWorkInfoToProgress(workInfo: WorkInfo): DownloadProgressInfo? {
            // Extract bookId from tags (format: "book_<bookId>")
            val bookId =
                workInfo.tags
                    .firstOrNull { it.startsWith("book_") }
                    ?.removePrefix("book_")
                    ?: return null // Can't identify the book

            val prog = workInfo.progress

            return DownloadProgressInfo(
                workId = workInfo.id,
                bookId = bookId,
                bookTitle = "Downloading...", // Title not available from WorkInfo
                state = workInfo.state,
                progress = prog.getInt(DownloadWorker.KEY_PROGRESS, 0),
                downloadRate = prog.getLong(DownloadWorker.KEY_DOWNLOAD_RATE, 0),
                numPeers = prog.getInt(DownloadWorker.KEY_NUM_PEERS, 0),
            )
        }
    }

/**
 * Download progress information from WorkManager.
 */
data class DownloadProgressInfo(
    val workId: UUID,
    val bookId: String,
    val bookTitle: String,
    val state: WorkInfo.State,
    val progress: Int,
    val downloadRate: Long,
    val numPeers: Int,
)
