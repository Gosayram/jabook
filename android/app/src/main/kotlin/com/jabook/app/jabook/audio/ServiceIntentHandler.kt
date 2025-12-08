package com.jabook.app.jabook.audio

import android.app.Service
import android.content.Intent

/**
 * Handles intents sent to AudioPlayerService via onStartCommand.
 * Processes notification actions, widget actions, and timer events.
 */
internal class ServiceIntentHandler(
    private val service: AudioPlayerService,
) {
    fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Ensure service is running in foreground (critical for Android 8.0+)
        // This is a safety check in case startForeground was not called in onCreate
        // or if system killed the notification but service is still referenced
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                // Check if we have a valid notification
                if (service.notificationHelper != null) {
                    // We don't want to recreate notification if it exists, but we must ensure startForeground is active
                    // However, calling startForeground again with same ID is safe and updates notification
                    // But we should avoid expensive operations if not needed.
                    // AudioPlayerServiceInitializer checks this in onCreate.
                    // Here we just handle edge cases.
                } else {
                    android.util.Log.w(
                        "AudioPlayerService",
                        "NotificationHelper is null in onStartCommand - this suggests initialization issue",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "AudioPlayerService",
                    "Failed to ensure startForeground() in onStartCommand",
                    e,
                )
                // Try fallback notification
                try {
                    val fallbackNotification =
                        service.notificationHelper?.createFallbackNotification()
                            ?: throw IllegalStateException("NotificationHelper not initialized")
                    service.startForeground(NotificationHelper.NOTIFICATION_ID, fallbackNotification)
                    android.util.Log.w(
                        "AudioPlayerService",
                        "Used fallback notification in onStartCommand after exception",
                    )
                } catch (e2: Exception) {
                    android.util.Log.e(
                        "AudioPlayerService",
                        "CRITICAL: Failed to call startForeground() in onStartCommand - service may crash",
                        e2,
                    )
                }
            }
        }

        // Handle actions from notification and timer
        val action = intent?.action
        android.util.Log.d(
            "AudioPlayerService",
            "onStartCommand called with action: $action, intent: $intent, flags: $flags, startId: $startId",
        )

        when (action) {
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PLAY -> {
                android.util.Log.i(
                    "AudioPlayerService",
                    "ACTION_PLAY received from notification. Current state: playWhenReady=${service.getActivePlayer().playWhenReady}, " +
                        "playbackState=${service.getActivePlayer().playbackState}",
                )
                try {
                    service.play()
                    android.util.Log.d("AudioPlayerService", "play() called successfully")
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to execute ACTION_PLAY", e)
                }
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PAUSE -> {
                android.util.Log.i(
                    "AudioPlayerService",
                    "ACTION_PAUSE received from notification. Current state: playWhenReady=${service.getActivePlayer().playWhenReady}, " +
                        "playbackState=${service.getActivePlayer().playbackState}",
                )
                try {
                    service.pause()
                    android.util.Log.d("AudioPlayerService", "pause() called successfully")
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to execute ACTION_PAUSE", e)
                }
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_NEXT -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: NEXT, resetting inactivity timer")
                service.next()
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PREVIOUS -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: PREVIOUS, resetting inactivity timer")
                service.previous()
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_REWIND -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: REWIND, resetting inactivity timer")
                val rewindSeconds = service.mediaSessionManager?.getRewindDuration()?.toInt() ?: 15
                service.rewind(rewindSeconds)
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_FORWARD -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: FORWARD, resetting inactivity timer")
                val forwardSeconds = service.mediaSessionManager?.getForwardDuration()?.toInt() ?: 30
                service.forward(forwardSeconds)
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_STOP -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: STOP")
                service.stopAndCleanup()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    service.stopForeground(true)
                }

                service.stopSelf()
            }

            // Handle timer actions
            PlaybackTimer.ACTION_TIMER_EXPIRED -> {
                // Timer expired - playback should already be paused by PlaybackTimer
                android.util.Log.d("AudioPlayerService", "Timer expired, playback paused")
            }
            InactivityTimer.ACTION_INACTIVITY_TIMER_EXPIRED -> {
                // Inactivity timer expired - unload player
                android.util.Log.i("AudioPlayerService", "Inactivity timer expired, unloading player")
                service.unloadPlayerDueToInactivity()
            }
            AudioPlayerService.ACTION_EXIT_APP -> {
                // Sleep timer expired - stop service and exit app
                // Only process if service is fully initialized to avoid stopping during initialization
                android.util.Log.d(
                    "AudioPlayerService",
                    "ACTION_EXIT_APP received: isFullyInitialized=${service.isFullyInitializedFlag}, mediaSession=${service.mediaSession != null}",
                )
                if (service.isFullyInitializedFlag && service.mediaSession != null) {
                    android.util.Log.i("AudioPlayerService", "Exit app requested by sleep timer, service is initialized, proceeding")
                    try {
                        service.stopAndCleanup()

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            service.stopForeground(true)
                        }

                        service.stopSelf()
                        // Send broadcast to finish activity
                        val exitIntent =
                            Intent("com.jabook.app.jabook.EXIT_APP").apply {
                                setPackage(service.packageName) // Set package for explicit broadcast
                            }
                        android.util.Log.d("AudioPlayerService", "Sending EXIT_APP broadcast")
                        service.sendBroadcast(exitIntent)
                        android.util.Log.i("AudioPlayerService", "EXIT_APP broadcast sent successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Error during exit app cleanup", e)
                        // Try to stop service anyway
                        try {
                            service.stopSelf()
                        } catch (e2: Exception) {
                            android.util.Log.e("AudioPlayerService", "Failed to stop service", e2)
                        }
                    }
                } else {
                    android.util.Log.w(
                        "AudioPlayerService",
                        "Exit app requested but service not initialized yet (isFullyInitialized=${service.isFullyInitializedFlag}, mediaSession=${service.mediaSession != null}), ignoring to prevent white screen",
                    )
                }
            }
        }

        // Return START_STICKY to restart service if killed by system
        return Service.START_STICKY
    }
}
