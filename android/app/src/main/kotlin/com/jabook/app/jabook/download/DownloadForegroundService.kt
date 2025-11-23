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
import android.app.NotificationManager as AndroidNotificationManager
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
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    // Create minimal notification only for foreground service
                    // Individual download notifications are handled by DownloadNotificationService
                    val minimalNotification = createMinimalNotification()
                    startForeground(NOTIFICATION_ID, minimalNotification)
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
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates minimal notification for foreground service.
     * 
     * This notification is only used to keep the service alive in background.
     * Individual download notifications are handled by DownloadNotificationService.
     */
    private fun createMinimalNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("JaBook Downloads")
            .setContentText("Downloads in progress")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

