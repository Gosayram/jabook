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

package com.jabook.app.jabook.audio

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.cancel

/**
 * Manages Service lifecycle events and cleanup logic.
 */
internal class ServiceLifecycleManager(
    private val service: AudioPlayerService,
) {
    fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.i("AudioPlayerService", "onTaskRemoved called")

        try {
            // If player is not playing, stop the service
            val player = service.getActivePlayer()
            if (!player.playWhenReady ||
                player.playbackState == Player.STATE_IDLE ||
                player.playbackState == Player.STATE_ENDED
            ) {
                android.util.Log.i("AudioPlayerService", "Stopping service onTaskRemoved because not playing")
                service.stopSelf()
            } else {
                android.util.Log.i("AudioPlayerService", "Ignoring onTaskRemoved because playing")
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Error in onTaskRemoved", e)
            // Safety: stop service if we can't check player state
            service.stopSelf()
        }
    }

    @OptIn(UnstableApi::class)
    fun onDestroy() {
        android.util.Log.d("AudioPlayerService", "onDestroy called")

        // Stop sleep timer check
        service.sleepTimerManager?.stopSleepTimerCheck()

        // Cancel coroutine scope (inspired by lissen-android)
        service.playerServiceScope.cancel()

        // Setting player to null ensures notification updates stop
        try {
            service.notificationManager?.let { notifManager ->
                android.util.Log.d("AudioPlayerService", "Cleaning up notification manager")
                // Cancel any pending notifications
                val androidNotifManager =
                    service.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as? android.app.NotificationManager
                androidNotifManager?.cancel(NotificationHelper.NOTIFICATION_ID)
                android.util.Log.d("AudioPlayerService", "Notification cancelled")
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Error cleaning up notifications", e)
        }

        // IMPORTANT: Do NOT call exoPlayer.release() - it's a singleton via Hilt!
        // Hilt automatically manages the lifecycle of ExoPlayer
        // Just clear MediaItems, but don't release the player
        try {
            service.getActivePlayer().clearMediaItems()
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Error clearing media items", e)
        }

        // Release custom player if exists (delegated to PlayerConfigurator)
        service.playerConfigurator?.release()

        // Cleanup other resources

        // Set back stacked activity before releasing session
        service.getBackStackedActivity()?.let { backStackedActivity ->
            service.mediaLibrarySession?.setSessionActivity(backStackedActivity)
            android.util.Log.d("AudioPlayerService", "Set back stacked activity before session release")
        }

        // Inspired by Easybook (lines 488-491): Proper MediaSession cleanup
        // Release sessions in correct order
        try {
            service.mediaLibrarySession?.release()
            android.util.Log.d("AudioPlayerService", "MediaLibrarySession released")
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Error releasing MediaLibrarySession", e)
        }

        try {
            service.mediaSession?.release()
            service.mediaSession = null
            android.util.Log.d("AudioPlayerService", "MediaSession released")
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Error releasing MediaSession", e)
        }

        // Release other managers
        service.mediaSessionManager?.release()
        service.playbackTimer?.release()
        service.inactivityTimer?.release()

        // Clear MediaSessionService.Listener
        // service.clearListener() // protected method of MediaSessionService logic?
        // AudioPlayerService defines clearListener() or it is from parent?
        // MediaSessionService has clearListener().
        // We cannot call protected method from helper class unless helper is inner or extended?
        // ServiceLifecycleManager is internal class.
        // So we cannot call service.clearListener() if it is protected in MediaSessionService?
        // We should call it from AudioPlayerService.
        // We will expose a method in AudioPlayerService or just keep super.onDestroy() there.

        // Assuming service.cleanupListener() or similar wrapper exists or we leave clearListener() in Service.

        android.util.Log.i("AudioPlayerService", "Service destroyed and all resources cleaned up")
    }

    fun stopAndCleanup() {
        android.util.Log.d("AudioPlayerService", "stopAndCleanup() called")

        // Clear duration cache to free memory
        service.durationManager.clearCache()

        val player = service.getActivePlayer()
        try {
            android.util.Log.d("AudioPlayerService", "Stopping player and releasing resources")
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
                android.util.Log.d("AudioPlayerService", "Notification cancelled in stopAndCleanup")
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Error cancelling notification", e)
            }

            // Clear notification manager reference
            service.notificationManager = null

            android.util.Log.d("AudioPlayerService", "Player stopped and resources released")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to stop and cleanup", e)
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Stop and cleanup execution")
        }
    }
}
