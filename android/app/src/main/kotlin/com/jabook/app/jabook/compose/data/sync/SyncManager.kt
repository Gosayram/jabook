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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
class SyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "SyncManager"
            private const val SYNC_INTERVAL_HOURS = 6L
        }

        /**
         * Schedule periodic sync.
         *
         * Runs every 6 hours when device is:
         * - Connected to internet
         * - Not in low battery mode
         */
        fun schedulePeriodicSync() {
            Log.d(TAG, "Scheduling periodic sync")

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

            Log.d(TAG, "Periodic sync scheduled (every $SYNC_INTERVAL_HOURS hours)")
        }

        /**
         * Cancel sync.
         */
        fun cancelSync() {
            Log.d(TAG, "Cancelling sync")
            WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
        }

        /**
         * Trigger immediate sync.
         */
        fun syncNow() {
            Log.d(TAG, "Triggering immediate sync")
            // TODO: Implement one-time sync work
        }
    }
