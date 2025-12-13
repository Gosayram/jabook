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
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.torrent.TorrentManager
import com.jabook.app.jabook.torrent.data.TorrentState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager-based worker for background torrent downloads.
 *
 * This worker:
 * - Downloads torrents in the background using TorrentManager
 * - Survives app restarts
 * - Reports progress via WorkManager progress API
 * - Can be retried on failure
 */
@HiltWorker
class DownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val torrentManager: TorrentManager,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "DownloadWorker"

            // Input data keys
            const val KEY_MAGNET_URI = "magnet_uri"
            const val KEY_SAVE_PATH = "save_path"
            const val KEY_BOOK_ID = "book_id"
            const val KEY_BOOK_TITLE = "book_title"

            // Output data keys
            const val KEY_INFO_HASH = "info_hash"
            const val KEY_FINAL_PATH = "final_path"

            // Progress data keys
            const val KEY_PROGRESS = "progress"
            const val KEY_STATE = "state"
            const val KEY_DOWNLOAD_RATE = "download_rate"
            const val KEY_NUM_PEERS = "num_peers"
        }

        override suspend fun doWork(): Result {
            val magnetUri = inputData.getString(KEY_MAGNET_URI) ?: return Result.failure()
            val savePath = inputData.getString(KEY_SAVE_PATH) ?: return Result.failure()
            val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: "Unknown Book"

            Log.d(TAG, "Starting download: $bookTitle")
            Log.d(TAG, "Magnet URI: $magnetUri")
            Log.d(TAG, "Save path: $savePath")

            return try {
                // Initialize TorrentManager if needed
                torrentManager.initialize()

                // Add magnet link
                val infoHash = torrentManager.addMagnetLink(magnetUri, savePath, sequential = true)
                Log.i(TAG, "Download started: $infoHash")

                // Monitor progress until complete or error
                monitorDownloadProgress(infoHash, bookTitle)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Result.failure(
                    workDataOf(
                        "error" to (e.message ?: "Unknown error"),
                    ),
                )
            }
        }

        private suspend fun monitorDownloadProgress(
            infoHash: String,
            bookTitle: String,
        ): Result {
            var result: Result? = null

            torrentManager.getDownloadProgress(infoHash).collect { progress ->
                // Update progress
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to progress.percentage.toInt(),
                        KEY_STATE to progress.state.name,
                        KEY_DOWNLOAD_RATE to progress.downloadRate,
                        KEY_NUM_PEERS to progress.numPeers,
                    ),
                )

                Log.d(
                    TAG,
                    "Progress: ${progress.percentage.toInt()}% - ${progress.state} - " +
                        "${progress.downloadRate / 1024}KB/s - ${progress.numPeers} peers",
                )

                // Check if download is complete
                when (progress.state) {
                    TorrentState.FINISHED -> {
                        Log.i(TAG, "Download completed: $bookTitle")
                        result =
                            Result.success(
                                workDataOf(
                                    KEY_INFO_HASH to infoHash,
                                    KEY_FINAL_PATH to inputData.getString(KEY_SAVE_PATH),
                                ),
                            )
                        return@collect // Exit collect
                    }
                    TorrentState.ERROR -> {
                        Log.e(TAG, "Download failed: $bookTitle")
                        result =
                            Result.failure(
                                workDataOf(
                                    "error" to "Torrent download failed",
                                ),
                            )
                        return@collect // Exit collect
                    }
                    else -> {
                        // Continue monitoring
                    }
                }
            }

            // Return result or default failure if flow completed
            return result ?: Result.failure(
                workDataOf(
                    "error" to "Download monitoring stopped unexpectedly",
                ),
            )
        }

        /**
         * Provide foreground service info for long-running work.
         */
        override suspend fun getForegroundInfo(): ForegroundInfo {
            val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: "Unknown Book"
            val notification =
                DownloadNotificationHelper.createDownloadNotification(
                    context = applicationContext,
                    title = "Downloading $bookTitle",
                    progress = 0f,
                    notificationId = id.hashCode(),
                )

            return ForegroundInfo(id.hashCode(), notification)
        }
    }
