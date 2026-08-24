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
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.rethrowCancellation
import com.jabook.app.jabook.compose.data.preferences.SleepTimerState
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

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
    private val saveCurrentPositionOnExpiry: () -> Unit = {},
    private val audioFader: AudioFader? = null,
    private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.SettingsRepository? = null,
    private val saveSleepTimerStateToDataStore: (com.jabook.app.jabook.compose.data.preferences.SleepTimerState) -> Unit = {},
    private val isShakeToExtendEnabled: () -> Boolean = { true },
    private val isPowerSaveModeEnabled: () -> Boolean = {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isPowerSaveMode == true
    },
) {
    // Sleep timer state
    var sleepTimerEndTime: Long = 0L
        private set
    var sleepTimerMode: SleepTimerMode = SleepTimerMode.NONE
        private set
    val sleepTimerEndOfChapter: Boolean
        get() = sleepTimerMode == SleepTimerMode.CHAPTER_END
    val sleepTimerEndOfTrack: Boolean
        get() = sleepTimerMode == SleepTimerMode.TRACK_END
    private var _sleepTimerRemainingSeconds: Int? = null

    // SuspendableCountDownTimer for pause/resume functionality (inspired by lissen-android)
    private var suspendableTimer: SuspendableCountDownTimer? = null
    private var isFixedTimerPaused: Boolean = false
    private var fixedTimerPausedRemainingMillis: Long? = null
    private val timerGeneration = AtomicLong(0L)

    // Shake to Extend
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isTimerExtensionInProgress: Boolean = false
    private var isShakeListenerRegistered: Boolean = false

    // Injectable clock so tests can drive the detector deterministically.
    private var shakeClockMillis: Long = 0L
    private val shakeDetector =
        ImprovedShakeDetector(
            clockMs = { shakeClockMillis },
            onShakeDetected = { extendTimer() },
        )
    private val shakeSensorListener: SensorEventListener = shakeDetector.asListener()

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
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        stopTimer() // Stop existing timer if any
        val callbackGeneration = timerGeneration.get()

        val totalMillis = minutes * 60 * 1000L

        sleepTimerEndTime = System.currentTimeMillis() + totalMillis
        sleepTimerMode = SleepTimerMode.FIXED_DURATION
        isFixedTimerPaused = true
        fixedTimerPausedRemainingMillis = totalMillis
        _sleepTimerRemainingSeconds = minutes * 60

        LogUtils.d("AudioPlayerService", "Sleep timer set: $minutes minutes")

        // Create and start SuspendableCountDownTimer
        suspendableTimer =
            SuspendableCountDownTimer(
                totalMillis = totalMillis,
                intervalMillis = 500L, // Update every 500ms
                onTickSeconds = { seconds ->
                    if (isCurrentFixedTimer(callbackGeneration)) {
                        _sleepTimerRemainingSeconds = seconds.toInt()
                    }
                    LogUtils.v("AudioPlayerService", "Sleep timer tick: ${seconds}s remaining")
                },
                onFinished = {
                    expireFixedTimer(callbackGeneration, "Sleep timer expired, pausing playback")
                },
            )

        // Start timer only if player is playing
        val player = getActivePlayer()
        if (player.isPlaying) {
            suspendableTimer?.start()
            isFixedTimerPaused = false
            fixedTimerPausedRemainingMillis = null
        } else {
            // Timer will be started when playback resumes
            LogUtils.d("AudioPlayerService", "Sleep timer created but paused (player not playing)")
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
        sleepTimerMode = SleepTimerMode.CHAPTER_END
        isFixedTimerPaused = false
        fixedTimerPausedRemainingMillis = null
        _sleepTimerRemainingSeconds = null
        suspendableTimer = null

        LogUtils.d("AudioPlayerService", "Sleep timer set: end of chapter")
        saveTimerState()
        // Note: For "end of chapter" mode, timer will be triggered in onMediaItemTransition
    }

    public fun setSleepTimerEndOfChapterOrFallback(hasChapterModeSupport: Boolean): Boolean {
        if (hasChapterModeSupport) {
            setSleepTimerEndOfChapter()
            return true
        }
        setSleepTimerEndOfTrack()
        return false
    }

    public fun setSleepTimerEndOfTrack() {
        stopTimer() // Stop existing timer if any

        sleepTimerEndTime = 0
        sleepTimerMode = SleepTimerMode.TRACK_END
        isFixedTimerPaused = false
        fixedTimerPausedRemainingMillis = null
        _sleepTimerRemainingSeconds = null
        suspendableTimer = null

        LogUtils.d("AudioPlayerService", "Sleep timer set: end of track")
        saveTimerState()
    }

    /**
     * Cancels active sleep timer.
     */
    public fun cancelSleepTimer() {
        stopTimer()
        clearRuntimeState()
        LogUtils.d("AudioPlayerService", "Sleep timer cancelled")
        saveTimerState()
    }

    /**
     * Stops and cleans up the timer.
     */
    private fun stopTimer() {
        timerGeneration.incrementAndGet()
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
        if (sleepTimerEndTime == 0L && sleepTimerMode == SleepTimerMode.NONE) {
            return null
        }
        if (sleepTimerMode != SleepTimerMode.FIXED_DURATION) {
            return null // Unknown duration for mode-based end conditions
        }

        if (isFixedTimerPaused) {
            val pausedRemaining = fixedTimerPausedRemainingMillis ?: return null
            val pausedRemainingSeconds = (pausedRemaining / 1000).toInt()
            return pausedRemainingSeconds.takeIf { it > 0 }
        }

        val remaining = ((sleepTimerEndTime - System.currentTimeMillis()) / 1000).toInt()
        return if (remaining > 0) remaining else null
    }

    /**
     * Checks if sleep timer is active.
     *
     * @return true if timer is active (either fixed duration or end of chapter)
     */
    public fun isSleepTimerActive(): Boolean = sleepTimerMode != SleepTimerMode.NONE

    /**
     * Sends sleep timer expired broadcast.
     */
    private fun sendTimerExpiredEvent() {
        val intent =
            Intent(ACTION_SLEEP_TIMER_EXPIRED).apply {
                setPackage(packageName) // Set package for explicit broadcast
            }
        sendBroadcast(intent)
        LogUtils.d("AudioPlayerService", "Sleep timer expired event sent")
        // Clear saved timer state when expired
        saveTimerState()
    }

    /** Publishes the same expiry signal for chapter/track based timers. */
    internal fun notifyTimerExpired() {
        sendTimerExpiredEvent()
    }

    /**
     * Sets up player listener for pause/resume timer functionality.
     *
     * Inspired by lissen-android: timer pauses when playback pauses and resumes when playback resumes.
     */
    private var playerListener: Player.Listener? = null
    private var playerListenerTarget: ExoPlayer? = null

    private fun setupPlayerListener() {
        removePlayerListener() // Remove existing listener if any

        val player = getActivePlayer()
        playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val currentTimer = suspendableTimer ?: return

                    // Only handle pause/resume for fixed duration timer
                    if (sleepTimerMode == SleepTimerMode.FIXED_DURATION) {
                        when (isPlaying) {
                            true -> {
                                // Resume timer when playback resumes
                                val remainingMillis = currentTimer.getRemainingMillis()
                                suspendableTimer = currentTimer.resume()
                                sleepTimerEndTime = System.currentTimeMillis() + remainingMillis
                                isFixedTimerPaused = false
                                fixedTimerPausedRemainingMillis = null
                                saveTimerState()
                                LogUtils.d("AudioPlayerService", "Sleep timer resumed (playback resumed)")
                            }
                            false -> {
                                // Pause timer when playback pauses
                                val remainingMillis = currentTimer.pause()
                                isFixedTimerPaused = true
                                fixedTimerPausedRemainingMillis = remainingMillis
                                saveTimerState()
                                LogUtils.d("AudioPlayerService", "Sleep timer paused (playback paused)")
                            }
                        }
                    }
                }
            }

        player.addListener(playerListener!!)
        playerListenerTarget = player
    }

    /**
     * Removes player listener.
     */
    private fun removePlayerListener() {
        playerListener?.let {
            playerListenerTarget?.removeListener(it)
        }
        playerListener = null
        playerListenerTarget = null
    }

    private fun setupShakeListener() {
        if (!isSleepTimerActive() || sleepTimerMode != SleepTimerMode.FIXED_DURATION) {
            return
        }
        if (!isShakeToExtendEnabled()) {
            LogUtils.d("AudioPlayerService", "Shake listener skipped: feature disabled")
            return
        }
        if (isPowerSaveModeEnabled()) {
            LogUtils.d("AudioPlayerService", "Shake listener skipped: power save mode is enabled")
            return
        }
        if (isShakeListenerRegistered) {
            return
        }
        if (accelerometer != null) {
            shakeDetector.reset()
            sensorManager.registerListener(shakeSensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isShakeListenerRegistered = true
            LogUtils.d("AudioPlayerService", "Shake listener registered")
        }
    }

    private fun removeShakeListener() {
        if (!isShakeListenerRegistered) {
            return
        }
        sensorManager.unregisterListener(shakeSensorListener)
        isShakeListenerRegistered = false
        LogUtils.d("AudioPlayerService", "Shake listener unregistered")
    }

    internal fun triggerShakeForTesting(nowMillis: Long) {
        shakeClockMillis = nowMillis
        // Detector requires minShakeCount jolts within its window; deliver both at once.
        val jolt = SensorManager.GRAVITY_EARTH * (ImprovedShakeDetector.DEFAULT_THRESHOLD + 1f)
        shakeDetector.processAccelerometer(0f, 0f, jolt)
        shakeDetector.processAccelerometer(0f, 0f, jolt)
    }

    private fun extendTimer() {
        if (!isSleepTimerActive() || sleepTimerMode != SleepTimerMode.FIXED_DURATION) return
        if (!isShakeToExtendEnabled() || isPowerSaveModeEnabled()) return
        if (isTimerExtensionInProgress) return

        isTimerExtensionInProgress = true
        try {
            val remainingSeconds = getSleepTimerRemainingSeconds() ?: 0
            // Extend by 5 minutes, rounding partial minutes up so up to 59s is not dropped
            val newDurationMinutes = (remainingSeconds + 59) / 60 + 5

            LogUtils.d("AudioPlayerService", "Shake detected! Extending timer to $newDurationMinutes minutes")

            // Show toast on Main thread
            playerServiceScope.launch(Dispatchers.Main) {
                try {
                    Toast.makeText(context, R.string.sleepTimerExtended, Toast.LENGTH_SHORT).show()
                } catch (toastError: Exception) {
                    LogUtils.w(
                        "AudioPlayerService",
                        "Failed to show sleep timer extension toast: ${toastError.message}",
                    )
                }
            }

            setSleepTimerMinutes(newDurationMinutes)
        } finally {
            isTimerExtensionInProgress = false
        }
    }

/**
     * Saves sleep timer state to DataStore (primary) and SharedPreferences (migration fallback).
     */
    private fun saveTimerState() {
        try {
            val persistedState =
                SleepTimerPersistence.toPersistedState(
                    SleepTimerRuntimeState(
                        endTimeMillis = sleepTimerEndTime,
                        mode = sleepTimerMode,
                        fixedDurationPaused = sleepTimerMode == SleepTimerMode.FIXED_DURATION && isFixedTimerPaused,
                        fixedDurationPausedRemainingMillis = fixedTimerPausedRemainingMillis,
                    ),
                )

            // Save to DataStore via callback (async)
            val dataStoreState =
                com.jabook.app.jabook.compose.data.preferences.SleepTimerState
                    .newBuilder()
                    .setMode(persistedState.mode?.name ?: "NONE")
                    .setEndTimeEpochMs(persistedState.endTimeMillis)
                    .setIsPaused(persistedState.paused)
                    .setPausedRemainingMs(persistedState.pausedRemainingMillis)
                    .build()
            saveSleepTimerStateToDataStore(dataStoreState)

            // Also save to SharedPreferences for backward compatibility during migration
            try {
                val prefs = context.getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putLong(SleepTimerPersistence.KEY_END_TIME, persistedState.endTimeMillis)
                editor.putBoolean(SleepTimerPersistence.KEY_END_OF_CHAPTER, persistedState.endOfChapter)
                editor.putString(SleepTimerPersistence.KEY_MODE, persistedState.mode?.name)
                editor.putBoolean(SleepTimerPersistence.KEY_PAUSED, persistedState.paused)
                editor.putLong(SleepTimerPersistence.KEY_PAUSED_REMAINING_MILLIS, persistedState.pausedRemainingMillis)
                editor.apply()
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.w("AudioPlayerService", "Failed to backup sleep timer state to SharedPreferences", e)
            }

            LogUtils.d(
                "AudioPlayerService",
                "Sleep timer state saved: endTime=$sleepTimerEndTime, endOfChapter=$sleepTimerEndOfChapter",
            )
        } catch (e: Exception) {
            e.rethrowCancellation()
            LogUtils.e("AudioPlayerService", "Failed to save sleep timer state", e)
        }
    }

/**
     * Restores sleep timer state from DataStore (primary) or SharedPreferences (fallback).
     *
     * Should be called in onCreate or onStartCommand to restore timer after app restart.
     */
    public suspend fun restoreTimerState() {
        try {
            stopTimer()
            // Try DataStore first if settingsRepository is available
            if (settingsRepository != null) {
                try {
                    val dataStoreState =
                        (settingsRepository as? com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository)
                            ?.sleepTimerState
                            ?.first()
                            ?: com.jabook.app.jabook.compose.data.preferences.SleepTimerState
                                .getDefaultInstance()
                    if (dataStoreState.mode.isNotBlank() || dataStoreState.endTimeEpochMs > 0L) {
                        restoreFromDataStoreState(dataStoreState)
                        LogUtils.d("AudioPlayerService", "Sleep timer restored from DataStore")
                        return
                    }
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    LogUtils.w("AudioPlayerService", "Failed to read sleep timer from DataStore, falling back to SharedPreferences", e)
                }
            }

            // Fallback to SharedPreferences
            restoreFromSharedPreferences()
        } catch (e: Exception) {
            e.rethrowCancellation()
            LogUtils.e("AudioPlayerService", "Failed to restore sleep timer state", e)
        }
    }

    /** Restore sleep timer state from DataStore SleepTimerState proto. */
    internal fun restoreFromDataStoreState(dataStoreState: com.jabook.app.jabook.compose.data.preferences.SleepTimerState) {
        val mode = enumValueOfOrNull(dataStoreState.mode) ?: SleepTimerMode.NONE
        when (mode) {
            SleepTimerMode.CHAPTER_END -> {
                sleepTimerEndTime = 0
                sleepTimerMode = SleepTimerMode.CHAPTER_END
                isFixedTimerPaused = false
                fixedTimerPausedRemainingMillis = null
                _sleepTimerRemainingSeconds = null
                LogUtils.d("AudioPlayerService", "Sleep timer restored: end of chapter mode")
                return
            }
            SleepTimerMode.TRACK_END -> {
                sleepTimerEndTime = 0
                sleepTimerMode = SleepTimerMode.TRACK_END
                isFixedTimerPaused = false
                fixedTimerPausedRemainingMillis = null
                _sleepTimerRemainingSeconds = null
                LogUtils.d("AudioPlayerService", "Sleep timer restored: end of track mode")
                return
            }
            SleepTimerMode.NONE -> {
                clearRuntimeState()
                return
            }
            SleepTimerMode.FIXED_DURATION -> Unit
        }
        val nowMillis = System.currentTimeMillis()
        val remainingMillis =
            when {
                dataStoreState.isPaused && dataStoreState.pausedRemainingMs > 0L -> dataStoreState.pausedRemainingMs
                else -> (dataStoreState.endTimeEpochMs - nowMillis).coerceAtLeast(0L)
            }

        if (remainingMillis <= 0L) {
            clearRuntimeState()
            return
        }

        restoreFixedDurationTimer(
            remainingMillis = remainingMillis,
            paused = dataStoreState.isPaused,
        )
    }

    /** Restore sleep timer state from SharedPreferences (legacy fallback). */
    private fun restoreFromSharedPreferences() {
        val prefs = context.getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE)
        val persistedState =
            SleepTimerPersistedState(
                endTimeMillis = prefs.getLong(SleepTimerPersistence.KEY_END_TIME, 0L),
                endOfChapter = prefs.getBoolean(SleepTimerPersistence.KEY_END_OF_CHAPTER, false),
                mode =
                    prefs.getString(SleepTimerPersistence.KEY_MODE, null)?.let { modeName ->
                        enumValueOfOrNull(modeName)
                    },
                paused = prefs.getBoolean(SleepTimerPersistence.KEY_PAUSED, false),
                pausedRemainingMillis =
                    prefs.getLong(
                        SleepTimerPersistence.KEY_PAUSED_REMAINING_MILLIS,
                        SleepTimerPersistence.NO_REMAINING_MILLIS,
                    ),
            )

        when (
            val restorePlan =
                SleepTimerPersistence.computeRestorePlan(
                    persistedState = persistedState,
                    nowMillis = System.currentTimeMillis(),
                )
        ) {
            SleepTimerRestorePlan.None -> {
                val hadPersistedState =
                    persistedState.endTimeMillis > 0L ||
                        persistedState.endOfChapter ||
                        persistedState.mode != null
                clearRuntimeState()
                if (hadPersistedState) {
                    saveTimerState()
                }
                LogUtils.d("AudioPlayerService", "No saved sleep timer state to restore")
            }
            SleepTimerRestorePlan.EndOfChapter -> {
                sleepTimerEndTime = 0
                sleepTimerMode = SleepTimerMode.CHAPTER_END
                isFixedTimerPaused = false
                fixedTimerPausedRemainingMillis = null
                _sleepTimerRemainingSeconds = null
                LogUtils.d("AudioPlayerService", "Sleep timer restored: end of chapter mode")
            }
            SleepTimerRestorePlan.EndOfTrack -> {
                sleepTimerEndTime = 0
                sleepTimerMode = SleepTimerMode.TRACK_END
                isFixedTimerPaused = false
                fixedTimerPausedRemainingMillis = null
                _sleepTimerRemainingSeconds = null
                LogUtils.d("AudioPlayerService", "Sleep timer restored: end of track mode")
            }
            is SleepTimerRestorePlan.FixedDuration -> {
                restoreFixedDurationTimer(
                    remainingMillis = restorePlan.remainingMillis,
                    paused = restorePlan.paused,
                )
            }
        }
    }

    /**
     * Releases all resources (listeners, sensors, timers).
     * Should be called when service is destroyed.
     */
    public fun release() {
        stopTimer()
        LogUtils.d("AudioPlayerService", "SleepTimerManager released")
    }

    private fun clearRuntimeState() {
        sleepTimerEndTime = 0L
        sleepTimerMode = SleepTimerMode.NONE
        isFixedTimerPaused = false
        fixedTimerPausedRemainingMillis = null
        _sleepTimerRemainingSeconds = null
    }

    private fun restoreFixedDurationTimer(
        remainingMillis: Long,
        paused: Boolean,
    ) {
        val callbackGeneration = timerGeneration.get()
        val remaining = (remainingMillis / 1000).toInt()

        sleepTimerEndTime = System.currentTimeMillis() + remainingMillis
        sleepTimerMode = SleepTimerMode.FIXED_DURATION
        isFixedTimerPaused = paused
        fixedTimerPausedRemainingMillis = remainingMillis.takeIf { paused }
        _sleepTimerRemainingSeconds = remaining

        suspendableTimer =
            SuspendableCountDownTimer(
                totalMillis = remainingMillis,
                intervalMillis = 500L,
                onTickSeconds = { seconds ->
                    if (isCurrentFixedTimer(callbackGeneration)) {
                        _sleepTimerRemainingSeconds = seconds.toInt()
                    }
                },
                onFinished = {
                    expireFixedTimer(callbackGeneration, "Restored sleep timer expired, pausing playback")
                },
            )

        val player = getActivePlayer()
        if (player.isPlaying) {
            suspendableTimer?.start()
            sleepTimerEndTime = System.currentTimeMillis() + remainingMillis
            isFixedTimerPaused = false
            fixedTimerPausedRemainingMillis = null
        } else {
            isFixedTimerPaused = true
            fixedTimerPausedRemainingMillis = remainingMillis
        }

        setupPlayerListener()
        setupShakeListener()

        LogUtils.d("AudioPlayerService", "Sleep timer restored: $remaining seconds remaining")
    }

    private fun expireFixedTimer(
        callbackGeneration: Long,
        message: String,
    ) {
        if (!isCurrentFixedTimer(callbackGeneration)) return

        LogUtils.d("AudioPlayerService", message)
        saveCurrentPositionOnExpiry()
        val player = getActivePlayer()
        if (audioFader != null) {
            audioFader.fadeOut(player) {
                if (isCurrentFixedTimer(callbackGeneration)) {
                    player.playWhenReady = false
                    cancelSleepTimer()
                    sendTimerExpiredEvent()
                }
            }
        } else if (isCurrentFixedTimer(callbackGeneration)) {
            player.playWhenReady = false
            cancelSleepTimer()
            sendTimerExpiredEvent()
        }
    }

    private fun isCurrentFixedTimer(callbackGeneration: Long): Boolean =
        SleepTimerExpiryStalenessPolicy.shouldApply(
            activeGeneration = timerGeneration.get(),
            callbackGeneration = callbackGeneration,
            activeMode = sleepTimerMode,
        )

    private fun enumValueOfOrNull(modeName: String): SleepTimerMode? =
        try {
            SleepTimerMode.valueOf(modeName)
        } catch (_: IllegalArgumentException) {
            null
        }
}
