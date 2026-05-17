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

package com.jabook.app.jabook.audio

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.data.worker.ChapterDetectionWorker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues unique chapter-detection work for a specific book/file pair.
 */
@Singleton
public class ChapterDetectionWorkScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) {
        internal constructor(
            workManager: WorkManager,
            nowMsProvider: () -> Long,
        ) : this(workManager) {
            this.nowMsProvider = nowMsProvider
        }

        private var nowMsProvider: () -> Long = { System.currentTimeMillis() }

        private val enqueueHistory =
            ConcurrentHashMap<String, ChapterDetectionEnqueueGuardPolicy.EnqueueRecord>()

        public fun enqueue(
            bookId: String,
            filePath: String,
            fileIndex: Int,
            durationMs: Long,
            fileLastModifiedMs: Long,
        ) {
            if (bookId.isBlank() || filePath.isBlank() || durationMs <= 0L) return

            val workName = "${ChapterDetectionWorker.WORK_NAME_PREFIX}_${bookId}_$fileIndex"
            val signature =
                ChapterDetectionEnqueueGuardPolicy.FileSignature(
                    filePath = filePath,
                    fileIndex = fileIndex,
                    durationMs = durationMs,
                    lastModifiedMs = fileLastModifiedMs,
                )
            val nowMs = nowMsProvider()
            if (
                ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                    previous = enqueueHistory[workName],
                    next = signature,
                    nowMs = nowMs,
                )
            ) {
                return
            }
            val request =
                OneTimeWorkRequestBuilder<ChapterDetectionWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .build(),
                    ).setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10,
                        TimeUnit.SECONDS,
                    ).setInputData(
                        workDataOf(
                            ChapterDetectionWorker.KEY_BOOK_ID to bookId,
                            ChapterDetectionWorker.KEY_FILE_PATH to filePath,
                            ChapterDetectionWorker.KEY_FILE_INDEX to fileIndex,
                            ChapterDetectionWorker.KEY_DURATION_MS to durationMs,
                            ChapterDetectionWorker.KEY_FILE_LAST_MODIFIED_MS to fileLastModifiedMs,
                        ),
                    ).build()

            // Keep currently running/enqueued work to avoid churn during frequent re-scans.
            // A new request with the same unique work name will wait until current work
            // completes instead of replacing it mid-flight.
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
            enqueueHistory[workName] =
                ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                    signature = signature,
                    enqueuedAtMs = nowMs,
                )
        }
    }
