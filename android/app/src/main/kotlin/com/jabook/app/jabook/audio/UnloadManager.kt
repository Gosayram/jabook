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

import android.content.Context
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import android.app.NotificationManager as AndroidNotificationManager

/**
 * Manages player unloading and resource cleanup.
 */
internal class UnloadManager(
    private val context: Context,
    private val getActivePlayer: () -> ExoPlayer,
    private val getCustomExoPlayer: () -> ExoPlayer?,
    private val releaseCustomExoPlayer: () -> Unit,
    private val getMediaSession: () -> MediaSession?,
    private val releaseMediaSession: () -> Unit,
    private val getMediaSessionManager: () -> MediaSessionManager?,
    private val releaseMediaSessionManager: () -> Unit,
    private val getInactivityTimer: () -> InactivityTimer?,
    private val releaseInactivityTimer: () -> Unit,
    private val getPlaybackTimer: () -> PlaybackTimer?,
    private val releasePlaybackTimer: () -> Unit,
    private val getCurrentMetadata: () -> Map<String, String>?,
    private val setCurrentMetadata: (Map<String, String>?) -> Unit,
    private val getEmbeddedArtworkPath: () -> String?,
    private val setEmbeddedArtworkPath: (String?) -> Unit,
    private val saveCurrentPosition: () -> Unit,
    private val stopForeground: (Int) -> Unit,
    private val stopSelf: () -> Unit,
) {
    /**
     * Unloads player due to inactivity timer expiration.
     *
     * This method:
     * 1. Saves current position (position is already saved periodically and on pause)
     * 2. Stops ExoPlayer and clears MediaItems
     * 3. Releases MediaSession and other resources
     * 4. Removes notification
     * 5. Stops foreground service
     * 6. Stops the service itself
     *
     * Note: Position saving is handled by Media3PlayerService (Dart) which saves
     * periodically and on app lifecycle events. This method focuses on resource cleanup.
     */
    fun unloadPlayerDueToInactivity() {
        android.util.Log.i("AudioPlayerService", "Unloading player due to inactivity")

        try {
            // Log memory usage before unloading (for debugging)
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
            val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
            android.util.Log.d(
                "AudioPlayerService",
                "Memory usage before unload: ${usedMemory}MB / ${maxMemory}MB",
            )

            // Save position before unloading (attempt to trigger save through broadcast)
            val activePlayer = getActivePlayer()
            if (activePlayer.mediaItemCount > 0) {
                val currentIndex = activePlayer.currentMediaItemIndex
                val currentPosition = activePlayer.currentPosition
                android.util.Log.d(
                    "AudioPlayerService",
                    "Saving position before unload: track=$currentIndex, position=${currentPosition}ms",
                )

                // Broadcast intent to trigger position saving through MethodChannel
                // This will be handled by MainActivity or AudioPlayerMethodHandler if available
                try {
                    saveCurrentPosition()
                } catch (e: Exception) {
                    android.util.Log.w("AudioPlayerService", "Failed to send save position broadcast", e)
                    // Continue with unload even if broadcast fails - position is already saved periodically
                }

                // Note: Position is also saved periodically by Media3PlayerService (every 10-15 seconds)
                // and will be saved on next app resume/pause event, so this is an additional safety measure
            }

            // Stop ExoPlayer and clear MediaItems
            try {
                activePlayer.stop()
                activePlayer.clearMediaItems()
                android.util.Log.d("AudioPlayerService", "ExoPlayer stopped and MediaItems cleared")
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Error stopping player", e)
            }

            // Release custom player if exists
            getCustomExoPlayer()?.release()
            releaseCustomExoPlayer()

            // Release MediaSession
            getMediaSession()?.release()
            releaseMediaSession()
            android.util.Log.d("AudioPlayerService", "MediaSession released")

            // Release MediaSessionManager
            getMediaSessionManager()?.release()
            releaseMediaSessionManager()
            android.util.Log.d("AudioPlayerService", "MediaSessionManager released")

            // Release timers
            getInactivityTimer()?.release()
            releaseInactivityTimer()
            getPlaybackTimer()?.release()
            releasePlaybackTimer()
            android.util.Log.d("AudioPlayerService", "Timers released")

            // Remove notification
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
                    android.util.Log.d("AudioPlayerService", "Foreground service stopped and notification removed")
                } else {
                    // Use AndroidNotificationManager to cancel notification
                    val androidNotificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                    androidNotificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
                    android.util.Log.d("AudioPlayerService", "Notification cancelled")
                }
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Failed to remove notification", e)
            }

            // Clear metadata
            setCurrentMetadata(null)
            setEmbeddedArtworkPath(null)

            // Stop the service
            android.util.Log.i("AudioPlayerService", "Stopping service due to inactivity")

            // Log memory usage after cleanup (for debugging)
            val runtimeAfter = Runtime.getRuntime()
            val usedMemoryAfter = (runtimeAfter.totalMemory() - runtimeAfter.freeMemory()) / 1024 / 1024 // MB
            android.util.Log.d(
                "AudioPlayerService",
                "Memory usage after cleanup: ${usedMemoryAfter}MB / ${runtimeAfter.maxMemory() / 1024 / 1024}MB",
            )

            stopSelf()
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Error unloading player due to inactivity", e)
            // Still try to stop the service even if there was an error
            try {
                stopSelf()
            } catch (e2: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to stop service", e2)
            }
        }
    }
}
