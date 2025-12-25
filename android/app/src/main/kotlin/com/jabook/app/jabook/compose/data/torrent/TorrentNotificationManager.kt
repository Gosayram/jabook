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

package com.jabook.app.jabook.compose.data.torrent

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.ComposeMainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages torrent download notifications
 */
@Singleton
class TorrentNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        /**
         * Create progress notification for individual download
         */
        fun createProgressNotification(download: TorrentDownload): Notification {
            val builder =
                NotificationCompat
                    .Builder(context, TorrentDownloadService.CHANNEL_ID_DOWNLOADS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(download.name)
                    .setContentText(getProgressText(download))
                    .setSubText("${download.completedFiles}/${download.totalFiles} files")
                    .setProgress(100, (download.progress * 100).toInt(), false)
                    .setOngoing(download.isActive)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setGroup(NOTIFICATION_GROUP_DOWNLOADS)
                    .setContentIntent(createOpenDownloadsIntent())

            // State-based styling
            when (download.state) {
                TorrentState.DOWNLOADING, TorrentState.STREAMING -> {
                    builder.setColor(Color.GREEN)
                }
                TorrentState.PAUSED -> {
                    builder.setColor(Color.YELLOW)
                }
                TorrentState.ERROR -> {
                    builder
                        .setColor(Color.RED)
                        .setContentText(download.errorMessage ?: "Error")
                }
                TorrentState.COMPLETED -> {
                    builder
                        .setColor(Color.BLUE)
                        .setProgress(0, 0, false)
                }
                else -> {}
            }

            return builder.build()
        }

        /**
         * Create summary notification for download group
         */
        fun createSummaryNotification(downloads: List<TorrentDownload>): Notification {
            val activeCount = downloads.count { it.isActive }

            val totalBytes = downloads.sumOf { it.totalSize }
            val downloadedBytes = downloads.sumOf { it.downloadedSize }
            val totalProgress =
                if (totalBytes > 0) {
                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
            val totalSpeed = downloads.sumOf { it.downloadSpeed }

            return NotificationCompat
                .Builder(context, TorrentDownloadService.CHANNEL_ID_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(
                    context.getString(R.string.downloading_n_torrents, activeCount),
                ).setContentText(
                    "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}",
                ).setSubText("↓ ${formatSpeed(totalSpeed)}")
                .setProgress(100, (totalProgress * 100).toInt(), false)
                .setOngoing(true)
                .setGroupSummary(true)
                .setGroup(NOTIFICATION_GROUP_DOWNLOADS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createOpenDownloadsIntent())
                .build()
        }

        /**
         * Update notification for download
         */
        fun updateNotification(download: TorrentDownload) {
            val notification = createProgressNotification(download)
            notificationManager.notify(download.hash.hashCode(), notification)
        }

        /**
         * Cancel notification
         */
        fun cancel(notificationId: Int) {
            notificationManager.cancel(notificationId)
        }

        /**
         * Update all notifications
         */
        fun updateAllNotifications() {
            // Will be called by service when needed
        }

        private fun getProgressText(download: TorrentDownload): String =
            buildString {
                append("↓ ${formatSpeed(download.downloadSpeed)}")
                append("  ↑ ${formatSpeed(download.uploadSpeed)}")
                if (download.eta > 0) {
                    append("  ${formatETA(download.eta)}")
                }
            }

        private fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

        private fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

        private fun formatETA(seconds: Long): String =
            when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60}m"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            }

        private fun createOpenDownloadsIntent(): PendingIntent {
            val intent =
                Intent(context, ComposeMainActivity::class.java).apply {
                    // TODO: Add navigation to downloads screen
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        companion object {
            const val NOTIFICATION_GROUP_DOWNLOADS = "torrent_downloads_group"
        }
    }
