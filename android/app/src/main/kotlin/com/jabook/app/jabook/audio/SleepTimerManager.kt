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
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages sleep timer functionality.
 *
 * Inspired by EasyBook implementation: uses absolute end time instead of periodic timer.
 */
internal class SleepTimerManager(
    private val context: Context,
    private val packageName: String,
    private val playerServiceScope: CoroutineScope,
    private val getActivePlayer: () -> ExoPlayer,
    private val sendBroadcast: (Intent) -> Unit,
) {
    // Sleep timer state (inspired by EasyBook implementation)
    var sleepTimerEndTime: Long = 0
        private set
    var sleepTimerEndOfChapter: Boolean = false
        private set
    private var _sleepTimerRemainingSeconds: Int? = null
    private var sleepTimerCheckJob: Job? = null

    companion object {
        const val ACTION_SLEEP_TIMER_EXPIRED = "com.jabook.app.jabook.audio.SLEEP_TIMER_EXPIRED"
    }

    /**
     * Sets sleep timer with specified duration in minutes.
     *
     * Inspired by EasyBook implementation: uses absolute end time instead of periodic timer.
     *
     * @param minutes Timer duration in minutes
     */
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        sleepTimerEndOfChapter = false
        _sleepTimerRemainingSeconds = minutes * 60
        android.util.Log.d("AudioPlayerService", "Sleep timer set: $minutes minutes")
        saveTimerState()
        startSleepTimerCheck()
    }

    /**
     * Sets sleep timer to expire at end of current chapter.
     *
     * Inspired by EasyBook implementation: uses boolean flag for "end of chapter" mode.
     */
    fun setSleepTimerEndOfChapter() {
        sleepTimerEndTime = 0
        sleepTimerEndOfChapter = true
        _sleepTimerRemainingSeconds = null
        android.util.Log.d("AudioPlayerService", "Sleep timer set: end of chapter")
        saveTimerState()
        // Note: For "end of chapter" mode, we don't need periodic check
        // Timer will be triggered in onMediaItemTransition or onPlaybackStateChanged
    }

    /**
     * Cancels active sleep timer.
     */
    fun cancelSleepTimer() {
        sleepTimerEndTime = 0
        sleepTimerEndOfChapter = false
        _sleepTimerRemainingSeconds = null
        android.util.Log.d("AudioPlayerService", "Sleep timer cancelled")
        saveTimerState()
        stopSleepTimerCheck()
    }

    /**
     * Gets remaining seconds for sleep timer, or null if not active.
     *
     * @return Remaining seconds, or null if timer is not active or set to "end of chapter"
     */
    fun getSleepTimerRemainingSeconds(): Int? {
        if (sleepTimerEndTime == 0L && !sleepTimerEndOfChapter) {
            return null
        }
        if (sleepTimerEndOfChapter) {
            return null // Unknown duration for "end of chapter" mode
        }
        val remaining = ((sleepTimerEndTime - System.currentTimeMillis()) / 1000).toInt()
        return if (remaining > 0) remaining else null
    }

    /**
     * Checks if sleep timer is active.
     *
     * @return true if timer is active (either fixed duration or end of chapter)
     */
    fun isSleepTimerActive(): Boolean = sleepTimerEndTime > 0 || sleepTimerEndOfChapter

    /**
     * Sends sleep timer expired event to Flutter.
     *
     * This method broadcasts an intent that will be handled by Flutter
     * through MethodChannel or EventChannel.
     */
    private fun sendTimerExpiredEvent() {
        val intent =
            Intent(ACTION_SLEEP_TIMER_EXPIRED).apply {
                setPackage(packageName) // Set package for explicit broadcast
            }
        sendBroadcast(intent)
        android.util.Log.d("AudioPlayerService", "Sleep timer expired event sent")
        // Clear saved timer state when expired
        saveTimerState()
    }

    /**
     * Starts periodic check for sleep timer expiration (inspired by EasyBook progressUpdater).
     *
     * Checks timer every 500ms while player is playing, similar to EasyBook implementation.
     */
    fun startSleepTimerCheck() {
        stopSleepTimerCheck() // Stop existing check if any

        if (sleepTimerEndTime <= 0) {
            android.util.Log.d("AudioPlayerService", "Sleep timer check not started: no active timer")
            return
        }

        android.util.Log.d("AudioPlayerService", "Starting sleep timer periodic check (every 500ms)")
        sleepTimerCheckJob =
            playerServiceScope.launch {
                while (true) {
                    delay(500) // Check every 500ms like EasyBook

                    val player = getActivePlayer()
                    if (player.isPlaying && sleepTimerEndTime > 0) {
                        val currentTime = System.currentTimeMillis()
                        val remaining = ((sleepTimerEndTime - currentTime) / 1000).toInt()
                        if (currentTime >= sleepTimerEndTime) {
                            android.util.Log.d("AudioPlayerService", "Sleep timer expired (fixed duration), pausing playback")
                            player.playWhenReady = false
                            cancelSleepTimer()
                            sendTimerExpiredEvent()
                            break // Stop checking after expiration
                        }
                    } else if (!player.isPlaying || sleepTimerEndTime == 0L) {
                        // Player stopped or timer cancelled, stop checking
                        android.util.Log.d("AudioPlayerService", "Sleep timer check stopped: player not playing or timer cancelled")
                        break
                    }
                }
            }
    }

    /**
     * Stops periodic sleep timer check.
     */
    fun stopSleepTimerCheck() {
        sleepTimerCheckJob?.cancel()
        sleepTimerCheckJob = null
    }

    /**
     * Saves sleep timer state to SharedPreferences for restoration after app restart.
     */
    private fun saveTimerState() {
        try {
            val prefs = context.getSharedPreferences("jabook_timer_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putLong("sleepTimerEndTime", sleepTimerEndTime)
            editor.putBoolean("sleepTimerEndOfChapter", sleepTimerEndOfChapter)
            editor.apply()
            android.util.Log.d(
                "AudioPlayerService",
                "Sleep timer state saved: endTime=$sleepTimerEndTime, endOfChapter=$sleepTimerEndOfChapter",
            )
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to save sleep timer state", e)
        }
    }

    /**
     * Restores sleep timer state from SharedPreferences.
     *
     * Should be called in onCreate or onStartCommand to restore timer after app restart.
     */
    fun restoreTimerState() {
        try {
            val prefs = context.getSharedPreferences("jabook_timer_prefs", Context.MODE_PRIVATE)
            val savedEndTime = prefs.getLong("sleepTimerEndTime", 0)
            val savedEndOfChapter = prefs.getBoolean("sleepTimerEndOfChapter", false)

            if (savedEndTime > 0 || savedEndOfChapter) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Restoring sleep timer state: endTime=$savedEndTime, endOfChapter=$savedEndOfChapter",
                )

                if (savedEndOfChapter) {
                    // Restore "end of chapter" mode
                    sleepTimerEndTime = 0
                    sleepTimerEndOfChapter = true
                    _sleepTimerRemainingSeconds = null
                    android.util.Log.d("AudioPlayerService", "Sleep timer restored: end of chapter mode")
                } else {
                    // Restore fixed duration timer
                    val currentTime = System.currentTimeMillis()
                    val remaining = ((savedEndTime - currentTime) / 1000).toInt()
                    if (remaining > 0) {
                        // Timer hasn't expired yet, restore it
                        sleepTimerEndTime = savedEndTime
                        sleepTimerEndOfChapter = false
                        _sleepTimerRemainingSeconds = remaining
                        android.util.Log.d("AudioPlayerService", "Sleep timer restored: $remaining seconds remaining")
                        // Start periodic check for restored timer
                        startSleepTimerCheck()
                    } else {
                        // Timer already expired, clear it
                        android.util.Log.d("AudioPlayerService", "Sleep timer expired while app was closed, clearing")
                        sleepTimerEndTime = 0
                        sleepTimerEndOfChapter = false
                        _sleepTimerRemainingSeconds = null
                        saveTimerState() // Clear saved state
                    }
                }
            } else {
                android.util.Log.d("AudioPlayerService", "No saved sleep timer state to restore")
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to restore sleep timer state", e)
        }
    }
}
