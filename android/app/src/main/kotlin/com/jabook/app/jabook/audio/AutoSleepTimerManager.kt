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

import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages auto-sleep timer that automatically starts a sleep timer when playback begins.
 *
 * When enabled, listens for playback start events and starts a fixed-duration sleep timer.
 * The timer resets on user interaction (seek, speed change, etc.) via [onUserInteraction].
 * Does not interfere with manually set sleep timers.
 */
internal class AutoSleepTimerManager(
    private val playerServiceScope: CoroutineScope,
    private val getSleepTimerManager: () -> SleepTimerManager?,
    private val isManualSleepTimerActive: () -> Boolean,
) {
    private var isEnabled: Boolean = false
    private var durationMinutes: Int = 30
    private var autoTimerJob: Job? = null
    private var isPlaying: Boolean = false

    fun updateSettings(
        enabled: Boolean,
        minutes: Int,
    ) {
        val changed = isEnabled != enabled || durationMinutes != minutes
        isEnabled = enabled
        durationMinutes = minutes.coerceIn(5, 240)
        if (changed) {
            LogUtils.d(TAG, "Auto-sleep settings updated: enabled=$isEnabled, duration=${durationMinutes}min")
            if (!isEnabled) {
                cancelAutoTimer()
            }
        }
    }

    /**
     * Called when playback state changes. Starts auto-sleep timer when playback begins
     * if enabled and no manual sleep timer is active.
     */
    fun onPlaybackStateChanged(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (playing) {
            maybeStartAutoTimer()
        } else {
            cancelAutoTimer()
        }
    }

    /**
     * Called on user interaction (seek, speed change, chapter skip, etc.).
     * Resets the auto-sleep timer if active.
     */
    fun onUserInteraction() {
        if (!isEnabled || !isPlaying) return
        if (isManualSleepTimerActive()) return
        if (autoTimerJob?.isActive != true) return
        LogUtils.d(TAG, "User interaction detected, resetting auto-sleep timer")
        restartAutoTimer()
    }

    fun release() {
        cancelAutoTimer()
    }

    private fun maybeStartAutoTimer() {
        if (!isEnabled) return
        if (isManualSleepTimerActive()) {
            LogUtils.d(TAG, "Manual sleep timer active, skipping auto-sleep")
            return
        }
        startAutoTimer()
    }

    private fun startAutoTimer() {
        cancelAutoTimer()
        autoTimerJob =
            playerServiceScope.launch(Dispatchers.Main) {
                val millis = durationMinutes * 60 * 1000L
                LogUtils.d(TAG, "Auto-sleep timer started: ${durationMinutes}min")
                delay(millis)
                // Re-check before firing
                if (!isEnabled || !isPlaying) return@launch
                if (isManualSleepTimerActive()) {
                    LogUtils.d(TAG, "Manual sleep timer became active during auto-sleep delay, skipping")
                    return@launch
                }
                LogUtils.d(TAG, "Auto-sleep timer expired, starting sleep timer")
                getSleepTimerManager()?.setSleepTimerMinutes(durationMinutes)
            }
    }

    private fun restartAutoTimer() {
        startAutoTimer()
    }

    private fun cancelAutoTimer() {
        autoTimerJob?.cancel()
        autoTimerJob = null
    }

    companion object {
        private const val TAG = "AutoSleepTimerManager"
    }
}
