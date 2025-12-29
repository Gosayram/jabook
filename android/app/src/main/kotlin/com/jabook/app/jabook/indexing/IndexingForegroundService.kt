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

package com.jabook.app.jabook.indexing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.ComposeMainActivity
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for forum indexing.
 * Shows progress in notification panel and allows indexing to continue in background.
 */
@AndroidEntryPoint
class IndexingForegroundService : Service() {
    @Inject
    lateinit var forumIndexer: ForumIndexer

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var indexingJob: Job? = null
    private var currentProgress: IndexingProgress = IndexingProgress.Idle

    companion object {
        private const val TAG = "IndexingForegroundService"
        private const val CHANNEL_ID = "jabook_indexing"
        private const val CHANNEL_NAME = "Индексация форумов"
        private const val NOTIFICATION_ID = 100
        private const val AUTO_DISMISS_DELAY_MS = 5000L // 5 seconds

        const val ACTION_START = "com.jabook.app.jabook.indexing.START"
        const val ACTION_STOP = "com.jabook.app.jabook.indexing.STOP"
        const val ACTION_UPDATE_PROGRESS = "com.jabook.app.jabook.indexing.UPDATE_PROGRESS"

        const val EXTRA_PROGRESS = "progress"

        @Volatile
        private var instance: IndexingForegroundService? = null

        fun getInstance(): IndexingForegroundService? = instance

        /**
         * Start the indexing service.
         */
        fun start(context: Context) {
            val intent =
                Intent(context, IndexingForegroundService::class.java).apply {
                    action = ACTION_START
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the indexing service.
         */
        fun stop(context: Context) {
            val intent =
                Intent(context, IndexingForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting indexing")
                startForegroundWithNotification()
                startIndexing()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service")
                stopSelf()
            }
            ACTION_UPDATE_PROGRESS -> {
                // Progress update from external source (if needed)
                // Note: IndexingProgress is not Serializable, so we update from service's own state
                updateNotification(currentProgress)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        indexingJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    /**
     * Creates notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Уведомления о процессе индексации форумов"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Starts foreground service with initial notification.
     */
    private fun startForegroundWithNotification() {
        val notification = createNotification(IndexingProgress.Idle)
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Starts indexing process.
     */
    private fun startIndexing() {
        if (indexingJob?.isActive == true) {
            Log.w(TAG, "Indexing already in progress")
            return
        }

        indexingJob =
            serviceScope.launch {
                try {
                    // Check authentication
                    val currentAuthStatus = authRepository.authStatus.first()
                    val isAuthenticated = currentAuthStatus is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated

                    val hasValidUsername =
                        when (currentAuthStatus) {
                            is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated -> {
                                currentAuthStatus.username.isNotBlank() && currentAuthStatus.username != "User"
                            }
                            else -> false
                        }

                    if (!isAuthenticated || !hasValidUsername) {
                        Log.w(TAG, "Cannot start indexing: user is not authenticated")
                        currentProgress = IndexingProgress.Error("Требуется авторизация для индексации форумов")
                        updateNotification(currentProgress)
                        delay(AUTO_DISMISS_DELAY_MS)
                        stopSelf()
                        return@launch
                    }

                    currentProgress = IndexingProgress.Idle
                    updateNotification(currentProgress)

                    forumIndexer.indexForums(
                        forumIds = RutrackerApi.AUDIOBOOKS_FORUM_IDS,
                        preloadCovers = true,
                    ) { progress ->
                        currentProgress = progress
                        updateNotification(progress)

                        // If completed or error, schedule auto-dismiss
                        if (progress is IndexingProgress.Completed || progress is IndexingProgress.Error) {
                            serviceScope.launch {
                                delay(AUTO_DISMISS_DELAY_MS)
                                stopSelf()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Indexing failed", e)
                    currentProgress = IndexingProgress.Error(e.message ?: "Unknown error")
                    updateNotification(currentProgress)
                    delay(AUTO_DISMISS_DELAY_MS)
                    stopSelf()
                }
            }
    }

    /**
     * Creates notification with current progress.
     */
    private fun createNotification(progress: IndexingProgress): Notification {
        val intent =
            Intent(this, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(progress is IndexingProgress.InProgress || progress is IndexingProgress.Idle)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setAutoCancel(progress is IndexingProgress.Completed || progress is IndexingProgress.Error)

        when (progress) {
            is IndexingProgress.Idle -> {
                builder
                    .setContentTitle("Индексация форумов")
                    .setContentText("Подготовка к индексации...")
                    .setProgress(0, 0, true) // Indeterminate
            }
            is IndexingProgress.InProgress -> {
                val progressPercent = (progress.progress * 100).toInt()
                builder
                    .setContentTitle("Индексация форумов")
                    .setContentText(
                        "Форум ${progress.currentForumIndex + 1}/${progress.totalForums}: ${progress.currentForum}\n" +
                            "Страница ${progress.currentPage + 1}, тем: ${progress.topicsIndexed}",
                    ).setProgress(100, progressPercent, false)
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(
                                "Индексируем форум: ${progress.currentForum}\n" +
                                    "Форум ${progress.currentForumIndex + 1} из ${progress.totalForums}\n" +
                                    "Страница ${progress.currentPage + 1}\n" +
                                    "Проиндексировано: ${progress.topicsIndexed} тем\n" +
                                    "Прогресс: $progressPercent%",
                            ),
                    )
            }
            is IndexingProgress.Completed -> {
                builder
                    .setContentTitle("Индексация завершена")
                    .setContentText("Проиндексировано: ${progress.totalTopics} тем за ${progress.durationMs / 1000} сек")
                    .setProgress(0, 0, false)
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(
                                "Индексация завершена успешно!\n" +
                                    "Проиндексировано: ${progress.totalTopics} тем\n" +
                                    "Время выполнения: ${progress.durationMs / 1000} секунд",
                            ),
                    )
            }
            is IndexingProgress.Error -> {
                builder
                    .setContentTitle("Ошибка индексации")
                    .setContentText(progress.message)
                    .setProgress(0, 0, false)
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(progress.message),
                    )
            }
        }

        return builder.build()
    }

    /**
     * Updates notification with current progress.
     */
    private fun updateNotification(progress: IndexingProgress) {
        val notification = createNotification(progress)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
