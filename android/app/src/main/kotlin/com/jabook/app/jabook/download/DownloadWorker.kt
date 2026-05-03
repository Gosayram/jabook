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

package com.jabook.app.jabook.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.crash.CrashDiagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

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
public class DownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val torrentManager: TorrentManager,
    ) : CoroutineWorker(context, params) {
        public companion object {
            private const val TAG = "DownloadWorker"

            // Input data keys
            public const val KEY_MAGNET_URI: String = "magnet_uri"
            public const val KEY_SAVE_PATH: String = "save_path"
            public const val KEY_BOOK_ID: String = "book_id"
            public const val KEY_BOOK_TITLE: String = "book_title"

            // Output data keys
            public const val KEY_INFO_HASH: String = "info_hash"
            public const val KEY_FINAL_PATH: String = "final_path"

            // Progress data keys
            public const val KEY_PROGRESS: String = "progress"
            public const val KEY_STATE: String = "state"
            public const val KEY_DOWNLOAD_RATE: String = "download_rate"
            public const val KEY_NUM_PEERS: String = "num_peers"
        }

        override suspend fun doWork(): Result {
            val magnetUri = inputData.getString(KEY_MAGNET_URI) ?: return Result.failure()
            val savePath = inputData.getString(KEY_SAVE_PATH) ?: return Result.failure()
            val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: "Unknown Book"
            val attempt = runAttemptCount + 1
            val stopReasonAtStart = runCatching { stopReason }.getOrDefault(-1)

            Log.i(TAG, "Starting download: $bookTitle (attempt=$attempt, stopReason=$stopReasonAtStart)")
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
            } catch (e: CancellationException) {
                Log.i(TAG, "Download cancelled: $bookTitle")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                CrashDiagnostics.reportNonFatal(
                    tag = "download_worker_failure",
                    throwable = e,
                    attributes =
                        mapOf(
                            "attempt" to attempt,
                            "stop_reason" to runCatching { stopReason }.getOrDefault(-1),
                            "book_title" to bookTitle,
                        ),
                )
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

            torrentManager.getDownloadProgress(infoHash).collect { download ->
                // Update progress
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to (download.progress * 100).toInt(),
                        KEY_STATE to download.state.name,
                        KEY_DOWNLOAD_RATE to download.downloadSpeed.toLong(),
                        KEY_NUM_PEERS to download.numPeers,
                    ),
                )

                Log.d(
                    TAG,
                    "Progress: ${(download.progress * 100).toInt()}% - ${download.state} - " +
                        "${download.downloadSpeed / 1024}KB/s - ${download.numPeers} peers",
                )

                // Check if download is complete
                when (download.state) {
                    TorrentState.COMPLETED -> {
                        Log.i(TAG, "Download completed: $bookTitle")
                        Log.i(TAG, "Download worker success stopReason=${runCatching { stopReason }.getOrDefault(-1)}")
                        result =
                            Result.success(
                                workDataOf(
                                    KEY_INFO_HASH to infoHash,
                                    KEY_FINAL_PATH to inputData.getString(KEY_SAVE_PATH),
                                ),
                            )
                    }
                    TorrentState.ERROR -> {
                        Log.e(TAG, "Download failed: $bookTitle")
                        CrashDiagnostics.reportNonFatal(
                            tag = "download_worker_torrent_error",
                            throwable = IllegalStateException("Torrent entered ERROR state"),
                            attributes =
                                mapOf(
                                    "attempt" to (runAttemptCount + 1),
                                    "stop_reason" to runCatching { stopReason }.getOrDefault(-1),
                                    "book_title" to bookTitle,
                                    "info_hash" to infoHash,
                                ),
                        )
                        result =
                            Result.failure(
                                workDataOf(
                                    "error" to "Torrent download failed",
                                ),
                            )
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
