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
    fun handleStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Boolean {
        // Handle actions from notification and timer
        val action = intent?.action
        android.util.Log.d(
            "AudioPlayerService",
            "handleStartCommand called with action: $action, intent: $intent, flags: $flags, startId: $startId",
        )

        val handled =
            when (action) {
                // Handle timer actions
                PlaybackTimer.ACTION_TIMER_EXPIRED -> {
                    // Timer expired - playback should already be paused by PlaybackTimer
                    android.util.Log.d("AudioPlayerService", "Timer expired, playback paused")
                    true
                }
                InactivityTimer.ACTION_INACTIVITY_TIMER_EXPIRED -> {
                    // Inactivity timer expired - unload player
                    android.util.Log.i("AudioPlayerService", "Inactivity timer expired, unloading player")
                    service.unloadPlayerDueToInactivity()
                    true
                }
                AudioPlayerService.ACTION_EXIT_APP -> {
                    // Sleep timer expired - stop service and exit app
                    // Only process if service is fully initialized to avoid stopping during initialization
                    android.util.Log.d(
                        "AudioPlayerService",
                        "ACTION_EXIT_APP received: isFullyInitialized=${service.isFullyInitializedFlag}, mediaSession=${service.mediaSession != null}",
                    )
                    if (service.isFullyInitializedFlag && (service.mediaSession != null || service.mediaLibrarySession != null)) {
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
                            "Exit app requested but service not initialized yet (isFullyInitialized=${service.isFullyInitializedFlag}), ignoring to prevent white screen",
                        )
                    }
                    true
                }
                else -> false
            }

        return handled
    }
}
