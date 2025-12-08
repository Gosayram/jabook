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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.jabook.app.jabook.MainActivity
import android.app.NotificationManager as AndroidNotificationManager

/**
 * Foreground service for managing torrent downloads.
 *
 * This service keeps the download process alive when the app is in the background,
 * ensuring continuous download progress. It displays a notification with download progress.
 */
class DownloadForegroundService : Service() {
    private var notificationManager: AndroidNotificationManager? = null
    private var updateHandler: Handler? = null
    private var updateRunnable: Runnable? = null
    private var isServiceRunning = false

    companion object {
        private const val CHANNEL_ID = "jabook_downloads"
        private const val CHANNEL_NAME = "JaBook Downloads"
        private const val NOTIFICATION_ID = 2

        const val ACTION_START = "com.jabook.app.jabook.download.START"
        const val ACTION_STOP = "com.jabook.app.jabook.download.STOP"
        const val ACTION_UPDATE_PROGRESS = "com.jabook.app.jabook.download.UPDATE_PROGRESS"

        @Volatile
        private var instance: DownloadForegroundService? = null

        fun getInstance(): DownloadForegroundService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        updateHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()

        // CRITICAL FIX for Android 11+ and 14+: Call startForeground() immediately if possible
        // Android 14+ requires startForeground() within 5 seconds or service will be killed
        // Android 11+ on problematic devices (especially Xiaomi) also needs immediate startForeground()
        // This prevents crash: "Context.startForegroundService() did not then call Service.startForeground()"
        val needsImmediateStartForeground =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
                // Android 11+
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // Android 14+

        if (needsImmediateStartForeground) {
            try {
                // Create notification BEFORE calling startForeground() (required)
                val tempNotification = createMinimalNotification()
                // Note: createMinimalNotification() always returns non-null
                startForeground(NOTIFICATION_ID, tempNotification)
                android.util.Log.d(
                    "DownloadForegroundService",
                    "startForeground() called immediately in onCreate() for Android ${Build.VERSION.SDK_INT} (critical fix)",
                )
            } catch (e: Exception) {
                android.util.Log.w(
                    "DownloadForegroundService",
                    "Failed to call startForeground() in onCreate(), will try in onStartCommand",
                    e,
                )
                // Continue - will try again in onStartCommand
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // CRITICAL: For Android 11+ and 14+, ensure startForeground() is called
        // This is especially important for problematic devices (Xiaomi, etc.)
        // If onCreate() didn't call it (e.g., service was restarted), call it here
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            try {
                // Ensure notification is created and service is in foreground
                if (!isServiceRunning) {
                    val minimalNotification = createMinimalNotification()
                    // Note: createMinimalNotification() always returns non-null
                    startForeground(NOTIFICATION_ID, minimalNotification)
                    android.util.Log.d(
                        "DownloadForegroundService",
                        "startForeground() called in onStartCommand for Android ${Build.VERSION.SDK_INT}",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "DownloadForegroundService",
                    "Failed to ensure startForeground() in onStartCommand",
                    e,
                )
                // Continue - service might still work
            }
        }

        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    // Create minimal notification only for foreground service
                    // Individual download notifications are handled by DownloadNotificationService
                    val minimalNotification = createMinimalNotification()
                    // Note: createMinimalNotification() always returns non-null
                    try {
                        startForeground(NOTIFICATION_ID, minimalNotification)
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "DownloadForegroundService",
                            "Failed to start foreground service",
                            e,
                        )
                    }
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_UPDATE_PROGRESS -> {
                // Don't update notification here - individual notifications are handled by DownloadNotificationService
                // This service is only used to keep downloads alive in background
            }
        }

        // Return START_STICKY to restart service if killed by system
        // This is important for problematic devices that kill background services
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        isServiceRunning = false
        updateRunnable?.let { updateHandler?.removeCallbacks(it) }
        updateHandler = null
        super.onDestroy()
    }

    /**
     * Creates notification channel for Android O+.
     *
     * This channel is used for a silent foreground service notification.
     * Individual download notifications are handled by DownloadNotificationService.
     * This notification is completely hidden from the user to avoid duplicates.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    AndroidNotificationManager.IMPORTANCE_MIN, // MIN importance = silent, hidden from tray
                ).apply {
                    description = "Silent foreground service notification (completely hidden from user)"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                    // Additional settings to ensure notification is completely hidden
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
                        setShowBadge(false)
                        setBypassDnd(false)
                    }
                    // Lock screen visibility - hide completely
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Creates silent notification for foreground service.
     *
     * This notification is required for foreground service but is completely hidden from user.
     * It uses IMPORTANCE_MIN channel which makes it silent and not shown in tray.
     * Individual download notifications are handled by DownloadNotificationService.
     * This prevents duplicate notifications - only DownloadNotificationService shows user-visible notifications.
     */
    private fun createMinimalNotification(): Notification {
        val intent =
            Intent(this, MainActivity::class.java).apply {
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
            .setContentTitle("JaBook Downloads")
            .setContentText("Downloads in progress")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN) // MIN priority = silent, hidden
            .setSilent(true) // Explicitly mark as silent
            .setShowWhen(false) // Don't show timestamp
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen completely
            .setLocalOnly(true) // Don't show on connected devices
            .build()
    }
}
