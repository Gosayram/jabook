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
import com.jabook.app.jabook.widget.WidgetObservabilityPolicy

/**
 * Handles intents sent to AudioPlayerService via onStartCommand.
 * Processes notification actions, widget actions, and timer events.
 */
internal class ServiceIntentHandler(
    private val service: AudioPlayerService,
    private val widgetActionDeduplicator: WidgetActionDeduplicator = WidgetActionDeduplicator(),
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() },
) {
    public fun handleStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Boolean {
        // Handle actions from notification and timer
        val action = intent?.action
        val widgetId =
            intent?.getIntExtra(
                PlayerWidgetProvider.EXTRA_APP_WIDGET_ID,
                WidgetObservabilityPolicy.UNKNOWN_WIDGET_ID,
            ) ?: WidgetObservabilityPolicy.UNKNOWN_WIDGET_ID
        if (action == null || !WidgetActionDeduplicator.isWidgetAction(action)) {
            android.util.Log.d(
                "AudioPlayerService",
                "handleStartCommand called with action: $action, intent: $intent, flags: $flags, startId: $startId",
            )
        }

        if (action != null && WidgetActionDeduplicator.isWidgetAction(action)) {
            val actionCreatedAtMs =
                intent.getLongExtra(
                    PlayerWidgetProvider.EXTRA_WIDGET_ACTION_CREATED_AT_MS,
                    0L,
                )
            if (WidgetActionStalenessPolicy.shouldIgnore(
                    actionCreatedAtMs = actionCreatedAtMs,
                    nowMs = nowMsProvider(),
                )
            ) {
                android.util.Log.d(
                    "AudioPlayerService",
                    WidgetObservabilityPolicy.serviceMessage(
                        event = "action_ignored_stale",
                        action = action,
                        widgetId = widgetId,
                        deduplicated = false,
                    ),
                )
                android.util.Log.d(
                    "AudioPlayerService",
                    WidgetObservabilityPolicy.serviceMessage(
                        event = "action_retry_requested",
                        action = action,
                        widgetId = widgetId,
                        deduplicated = false,
                    ),
                )
                PlayerWidgetProvider.requestUpdate(service)
                return true
            }

            val shouldHandle = widgetActionDeduplicator.shouldHandle(action, widgetId)
            if (!shouldHandle) {
                android.util.Log.d(
                    "AudioPlayerService",
                    WidgetObservabilityPolicy.serviceMessage(
                        event = "action_ignored_deduplicated",
                        action = action,
                        widgetId = widgetId,
                        deduplicated = true,
                    ),
                )
                return true
            }
            android.util.Log.d(
                "AudioPlayerService",
                WidgetObservabilityPolicy.serviceMessage(
                    event = "action_accepted",
                    action = action,
                    widgetId = widgetId,
                    deduplicated = false,
                ),
            )
        }

        val notifyWidgetUpdated: (String) -> Unit = { widgetAction ->
            PlayerWidgetProvider.requestUpdate(service)
            android.util.Log.d(
                "AudioPlayerService",
                WidgetObservabilityPolicy.serviceMessage(
                    event = "request_update_sent",
                    action = widgetAction,
                    widgetId = widgetId,
                ),
            )
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
                    notifyWidgetUpdated(action)
                    true
                }
                "com.jabook.app.jabook.WIDGET_NEXT" -> {
                    android.util.Log.d("AudioPlayerService", "Widget next action")
                    service.next()
                    notifyWidgetUpdated(action)
                    true
                }
                "com.jabook.app.jabook.WIDGET_PREVIOUS" -> {
                    android.util.Log.d("AudioPlayerService", "Widget previous action")
                    service.previous()
                    notifyWidgetUpdated(action)
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
                    notifyWidgetUpdated(action)
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
                    notifyWidgetUpdated(action)
                    true
                }
                "com.jabook.app.jabook.WIDGET_TIMER" -> {
                    android.util.Log.d("AudioPlayerService", "Widget timer action")
                    // Cycle through timer options: off -> 15min -> 30min -> 45min -> 60min -> off
                    val remainingSeconds = service.getSleepTimerRemainingSeconds()
                    val currentTimerMinutes = if (remainingSeconds != null) remainingSeconds / 60 else 0
                    val timerOptions = listOf(0, 15, 30, 45, 60)
                    val currentIndex = timerOptions.indexOf(currentTimerMinutes)
                    val nextIndex =
                        if (currentIndex >= 0 &&
                            currentIndex < timerOptions.size - 1
                        ) {
                            currentIndex + 1
                        } else {
                            0
                        }
                    val newTimerMinutes = timerOptions[nextIndex]

                    if (newTimerMinutes > 0) {
                        service.setSleepTimerMinutes(newTimerMinutes)
                    } else {
                        service.cancelSleepTimer()
                    }
                    notifyWidgetUpdated(action)
                    true
                }
                else -> false
            }

        return handled
    }
}

internal object WidgetActionStalenessPolicy {
    internal const val MAX_ACTION_AGE_MS: Long = 12L * 60L * 60L * 1000L // 12h

    internal fun shouldIgnore(
        actionCreatedAtMs: Long,
        nowMs: Long,
    ): Boolean {
        if (actionCreatedAtMs <= 0L) {
            return false
        }
        val ageMs = nowMs - actionCreatedAtMs
        if (ageMs < 0L) {
            return false
        }
        return ageMs > MAX_ACTION_AGE_MS
    }
}
