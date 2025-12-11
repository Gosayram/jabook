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

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.data.download.DownloadWorker
import com.jabook.app.jabook.compose.domain.model.DownloadInfo
import com.jabook.app.jabook.compose.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-based implementation of DownloadRepository.
 *
 * Manages download tasks using WorkManager for background execution.
 */
@Singleton
class WorkManagerDownloadRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadRepository {
        private val workManager = WorkManager.getInstance(context)

        override fun startDownload(
            bookId: String,
            torrentUrl: String,
        ): Flow<DownloadState> {
            val workRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        Data
                            .Builder()
                            .putString(DownloadWorker.KEY_BOOK_ID, bookId)
                            .putString(DownloadWorker.KEY_TORRENT_URL, torrentUrl)
                            .build(),
                    ).setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()

            // Enqueue work with unique name to prevent duplicates
            workManager.enqueueUniqueWork(
                "download_$bookId",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )

            // Observe work status
            return workManager
                .getWorkInfosForUniqueWorkFlow("download_$bookId")
                .map { workInfoList ->
                    workInfoList.firstOrNull()?.let { workInfo ->
                        mapWorkInfoToDownloadState(workInfo)
                    } ?: DownloadState.Idle
                }.distinctUntilChanged()
        }

        override suspend fun pauseDownload(bookId: String) {
            // WorkManager doesn't support pause directly
            // Would need custom implementation with worker communication
            // For MVP, this is a no-op
        }

        override suspend fun resumeDownload(bookId: String) {
            // WorkManager will auto-retry failed work
            // For MVP, this is a no-op
        }

        override suspend fun cancelDownload(bookId: String) {
            workManager.cancelUniqueWork("download_$bookId")
        }

        override fun getDownloadStatus(bookId: String): Flow<DownloadState> =
            workManager
                .getWorkInfosForUniqueWorkFlow("download_$bookId")
                .map { workInfoList ->
                    workInfoList.firstOrNull()?.let { workInfo ->
                        mapWorkInfoToDownloadState(workInfo)
                    } ?: DownloadState.Idle
                }.distinctUntilChanged()

        override fun getActiveDownloads(): Flow<List<DownloadInfo>> =
            workManager
                .getWorkInfosByTagFlow("download")
                .map { workInfoList ->
                    workInfoList
                        .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                        .mapIndexed { index, workInfo ->
                            // Extract bookId from tags (WorkManager doesn't expose inputData in flows)
                            // For MVP, use placeholder
                            // TODO: Store book metadata in a separate database table
                            val bookId = "unknown_${workInfo.id}"

                            DownloadInfo(
                                bookId = bookId,
                                bookTitle = "Downloading book...",
                                torrentUrl = "",
                                state = mapWorkInfoToDownloadState(workInfo),
                                queuePosition = if (workInfo.state == WorkInfo.State.RUNNING) 0 else index + 1,
                            )
                        }
                }.distinctUntilChanged()

        /**
         * Map WorkInfo to DownloadState.
         */
        private fun mapWorkInfoToDownloadState(workInfo: WorkInfo): DownloadState =
            when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> DownloadState.Idle
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getFloat(DownloadWorker.KEY_PROGRESS, 0f)
                    DownloadState.Downloading(
                        progress = progress,
                        downloadedBytes = 0L, // Not tracked in stub
                        totalBytes = null,
                        speedBytesPerSecond = 0L,
                    )
                }

                WorkInfo.State.SUCCEEDED -> {
                    val localPath = workInfo.outputData.getString("local_path") ?: ""
                    DownloadState.Completed(localPath)
                }

                WorkInfo.State.FAILED -> DownloadState.Failed("Download failed")
                WorkInfo.State.CANCELLED -> DownloadState.Idle
                WorkInfo.State.BLOCKED -> DownloadState.Idle
            }
    }
