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

package com.jabook.app.jabook.compose.data.torrent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Starts [TorrentDownloadService] when a direct foreground-service start was blocked
 * by Android 12+ background-start restrictions. Enqueued from TorrentManager fallback.
 */
@HiltWorker
public class TorrentStartWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val loggerFactory: LoggerFactory,
    ) : CoroutineWorker(context, params) {
        public companion object {
            public const val WORK_NAME: String = "torrent_start_service"
            private const val MAX_START_ATTEMPTS = 3
        }

        private val logger = loggerFactory.get("TorrentStartWorker")

        override suspend fun doWork(): Result {
            val intent =
                Intent(applicationContext, TorrentDownloadService::class.java).apply {
                    action = TorrentDownloadService.ACTION_START
                }
            return try {
                ContextCompat.startForegroundService(applicationContext, intent)
                logger.i { "Download service started via WorkManager fallback" }
                Result.success()
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                logger.w {
                    "FGS start not allowed from worker (attempt=$runAttemptCount): ${e.message}"
                }
                if (runAttemptCount < MAX_START_ATTEMPTS - 1) Result.retry() else Result.failure()
            } catch (e: Exception) {
                logger.e({ "Failed to start download service from worker" }, e)
                Result.failure()
            }
        }
    }
