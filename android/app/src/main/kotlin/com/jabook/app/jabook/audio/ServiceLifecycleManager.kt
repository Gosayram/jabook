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

import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils

/**
 * Manages Service lifecycle events and cleanup logic.
 */
internal class ServiceLifecycleManager(
    private val service: AudioPlayerService,
) {
    public fun onTaskRemoved() {
        LogUtils.i("AudioPlayerService", "onTaskRemoved called")

        try {
            // Best-effort immediate save to avoid losing progress when app is swiped away.
            service.saveCurrentPosition()
            service.saveCurrentPositionSynchronously()
        } catch (e: Exception) {
            LogUtils.w("AudioPlayerService", "Failed to save position in onTaskRemoved", e)
            CrashDiagnostics.reportNonFatal(
                tag = "service_on_task_removed_save_failed",
                throwable = e,
                attributes = mapOf("service" to "AudioPlayerService"),
            )
        }

        try {
            // If player is not playing, stop the service
            val player = service.getActivePlayer()
            if (!player.playWhenReady ||
                player.playbackState == Player.STATE_IDLE ||
                player.playbackState == Player.STATE_ENDED
            ) {
                LogUtils.i("AudioPlayerService", "Stopping service onTaskRemoved because not playing")
                finishListeningSession(reason = "task_removed")
                // CRITICAL: Explicitly cancel notification to prevent it from getting stuck
                // This mimics the behavior of Rhythm and other well-behaved players
                ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
                service.stopSelf()
            } else {
                LogUtils.i("AudioPlayerService", "Ignoring onTaskRemoved because playing")
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Error in onTaskRemoved", e)
            CrashDiagnostics.reportNonFatal(
                tag = "service_on_task_removed_failed",
                throwable = e,
                attributes = mapOf("service" to "AudioPlayerService"),
            )
            // Safety: stop service if we can't check player state
            finishListeningSession(reason = "task_removed")
            service.stopSelf()
        }
    }

    public fun onDestroy() {
        LogUtils.d("AudioPlayerService", "onDestroy called")

        // CRITICAL: Save position before destroying service
        // This ensures position is saved when:
        // - User closes app
        // - Device shuts down
        // - System kills service
        // - Any other scenario where service is destroyed
        try {
            LogUtils.d("AudioPlayerService", "Saving position before service destruction")
            service.saveCurrentPosition()
            service.saveCurrentPositionSynchronously()
        } catch (e: Exception) {
            LogUtils.w("AudioPlayerService", "Failed to save position in onDestroy", e)
            CrashDiagnostics.reportNonFatal(
                tag = "service_on_destroy_save_failed",
                throwable = e,
                attributes = mapOf("service" to "AudioPlayerService"),
            )
        }

        finishListeningSession(reason = "on_destroy")
        // AudioServiceReleaseHandler owns all runtime resources. Keeping lifecycle
        // work to persistence avoids releasing the same Media3 objects twice.
    }

    public fun stopAndCleanup() {
        LogUtils.d("AudioPlayerService", "stopAndCleanup() called")

        finishListeningSession(reason = "stop_and_cleanup")

        // Clear duration cache to free memory
        service.durationManager.clearCache()

        val player = service.getActivePlayer()
        try {
            LogUtils.d("AudioPlayerService", "Stopping player and releasing resources")
            player.stop()
            player.clearMediaItems()
            service.playbackTimer?.stopTimer()
            service.inactivityTimer?.stopTimer()

            // Release MediaSession
            service.mediaSessionManager?.release()
            service.mediaSession = null

            // Inspired by Easybook: Properly cancel notification when stopping service
            try {
                val notificationManager =
                    service.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as? android.app.NotificationManager
                notificationManager?.cancel(NotificationHelper.NOTIFICATION_ID)
                LogUtils.d("AudioPlayerService", "Notification cancelled in stopAndCleanup")
            } catch (e: Exception) {
                LogUtils.w("AudioPlayerService", "Error cancelling notification", e)
            }

            // notificationManager removed - MediaSession handles notifications automatically
            // service.notificationManager = null

            LogUtils.d("AudioPlayerService", "Player stopped and resources released")
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to stop and cleanup", e)
            CrashDiagnostics.reportNonFatal(
                tag = "service_stop_and_cleanup_failed",
                throwable = e,
                attributes = mapOf("service" to "AudioPlayerService"),
            )
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Stop and cleanup execution")
        }
    }

    private fun finishListeningSession(reason: String) {
        try {
            service.finishListeningSessionIfActive(reason)
        } catch (e: Exception) {
            LogUtils.w("AudioPlayerService", "Failed to finish listening session reason=$reason", e)
            CrashDiagnostics.reportNonFatal(
                tag = "service_finish_listening_session_failed",
                throwable = e,
                attributes = mapOf("service" to "AudioPlayerService", "reason" to reason),
            )
        }
    }
}
