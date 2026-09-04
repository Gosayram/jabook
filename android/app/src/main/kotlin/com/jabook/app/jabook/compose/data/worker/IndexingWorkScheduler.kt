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

import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Schedules a user-requested forum index with a transparent expedited fallback. */
@ViewModelScoped
public class IndexingWorkScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) {
        public fun enqueue() {
            val request =
                OneTimeWorkRequestBuilder<IndexingWorker>()
                    .setInputData(
                        Data
                            .Builder()
                            .putBoolean(IndexingWorker.KEY_PRELOAD_COVERS, true)
                            .build(),
                    ).setConstraints(WorkConstraintsPolicy.userInitiatedDownload())
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .addTag(IndexingWorker.WORK_NAME_ONE_TIME)
                    .build()

            workManager.enqueueUniqueWork(
                IndexingWorker.WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        public fun cancel() {
            workManager.cancelUniqueWork(IndexingWorker.WORK_NAME_ONE_TIME)
        }

        public fun observe(): kotlinx.coroutines.flow.Flow<List<WorkInfo>> =
            workManager.getWorkInfosForUniqueWorkFlow(IndexingWorker.WORK_NAME_ONE_TIME)
    }
