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

@file:Suppress("UnusedImport")

package com.jabook.app.jabook.compose.data.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager worker for downloading books via torrent.
 *
 * Runs in background and shows progress notification.
 * Uses TorrentDownloader for actual download logic.
 *
 * Note: For MVP, this uses stub downloader.
 * Real torrent integration will be added in future phase.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun getTorrentDownloader(): LibTorrentDownloader
    }

    private val torrentDownloader =
        EntryPointAccessors
            .fromApplication(
                applicationContext,
                DownloadWorkerEntryPoint::class.java,
            ).getTorrentDownloader()

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_TORRENT_URL = "torrent_url"
        const val KEY_SAVE_PATH = "save_path"
        const val KEY_PROGRESS = "progress"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val torrentUrl = inputData.getString(KEY_TORRENT_URL) ?: return Result.failure()
        val savePath =
            inputData.getString(KEY_SAVE_PATH)
                ?: "${applicationContext.filesDir}/downloads/$bookId"

        Log.d(TAG, "Starting download for book $bookId")

        return try {
            // Set as foreground work with notification
            setForeground(createForegroundInfo(0f, "Starting download..."))

            // Start download
            val localPath =
                torrentDownloader.download(
                    torrentUrl = torrentUrl,
                    savePath = savePath,
                    onProgress = { progress ->
                        // Update progress
                        runCatching {
                            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                            // Update foreground notification
                            setForegroundAsync(
                                createForegroundInfo(
                                    progress,
                                    "Downloading... ${(progress * 100).toInt()}%",
                                ),
                            )
                        }
                    },
                )

            Log.d(TAG, "Download completed: $localPath")

            // TODO: Update database with downloaded file path
            // For MVP, just return success
            Result.success(workDataOf("local_path" to localPath))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure()
        }
    }

    /**
     * Create foreground notification info.
     */
    private fun createForegroundInfo(
        progress: Float,
        contentText: String,
    ): ForegroundInfo {
        val notification =
            androidx.core.app.NotificationCompat
                .Builder(
                    applicationContext,
                    "downloads",
                ).setContentTitle("Downloading book")
                .setContentText(contentText)
                .setProgress(100, (progress * 100).toInt(), false)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
