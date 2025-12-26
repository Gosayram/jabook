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
class SyncWorker
    @dagger.assisted.AssistedInject
    constructor(
        @dagger.assisted.Assisted appContext: Context,
        @dagger.assisted.Assisted params: WorkerParameters,
        private val offlineSearchDao: com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao,
        private val torrentDownloadRepository: com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository,
    ) : CoroutineWorker(appContext, params) {
        companion object {
            private const val TAG = "SyncWorker"
            const val WORK_NAME = "sync_work"
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
            Log.d(TAG, "Syncing book metadata - TODO (Waiting for topicId migration)")
            // TODO: Implement updates for downloaded torrents once topicId is available in TorrentDownload entity
        }

        private suspend fun syncCoverImages() {
            Log.d(TAG, "Syncing cover images - TODO")
            // TODO: Implement cover sync logic
        }

        private suspend fun cleanupOldData() {
            Log.d(TAG, "Cleaning up old search cache")
            val threshold = System.currentTimeMillis() - (CACHE_TTL_DAYS * 24 * 60 * 60 * 1000) // 7 days ago
            offlineSearchDao.clearOldCache(threshold)
        }
    }
