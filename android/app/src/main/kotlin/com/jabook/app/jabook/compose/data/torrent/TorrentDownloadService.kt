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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.ComposeMainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Foreground service for torrent downloads
 */
@AndroidEntryPoint
class TorrentDownloadService : Service() {
    @Inject
    lateinit var torrentManager: TorrentManager

    @Inject
    lateinit var notificationManager: TorrentNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        // Initialize torrent manager
        torrentManager.initialize()

        // Observe downloads for notification updates
        observeDownloads()

        // Start foreground
        startForeground()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.i(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                // Already started in onCreate
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")

        serviceScope.cancel()
        releaseWakeLock()

        // Don't stop session - it should persist
        // torrentManager.shutdown()
    }

    private fun startForeground() {
        createNotificationChannel()

        val notification = createForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }

        acquireWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID_DOWNLOADS,
                    getString(R.string.torrent_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.torrent_downloads_channel_description)
                    setShowBadge(false)
                }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent =
            Intent(this, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.torrent_service_running))
            .setContentText(getString(R.string.tap_to_manage_downloads))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun observeDownloads() {
        torrentManager.downloadsFlow
            .onEach { downloads ->
                updateNotifications(downloads)

                // Auto-stop service if no downloads
                if (downloads.isEmpty()) {
                    Log.i(TAG, "No active downloads, stopping service")
                    stopSelf()
                }
            }.launchIn(serviceScope)
    }

    private fun updateNotifications(downloads: Map<String, TorrentDownload>) {
        if (downloads.isEmpty()) return

        // Update summary notification
        val summaryNotification =
            notificationManager.createSummaryNotification(
                downloads.values.toList(),
            )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_SUMMARY, summaryNotification)

        // Update individual notifications
        downloads.forEach { (hash, download) ->
            val notification = notificationManager.createProgressNotification(download)
            nm.notify(hash.hashCode(), notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Jabook::TorrentDownload",
                )
        }

        wakeLock?.takeIf { !it.isHeld }?.acquire(10 * 60 * 1000L) // 10 minutes
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "TorrentDownloadService"

        const val ACTION_START = "org.jabook.ACTION_START_DOWNLOAD_SERVICE"
        const val ACTION_STOP = "org.jabook.ACTION_STOP_DOWNLOAD_SERVICE"

        const val CHANNEL_ID_DOWNLOADS = "torrent_downloads"
        private const val NOTIFICATION_ID_FOREGROUND = 1000
        private const val NOTIFICATION_ID_SUMMARY = 1001
    }
}
