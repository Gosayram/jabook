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

package com.jabook.app.jabook.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jabook.app.jabook.compose.ComposeMainActivity
import com.jabook.app.jabook.torrent.TorrentManager
import com.jabook.app.jabook.torrent.data.TorrentState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.NotificationManager as AndroidNotificationManager

/**
 * Foreground service for managing torrent downloads.
 *
 * This service:
 * - Keeps torrent downloads alive in the background
 * - Integrates with TorrentManager for torrent operations
 * - Shows notifications with download progress
 * - Handles magnet links from deep links
 */
@AndroidEntryPoint
class DownloadForegroundService : Service() {
    companion object {
        private const val TAG = "DownloadForegroundService"
        private const val CHANNEL_ID = "jabook_downloads"
        private const val CHANNEL_NAME = "Downloads"
        private const val NOTIFICATION_ID = 2
        private const val UPDATE_INTERVAL_MS = 1000L // Update notification every second

        const val ACTION_START = "com.jabook.app.jabook.download.START"
        const val ACTION_STOP = "com.jabook.app.jabook.download.STOP"
        const val ACTION_ADD_MAGNET = "com.jabook.app.jabook.download.ADD_MAGNET"
        const val ACTION_PAUSE = "com.jabook.app.jabook.download.PAUSE"
        const val ACTION_RESUME = "com.jabook.app.jabook.download.RESUME"
        const val ACTION_REMOVE = "com.jabook.app.jabook.download.REMOVE"

        const val EXTRA_MAGNET_URI = "magnet_uri"
        const val EXTRA_SAVE_PATH = "save_path"
        const val EXTRA_INFO_HASH = "info_hash"

        @Volatile
        private var instance: DownloadForegroundService? = null

        fun getInstance(): DownloadForegroundService? = instance

        /**
         * Start the download service and add a magnet link.
         */
        fun startDownload(
            context: Context,
            magnetUri: String,
            savePath: String,
        ) {
            val intent =
                Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_ADD_MAGNET
                    putExtra(EXTRA_MAGNET_URI, magnetUri)
                    putExtra(EXTRA_SAVE_PATH, savePath)
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    @Inject
    lateinit var torrentManager: TorrentManager

    private var notificationManager: AndroidNotificationManager? = null
    private var isServiceRunning = false

    // Coroutine scope for background work
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Active downloads: infoHash -> magnetUri
    private val activeDownloads = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        createNotificationChannel()

        // Initialize TorrentManager
        torrentManager.initialize()

        // Start notification updates
        startNotificationUpdates()

        // CRITICAL FIX: Start foreground immediately for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val notification = createDownloadNotification("Initializing downloads...", 0f)
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "startForeground() called in onCreate()")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to call startForeground() in onCreate()", e)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Ensure foreground for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isServiceRunning) {
            try {
                val notification = createDownloadNotification("Downloads ready", 0f)
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "startForeground() called in onStartCommand()")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ensure startForeground()", e)
            }
        }

        when (intent?.action) {
            ACTION_START -> {
                isServiceRunning = true
                Log.d(TAG, "Download service started")
            }
            ACTION_ADD_MAGNET -> {
                val magnetUri = intent.getStringExtra(EXTRA_MAGNET_URI)
                val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)

                if (magnetUri != null && savePath != null) {
                    addMagnetLink(magnetUri, savePath)
                } else {
                    Log.w(TAG, "Invalid magnet link or save path")
                }
            }
            ACTION_PAUSE -> {
                val infoHash = intent.getStringExtra(EXTRA_INFO_HASH)
                infoHash?.let { pauseDownload(it) }
            }
            ACTION_RESUME -> {
                val infoHash = intent.getStringExtra(EXTRA_INFO_HASH)
                infoHash?.let { resumeDownload(it) }
            }
            ACTION_REMOVE -> {
                val infoHash = intent.getStringExtra(EXTRA_INFO_HASH)
                val deleteFiles = intent.getBooleanExtra("delete_files", false)
                infoHash?.let { removeDownload(it, deleteFiles) }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        isServiceRunning = false

        // Shutdown TorrentManager
        torrentManager.shutdown()

        // Cancel coroutine scope
        serviceScope.cancel()

        super.onDestroy()
        Log.d(TAG, "Download service destroyed")
    }

    /**
     * Add a magnet link and start downloading.
     */
    private fun addMagnetLink(
        magnetUri: String,
        savePath: String,
    ) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Adding magnet link: $magnetUri")
                val infoHash = torrentManager.addMagnetLink(magnetUri, savePath, sequential = true)
                activeDownloads[infoHash] = magnetUri
                Log.i(TAG, "Started download: $infoHash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add magnet link", e)
            }
        }
    }

    /**
     * Pause a download.
     */
    private fun pauseDownload(infoHash: String) {
        serviceScope.launch {
            try {
                torrentManager.pauseDownload(infoHash)
                Log.d(TAG, "Paused download: $infoHash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause download", e)
            }
        }
    }

    /**
     * Resume a download.
     */
    private fun resumeDownload(infoHash: String) {
        serviceScope.launch {
            try {
                torrentManager.resumeDownload(infoHash)
                Log.d(TAG, "Resumed download: $infoHash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume download", e)
            }
        }
    }

    /**
     * Remove a download.
     */
    private fun removeDownload(
        infoHash: String,
        deleteFiles: Boolean,
    ) {
        serviceScope.launch {
            try {
                torrentManager.removeDownload(infoHash, deleteFiles)
                activeDownloads.remove(infoHash)
                Log.d(TAG, "Removed download: $infoHash")

                // Stop service if no active downloads
                if (activeDownloads.isEmpty()) {
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove download", e)
            }
        }
    }

    /**
     * Start periodic notification updates.
     */
    private fun startNotificationUpdates() {
        torrentManager.downloads
            .onEach { downloads ->
                if (downloads.isNotEmpty()) {
                    // Update notification with first active download
                    val firstDownload = downloads.values.first()
                    val notification =
                        when (firstDownload.state) {
                            TorrentState.DOWNLOADING -> {
                                createDownloadNotification(
                                    "Downloading (${downloads.size} active)",
                                    firstDownload.percentage,
                                )
                            }
                            TorrentState.FINISHED -> {
                                createDownloadNotification("Download complete", 100f)
                            }
                            else -> {
                                createDownloadNotification(
                                    firstDownload.state.name,
                                    firstDownload.percentage,
                                )
                            }
                        }
                    notificationManager?.notify(NOTIFICATION_ID, notification)
                } else {
                    // No active downloads, show idle notification
                    val notification = createDownloadNotification("No active downloads", 0f)
                    notificationManager?.notify(NOTIFICATION_ID, notification)
                }
            }.launchIn(serviceScope)
    }

    /**
     * Create notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    AndroidNotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Download progress notifications"
                    setShowBadge(true)
                    enableLights(false)
                    enableVibration(false)
                }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Create download notification with progress.
     */
    private fun createDownloadNotification(
        title: String,
        progress: Float,
    ): Notification {
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

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("${progress.toInt()}% complete")
            .setProgress(100, progress.toInt(), false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
