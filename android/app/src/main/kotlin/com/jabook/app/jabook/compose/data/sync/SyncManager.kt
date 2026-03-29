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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling and managing sync operations.
 *
 * Uses WorkManager to schedule periodic sync tasks.
 */
@Singleton
public class SyncManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("SyncManager")

        public companion object {
            private const val SYNC_INTERVAL_HOURS = 6L
            private const val IMMEDIATE_SYNC_WORK_NAME = "sync_work_immediate"
        }

        /**
         * Schedule periodic sync.
         *
         * Runs every 6 hours when device is:
         * - Connected to internet
         * - Not in low battery mode
         */
        public fun schedulePeriodicSync() {
            logger.d { "Scheduling periodic sync" }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val syncRequest =
                PeriodicWorkRequestBuilder<SyncWorker>(
                    SYNC_INTERVAL_HOURS,
                    TimeUnit.HOURS,
                ).setConstraints(constraints)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    SyncWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest,
                )

            logger.d { "Periodic sync scheduled (every $SYNC_INTERVAL_HOURS hours)" }
        }

        /**
         * Cancel sync.
         */
        public fun cancelSync() {
            logger.d { "Cancelling sync" }
            WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
        }

        /**
         * Trigger immediate sync.
         */
        public fun syncNow() {
            logger.d { "Triggering immediate sync" }
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            val syncRequest =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest,
            )
        }
    }
