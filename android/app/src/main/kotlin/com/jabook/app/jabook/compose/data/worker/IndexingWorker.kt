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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for background forum indexing.
 *
 * Features:
 * - Survives app restarts
 * - Respects network constraints (Wi-Fi only)
 * - Reports progress via WorkManager progress API
 * - Chunked + resumable via ForumIndexer
 */
@HiltWorker
public class IndexingWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val forumIndexer: ForumIndexer,
        private val loggerFactory: LoggerFactory,
    ) : CoroutineWorker(context, params) {
        public companion object {
            private const val TAG = "IndexingWorker"

            // Work names
            public const val WORK_NAME_PERIODIC: String = "jabook_periodic_indexing"
            public const val WORK_NAME_ONE_TIME: String = "jabook_one_time_indexing"
            private const val NOTIFICATION_ID: Int = 3_104
            private const val NOTIFICATION_CHANNEL_ID: String = "indexing_work"

            // Progress keys
            public const val KEY_PROGRESS_PERCENT: String = "progress_percent"
            public const val KEY_PROGRESS_MESSAGE: String = "progress_message"
            public const val KEY_TOPICS_INDEXED: String = "topics_indexed"
            public const val KEY_FORUM_IDS: String = "forumIds"
            public const val KEY_PRELOAD_COVERS: String = "preloadCovers"

            internal fun parseForumIds(input: String?): String {
                val forumIds =
                    input
                        ?.split(',')
                        ?.map(String::trim)
                        ?.filter(String::isNotEmpty)

                return if (forumIds.isNullOrEmpty() || forumIds.any { !it.all(Char::isDigit) }) {
                    RutrackerApi.AUDIOBOOKS_FORUM_IDS
                } else {
                    forumIds.joinToString(",")
                }
            }
        }

        private val logger = loggerFactory.get(TAG)

        override suspend fun getForegroundInfo(): ForegroundInfo {
            createNotificationChannel()
            val notification =
                NotificationCompat
                    .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(com.jabook.app.jabook.R.drawable.ic_notification_logo)
                    .setContentTitle(applicationContext.getString(com.jabook.app.jabook.R.string.indexingNotificationTitle))
                    .setContentText(applicationContext.getString(com.jabook.app.jabook.R.string.indexingNotificationBody))
                    .setOngoing(true)
                    .build()
            return ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }

        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                logger.i { "Starting indexing worker (attempt=$runAttemptCount)" }

                var errorOccurred = false
                val progressMutex = Mutex()

                try {
                    try {
                        setForeground(getForegroundInfo())
                    } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                        // Android 12+: FGS start may be denied when worker runs without
                        // user visibility (e.g. expedited fallback). Degrade to background.
                        logger.w { "FGS start not allowed for indexing worker: ${e.message}" }
                    }
                    val forumIds = parseForumIds(inputData.getString(KEY_FORUM_IDS))
                    val preloadCovers = inputData.getBoolean(KEY_PRELOAD_COVERS, false)

                    forumIndexer.indexForums(
                        forumIds = forumIds,
                        preloadCovers = preloadCovers,
                    ) { progress ->
                        when (progress) {
                            is IndexingProgress.InProgress -> {
                                val percent =
                                    if (progress.detail.totalForums > 0) {
                                        (progress.detail.percentComplete * 100).toInt()
                                    } else {
                                        0
                                    }
                                progressMutex.withLock {
                                    setProgress(
                                        Data
                                            .Builder()
                                            .putInt(KEY_PROGRESS_PERCENT, percent)
                                            .putString(KEY_PROGRESS_MESSAGE, progress.detail.currentForumName)
                                            .build(),
                                    )
                                }
                            }
                            is IndexingProgress.Completed -> {
                                progressMutex.withLock {
                                    setProgress(
                                        Data
                                            .Builder()
                                            .putInt(KEY_PROGRESS_PERCENT, 100)
                                            .putLong(KEY_TOPICS_INDEXED, progress.totalTopics.toLong())
                                            .build(),
                                    )
                                }
                            }
                            is IndexingProgress.Error -> {
                                errorOccurred = true
                                logger.e({ "Indexing error: ${progress.message}" })
                            }
                            else -> { /* Idle */ }
                        }
                    }

                    if (errorOccurred) {
                        Result.failure()
                    } else {
                        logger.i { "Indexing worker completed successfully" }
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    logger.w({ "Indexing worker cancelled" })
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Indexing worker failed" }, e)
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure(
                            workDataOf(
                                "error_message" to (e.message ?: "Unknown error"),
                            ),
                        )
                    }
                }
            }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Индексация",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
        }
    }
