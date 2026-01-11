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

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages sleep timer functionality.
 *
 * Inspired by lissen-android implementation: uses SuspendableCountDownTimer
 * for pause/resume functionality when playback pauses/resumes.
 */
internal class SleepTimerManager(
    private val context: Context,
    private val packageName: String,
    private val playerServiceScope: CoroutineScope,
    private val getActivePlayer: () -> ExoPlayer,
    private val sendBroadcast: (Intent) -> Unit,
) {
    // Sleep timer state
    var sleepTimerEndTime: Int = 0
        private set
    var sleepTimerEndOfChapter: Boolean = false
        private set
    private var _sleepTimerRemainingSeconds: Int? = null

    // SuspendableCountDownTimer for pause/resume functionality (inspired by lissen-android)
    private var suspendableTimer: SuspendableCountDownTimer? = null
    private var timerOption: TimerOption = TimerOption.FIXED_DURATION

    // Shake to Extend
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime: Long = 0L
    private val shakeThreshold = 1.6f // g-force threshold
    private val shakeDebounceMs = 2000L

    private val shakeListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Normalize by gravity
                val gX = x / SensorManager.GRAVITY_EARTH
                val gY = y / SensorManager.GRAVITY_EARTH
                val gZ = z / SensorManager.GRAVITY_EARTH

                // Calculate gForce
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                if (gForce > shakeThreshold) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > shakeDebounceMs) {
                        lastShakeTime = now
                        extendTimer()
                    }
                }
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int,
            ) {
                // Not used
            }
        }

    /**
     * Timer option types.
     */
    private enum class TimerOption {
        FIXED_DURATION,
        CURRENT_CHAPTER,
    }

    companion object {
        public const val ACTION_SLEEP_TIMER_EXPIRED = "com.jabook.app.jabook.audio.SLEEP_TIMER_EXPIRED"
    }

    /**
     * Sets sleep timer with specified duration in minutes.
     *
     * Inspired by lissen-android: uses SuspendableCountDownTimer for pause/resume.
     *
     * @param minutes Timer duration in minutes
     */
    public fun setSleepTimerMinutes(minutes: Int) {
        stopTimer() // Stop existing timer if any

        val totalMillis = minutes * 60 * 1000L
        if (totalMillis <= 0L) return

        sleepTimerEndTime = System.currentTimeMillis() + totalMillis
        sleepTimerEndOfChapter = false
        timerOption = TimerOption.FIXED_DURATION
        _sleepTimerRemainingSeconds = minutes * 60

        android.util.Log.d("AudioPlayerService", "Sleep timer set: $minutes minutes")

        // Create and start SuspendableCountDownTimer
        suspendableTimer =
            SuspendableCountDownTimer(
                totalMillis = totalMillis,
                intervalMillis = 500L, // Update every 500ms
                onTickSeconds = { seconds ->
                    _sleepTimerRemainingSeconds = seconds.toInt()
                    android.util.Log.v("AudioPlayerService", "Sleep timer tick: ${seconds}s remaining")
                },
                onFinished = {
                    android.util.Log.d("AudioPlayerService", "Sleep timer expired, pausing playback")
                    val player = getActivePlayer()
                    player.playWhenReady = false
                    cancelSleepTimer()
                    sendTimerExpiredEvent()
                },
            )

        // Start timer only if player is playing
        val player = getActivePlayer()
        if (player.isPlaying) {
            suspendableTimer?.start()
        } else {
            // Timer will be started when playback resumes
            android.util.Log.d("AudioPlayerService", "Sleep timer created but paused (player not playing)")
        }

        // Add player listener for pause/resume
        setupPlayerListener()

        // Register shake listener
        setupShakeListener()

        saveTimerState()
    }

    /**
     * Sets sleep timer to expire at end of current chapter.
     *
     * Inspired by lissen-android: timer pauses when playback pauses.
     */
    public fun setSleepTimerEndOfChapter() {
        stopTimer() // Stop existing timer if any

        sleepTimerEndTime = 0
        sleepTimerEndOfChapter = true
        timerOption = TimerOption.CURRENT_CHAPTER
        _sleepTimerRemainingSeconds = null
        suspendableTimer = null

        android.util.Log.d("AudioPlayerService", "Sleep timer set: end of chapter")
        saveTimerState()
        // Note: For "end of chapter" mode, timer will be triggered in onMediaItemTransition
    }

    /**
     * Cancels active sleep timer.
     */
    public fun cancelSleepTimer() {
        stopTimer()
        sleepTimerEndTime = 0
        sleepTimerEndOfChapter = false
        _sleepTimerRemainingSeconds = null
        android.util.Log.d("AudioPlayerService", "Sleep timer cancelled")
        saveTimerState()
    }

    /**
     * Stops and cleans up the timer.
     */
    private fun stopTimer() {
        suspendableTimer?.cancel()
        suspendableTimer = null
        removePlayerListener()
        removeShakeListener()
    }

    /**
     * Gets remaining seconds for sleep timer, or null if not active.
     *
     * @return Remaining seconds, or null if timer is not active or set to "end of chapter"
     */
    public fun getSleepTimerRemainingSeconds(): Int? {
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
    public fun isSleepTimerActive(): Boolean = sleepTimerEndTime > 0 || sleepTimerEndOfChapter

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
     * Sets up player listener for pause/resume timer functionality.
     *
     * Inspired by lissen-android: timer pauses when playback pauses and resumes when playback resumes.
     */
    private var playerListener: Player.Listener? = null

    private fun setupPlayerListener() {
        removePlayerListener() // Remove existing listener if any

        val player = getActivePlayer()
        playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val currentTimer = suspendableTimer ?: return

                    // Only handle pause/resume for fixed duration timer
                    if (timerOption == TimerOption.FIXED_DURATION) {
                        when (isPlaying) {
                            true -> {
                                // Resume timer when playback resumes
                                suspendableTimer = currentTimer.resume()
                                android.util.Log.d("AudioPlayerService", "Sleep timer resumed (playback resumed)")
                            }
                            false -> {
                                // Pause timer when playback pauses
                                currentTimer.pause()
                                android.util.Log.d("AudioPlayerService", "Sleep timer paused (playback paused)")
                            }
                        }
                    }
                }
            }

        player.addListener(playerListener!!)
    }

    /**
     * Removes player listener.
     */
    private fun removePlayerListener() {
        playerListener?.let {
            val player = getActivePlayer()
            player.removeListener(it)
            playerListener = null
        }
    }

    private fun setupShakeListener() {
        if (accelerometer != null) {
            sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            android.util.Log.d("AudioPlayerService", "Shake listener registered")
        }
    }

    private fun removeShakeListener() {
        sensorManager.unregisterListener(shakeListener)
        android.util.Log.d("AudioPlayerService", "Shake listener unregistered")
    }

    private fun extendTimer() {
        if (!isSleepTimerActive() || timerOption != TimerOption.FIXED_DURATION) return

        val remainingSeconds = getSleepTimerRemainingSeconds() ?: 0
        // Extend by 5 minutes
        val newDurationMinutes = (remainingSeconds / 60) + 5

        android.util.Log.d("AudioPlayerService", "Shake detected! Extending timer to $newDurationMinutes minutes")

        // Show toast on Main thread
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            Toast.makeText(context, R.string.sleepTimerExtended, Toast.LENGTH_SHORT).show()
        }

        setSleepTimerMinutes(newDurationMinutes)
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
    public fun restoreTimerState() {
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
                        timerOption = TimerOption.FIXED_DURATION
                        _sleepTimerRemainingSeconds = remaining

                        // Restore SuspendableCountDownTimer
                        val totalMillis = remaining * 1000L
                        suspendableTimer =
                            SuspendableCountDownTimer(
                                totalMillis = totalMillis,
                                intervalMillis = 500L,
                                onTickSeconds = { seconds ->
                                    _sleepTimerRemainingSeconds = seconds.toInt()
                                },
                                onFinished = {
                                    android.util.Log.d("AudioPlayerService", "Restored sleep timer expired, pausing playback")
                                    val player = getActivePlayer()
                                    player.playWhenReady = false
                                    cancelSleepTimer()
                                    sendTimerExpiredEvent()
                                },
                            )

                        // Start timer only if player is playing
                        val player = getActivePlayer()
                        if (player.isPlaying) {
                            suspendableTimer?.start()
                        }

                        setupPlayerListener()
                        setupShakeListener()

                        android.util.Log.d("AudioPlayerService", "Sleep timer restored: $remaining seconds remaining")
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
