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

package com.jabook.app.jabook.compose.infrastructure.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Helper for creating and managing notifications.
 *
 * Creates notification channels and builds notifications
 * for downloads, playback, and other features.
 */
object NotificationHelper {
    /**
     * Notification channel IDs.
     */
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_PLAYER = "player"

    /**
     * Create notification channels.
     *
     * Should be called in Application.onCreate().
     *
     * @param context Application context
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Downloads channel
            val downloadsChannel =
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Book download progress and completion"
                    setShowBadge(false)
                }

            // Player channel (for media playback notifications)
            val playerChannel =
                NotificationChannel(
                    CHANNEL_PLAYER,
                    "Audio Player",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Audio playback controls and status"
                    setShowBadge(false)
                }

            notificationManager.createNotificationChannels(
                listOf(downloadsChannel, playerChannel),
            )
        }
    }

    /**
     * Create download progress notification.
     *
     * @param context Context
     * @param bookTitle Title of the book being downloaded
     * @param progress Download progress (0-100)
     * @return Notification
     */
    fun createDownloadNotification(
        context: Context,
        bookTitle: String,
        progress: Int,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle("Downloading: $bookTitle")
            .setContentText("$progress% complete")
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /**
     * Create download complete notification.
     *
     * @param context Context
     * @param bookTitle Title of the downloaded book
     * @return Notification
     */
    fun createDownloadCompleteNotification(
        context: Context,
        bookTitle: String,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle("Download complete")
            .setContentText(bookTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

    /**
     * Create download failed notification.
     *
     * @param context Context
     * @param bookTitle Title of the book
     * @param error Error message
     * @return Notification
     */
    fun createDownloadFailedNotification(
        context: Context,
        bookTitle: String,
        error: String,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle("Download failed: $bookTitle")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
}
