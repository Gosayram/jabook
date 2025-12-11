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

package com.jabook.app.jabook

import android.app.Application
import com.jabook.app.jabook.compose.data.sync.SyncManager
import com.jabook.app.jabook.compose.infrastructure.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Jabook with Dagger Hilt support.
 *
 * This class initializes Dagger Hilt for dependency injection
 * and creates notification channels.
 */
@HiltAndroidApp
class JabookApplication :
    Application(),
    androidx.work.Configuration.Provider {
    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    override val workManagerConfiguration: androidx.work.Configuration
        get() =
            androidx.work.Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()

        // Create notification channels for downloads and player
        NotificationHelper.createNotificationChannels(this)

        // Schedule periodic sync
        syncManager.schedulePeriodicSync()

        android.util.Log.d("JabookApplication", "Application created with Hilt support")
    }
}
