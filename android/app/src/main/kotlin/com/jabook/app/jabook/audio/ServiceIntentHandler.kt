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

package com.jabook.app.jabook.audio

import android.app.Service
import android.content.Intent
import com.jabook.app.jabook.widget.PlayerWidgetProvider

/**
 * Handles intents sent to AudioPlayerService via onStartCommand.
 * Processes notification actions, widget actions, and timer events.
 */
internal class ServiceIntentHandler(
    private val service: AudioPlayerService,
    private val widgetActionDeduplicator: WidgetActionDeduplicator = WidgetActionDeduplicator(),
) {
    public fun handleStartCommand(
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

        if (action != null && WidgetActionDeduplicator.isWidgetAction(action)) {
            val widgetId =
                intent.getIntExtra(
                    PlayerWidgetProvider.EXTRA_APP_WIDGET_ID,
                    WidgetActionDeduplicator.UNKNOWN_WIDGET_ID,
                )
            val shouldHandle = widgetActionDeduplicator.shouldHandle(action, widgetId)
            if (!shouldHandle) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Skipped duplicate widget action=$action for widgetId=$widgetId within dedupe window",
                )
                return true
            }
        }

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
                    if (service.isFullyInitializedFlag &&
                        (service.mediaSession != null || service.mediaLibrarySession != null)
                    ) {
                        android.util.Log.i(
                            "AudioPlayerService",
                            "Exit app requested by sleep timer, service is initialized, proceeding",
                        )
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
                // Widget actions
                "com.jabook.app.jabook.WIDGET_PLAY_PAUSE" -> {
                    android.util.Log.d("AudioPlayerService", "Widget play/pause action")
                    val player = service.getActivePlayer()
                    if (player.isPlaying) {
                        service.pause()
                    } else {
                        service.play()
                    }
                    // Update widget after state change
                    com.jabook.app.jabook.widget.PlayerWidgetProvider
                        .requestUpdate(service)
                    true
                }
                "com.jabook.app.jabook.WIDGET_NEXT" -> {
                    android.util.Log.d("AudioPlayerService", "Widget next action")
                    service.next()
                    // Update widget after state change
                    com.jabook.app.jabook.widget.PlayerWidgetProvider
                        .requestUpdate(service)
                    true
                }
                "com.jabook.app.jabook.WIDGET_PREVIOUS" -> {
                    android.util.Log.d("AudioPlayerService", "Widget previous action")
                    service.previous()
                    // Update widget after state change
                    com.jabook.app.jabook.widget.PlayerWidgetProvider
                        .requestUpdate(service)
                    true
                }
                "com.jabook.app.jabook.WIDGET_REPEAT" -> {
                    android.util.Log.d("AudioPlayerService", "Widget repeat action")
                    val player = service.getActivePlayer()
                    val currentRepeatMode = player.repeatMode
                    val newRepeatMode =
                        when (currentRepeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_OFF
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                    service.setRepeatMode(newRepeatMode)
                    // Update widget after state change
                    com.jabook.app.jabook.widget.PlayerWidgetProvider
                        .requestUpdate(service)
                    true
                }
                "com.jabook.app.jabook.WIDGET_SPEED" -> {
                    android.util.Log.d("AudioPlayerService", "Widget speed action")
                    // Cycle through speeds: 0.5x -> 0.75x -> 1.0x -> 1.25x -> 1.5x -> 2.0x -> 0.5x
                    val player = service.getActivePlayer()
                    val currentSpeed = player.playbackParameters.speed
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    val currentIndex = speeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
                    val nextIndex = if (currentIndex >= 0 && currentIndex < speeds.size - 1) currentIndex + 1 else 0
                    val newSpeed = speeds[nextIndex]
                    service.setSpeed(newSpeed)
                    // Update widget after state change
                    com.jabook.app.jabook.widget.PlayerWidgetProvider
                        .requestUpdate(service)
                    true
                }
                "com.jabook.app.jabook.WIDGET_TIMER" -> {
                    android.util.Log.d("AudioPlayerService", "Widget timer action")
                    // Cycle through timer options: off -> 15min -> 30min -> 45min -> 60min -> off
                    val remainingSeconds = service.getSleepTimerRemainingSeconds()
                    val currentTimerMinutes = if (remainingSeconds != null) remainingSeconds / 60 else 0
                    val timerOptions = listOf(0, 15, 30, 45, 60)
                    val currentIndex = timerOptions.indexOf(currentTimerMinutes)
                    val nextIndex = if (currentIndex >= 0 && currentIndex < timerOptions.size - 1) currentIndex + 1 else 0
                    val newTimerMinutes = timerOptions[nextIndex]

                    if (newTimerMinutes > 0) {
                        service.setSleepTimerMinutes(newTimerMinutes)
                    } else {
                        service.cancelSleepTimer()
                    }
                    // Update widget after state change
                    com.jabook.app.jabook.widget.PlayerWidgetProvider
                        .requestUpdate(service)
                    true
                }
                else -> false
            }

        return handled
    }
}
