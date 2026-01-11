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
public class IndexingForegroundService : Service() {
    @Inject
    lateinit var forumIndexer: ForumIndexer

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var indexingJob: Job? = null
    private var currentProgress: IndexingProgress = IndexingProgress.Idle
    private var startTime: Long = 0L

    public companion object {
        private const val TAG = "IndexingForegroundService"
        private const val CHANNEL_ID = "jabook_indexing"
        private const val CHANNEL_NAME = "Индексация форумов"
        private const val NOTIFICATION_ID = 100
        private const val AUTO_DISMISS_DELAY_MS = 5000L // 5 seconds

        public const val ACTION_START: String = "com.jabook.app.jabook.indexing.START"
        public const val ACTION_STOP: String = "com.jabook.app.jabook.indexing.STOP"
        public const val ACTION_UPDATE_PROGRESS: String = "com.jabook.app.jabook.indexing.UPDATE_PROGRESS"
        public const val EXTRA_PROGRESS: String = "progress"
        @Volatile
        private var instance: IndexingForegroundService? = null

        public fun getInstance(): IndexingForegroundService? = instance

        /**
         * Start the indexing service.
         */
        public fun start(context: Context) {
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
        public fun stop(context: Context) {
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
        Log.i(TAG, "=== IndexingForegroundService onCreate() ===")
        createNotificationChannel()
        Log.i(TAG, "Service created and notification channel initialized")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.i(TAG, "onStartCommand() called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "=== ACTION_START: Starting indexing ===")
                startForegroundWithNotification()
                startIndexing()
            }
            ACTION_STOP -> {
                Log.i(TAG, "=== ACTION_STOP: Stopping service ===")
                stopSelf()
            }
            ACTION_UPDATE_PROGRESS -> {
                Log.d(TAG, "ACTION_UPDATE_PROGRESS received")
                // Progress update from external source (if needed)
                // Note: IndexingProgress is not Serializable, so we update from service's own state
                updateNotification(currentProgress)
            }
            null -> {
                Log.w(TAG, "⚠️ onStartCommand called with null action")
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown action: ${intent.action}")
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
            Log.i(TAG, "Creating notification channel: $CHANNEL_ID")
            public val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW, // Keep LOW to avoid sound/vibration, but make visible
                ).apply {
                    description = "Уведомления о процессе индексации форумов"
                    setShowBadge(true)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Make visible on lock screen
                }
            public val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            // Verify channel was created
            public val createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (createdChannel != null) {
                Log.i(TAG, "✅ Notification channel created successfully: $CHANNEL_ID")
                Log.i(TAG, "Channel importance: ${createdChannel.importance}")
                Log.i(TAG, "Channel can show badge: ${createdChannel.canShowBadge()}")
            } else {
                Log.e(TAG, "❌ Failed to create notification channel!")
            }
        } else {
            Log.i(TAG, "Android < O, notification channel not needed")
        }
    }

    /**
     * Starts foreground service with initial notification.
     */
    private fun startForegroundWithNotification() {
        Log.i(TAG, "Starting foreground service with notification ID: $NOTIFICATION_ID")
        try {
            public val notification = createNotification(IndexingProgress.Idle)
            startForeground(NOTIFICATION_ID, notification)
            Log.i(TAG, "✅ Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
            throw e
        }
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
                    public val currentAuthStatus = authRepository.authStatus.first()
                    public val isAuthenticated = currentAuthStatus is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated

                    public val hasValidUsername =
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
                    startTime = System.currentTimeMillis()
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
        Log.d(TAG, "Creating notification for progress: ${progress::class.simpleName}")
        public val intent =
            Intent(this, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        public val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        Log.d(TAG, "PendingIntent created for notification")

        public val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(progress is IndexingProgress.InProgress || progress is IndexingProgress.Idle)
                .setOnlyAlertOnce(true)
                .setPriority(
                    if (progress is IndexingProgress.InProgress) {
                        NotificationCompat.PRIORITY_DEFAULT // More visible during indexing
                    } else {
                        NotificationCompat.PRIORITY_LOW
                    },
                ).setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setAutoCancel(progress is IndexingProgress.Completed || progress is IndexingProgress.Error)

        when (progress) {
            is IndexingProgress.Idle -> {
                builder
                    .setContentTitle("Индексация форумов")
                    .setContentText("Подготовка к индексации...")
                    .setProgress(0, 0, true) // Indeterminate
            }
            is IndexingProgress.InProgress -> {
                public val progressPercent = (progress.progress * 100).toInt()
                builder
                    .setContentTitle("Индексация форумов")
                    .setContentText(
                        "Форум ${progress.currentForumIndex + 1}/${progress.totalForums}: ${progress.currentForum}",
                    ).setProgress(100, progressPercent, false)
                    .setUsesChronometer(true)
                    .setWhen(startTime)
                    .setSubText("${progress.topicsIndexed} тем")
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(
                                "Форум: ${progress.currentForum}\n" +
                                    "Прогресс: $progressPercent% (${progress.currentForumIndex + 1}/${progress.totalForums})\n" +
                                    "Страница: ${progress.currentPage + 1}\n" +
                                    "Найдено тем: ${progress.topicsIndexed}",
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

        public val notification = builder.build()
        Log.d(TAG, "Notification built successfully")
        return notification
    }

    /**
     * Updates notification with current progress.
     */
    private fun updateNotification(progress: IndexingProgress) {
        try {
            Log.d(TAG, "Updating notification for: ${progress::class.simpleName}")
            public val notification = createNotification(progress)
            public val notificationManager = getSystemService(NotificationManager::class.java)

            // Check if notifications are enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!notificationManager.areNotificationsEnabled()) {
                    Log.w(TAG, "⚠️ Notifications are DISABLED by user in system settings!")
                }
            }

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "✅ Notification posted successfully: ${progress::class.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update notification", e)
        }
    }
}
