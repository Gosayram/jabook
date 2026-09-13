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

package com.jabook.app.jabook.compose.infrastructure.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Helper for creating and managing notifications.
 *
 * Creates notification channels and builds notifications
 * for downloads, playback, and other features.
 */
public object NotificationHelper {
    /**
     * Notification channel IDs.
     */
    public const val CHANNEL_DOWNLOADS: String = "downloads"
    public const val CHANNEL_PLAYER: String = "player"

    /**
     * Create notification channels.
     *
     * Should be called in Application.onCreate().
     *
     * @param context Application context
     */
    public fun createNotificationChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Downloads channel
        // Android 8+ freezes importance after first creation; changing it later requires
        // a NEW channel id (migration), not editing IMPORTANCE_LOW here.
        val downloadsChannel =
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(com.jabook.app.jabook.R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.jabook.app.jabook.R.string.notification_channel_downloads_desc)
                setShowBadge(false)
            }

        // Player channel (for media playback notifications)
        val playerChannel =
            NotificationChannel(
                CHANNEL_PLAYER,
                context.getString(com.jabook.app.jabook.R.string.notification_channel_player),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.jabook.app.jabook.R.string.notification_channel_player_desc)
                setShowBadge(false)
            }

        notificationManager.createNotificationChannels(
            listOf(downloadsChannel, playerChannel),
        )
    }

    /**
     * Create download progress notification.
     *
     * @param context Context
     * @param bookTitle Title of the book being downloaded
     * @param progress Download progress (0-100)
     * @return Notification
     */
    public fun createDownloadNotification(
        context: Context,
        bookTitle: String,
        progress: Int,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle(context.getString(com.jabook.app.jabook.R.string.downloading_book, bookTitle))
            .setContentText(context.getString(com.jabook.app.jabook.R.string.download_progress_percent, progress))
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
    public fun createDownloadCompleteNotification(
        context: Context,
        bookTitle: String,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle(context.getString(com.jabook.app.jabook.R.string.downloadComplete))
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
    public fun createDownloadFailedNotification(
        context: Context,
        bookTitle: String,
        error: String,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle(context.getString(com.jabook.app.jabook.R.string.downloadFailed, bookTitle))
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
}
