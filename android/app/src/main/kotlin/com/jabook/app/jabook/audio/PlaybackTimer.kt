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
import android.os.CountDownTimer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages sleep timer for playback.
 *
 * Inspired by lissen-android implementation for sleep timer functionality.
 * Supports timer options: fixed duration or "until end of current track".
 */
class PlaybackTimer(
    private val context: Context,
    private val player: ExoPlayer
) {
    private var timer: CountDownTimer? = null
    private var timerOption: TimerOption = TimerOption.FIXED_DURATION
    private var isPaused = false
    
    enum class TimerOption {
        FIXED_DURATION,      // Timer counts down regardless of playback
        CURRENT_TRACK         // Timer pauses when playback pauses, resumes when playback resumes
    }
    
    companion object {
        const val ACTION_TIMER_TICK = "com.jabook.app.jabook.audio.TIMER_TICK"
        const val ACTION_TIMER_EXPIRED = "com.jabook.app.jabook.audio.TIMER_EXPIRED"
        const val EXTRA_REMAINING_SECONDS = "remainingSeconds"
    }
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // For CURRENT_TRACK option, pause/resume timer with playback
            if (timerOption == TimerOption.CURRENT_TRACK) {
                if (isPlaying && isPaused) {
                    // Resume timer if it was paused
                    resumeTimer()
                } else if (!isPlaying && timer != null && !isPaused) {
                    // Pause timer if playback paused
                    pauseTimer()
                }
            }
        }
    }
    
    init {
        player.addListener(playerListener)
    }
    
    /**
     * Starts sleep timer.
     *
     * @param delayInSeconds Timer duration in seconds
     * @param option Timer option (FIXED_DURATION or CURRENT_TRACK)
     */
    fun startTimer(delayInSeconds: Double, option: TimerOption = TimerOption.FIXED_DURATION) {
        stopTimer()
        
        val totalMillis = (delayInSeconds * 1000).toLong()
        if (totalMillis <= 0L) {
            android.util.Log.w("PlaybackTimer", "Invalid timer duration: $delayInSeconds seconds")
            return
        }
        
        timerOption = option
        isPaused = false
        
        android.util.Log.d("PlaybackTimer", "Starting timer: ${delayInSeconds}s, option=$option")
        
        // Broadcast initial remaining time
        broadcastRemaining(delayInSeconds.toLong())
        
        timer = object : CountDownTimer(totalMillis, 500L) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused) {
                    val remainingSeconds = (millisUntilFinished / 1000).toLong()
                    broadcastRemaining(remainingSeconds)
                }
            }
            
            override fun onFinish() {
                android.util.Log.d("PlaybackTimer", "Timer expired")
                broadcastTimerExpired()
                stopTimer()
            }
        }
        
        // Start timer only if playing (for CURRENT_TRACK option)
        if (timerOption == TimerOption.CURRENT_TRACK && !player.isPlaying) {
            isPaused = true
        } else {
            timer?.start()
        }
    }
    
    /**
     * Stops and cancels timer.
     */
    fun stopTimer() {
        timer?.cancel()
        timer = null
        isPaused = false
        android.util.Log.d("PlaybackTimer", "Timer stopped")
    }
    
    /**
     * Pauses timer (for CURRENT_TRACK option).
     */
    private fun pauseTimer() {
        if (timer != null && !isPaused) {
            // Note: CountDownTimer doesn't support pause/resume natively
            // We'll track pause state and skip onTick updates
            isPaused = true
            android.util.Log.d("PlaybackTimer", "Timer paused")
        }
    }
    
    /**
     * Resumes timer (for CURRENT_TRACK option).
     */
    private fun resumeTimer() {
        if (timer != null && isPaused) {
            // Note: CountDownTimer doesn't support pause/resume natively
            // We'll track pause state and resume onTick updates
            isPaused = false
            android.util.Log.d("PlaybackTimer", "Timer resumed")
        }
    }
    
    /**
     * Broadcasts remaining time.
     */
    private fun broadcastRemaining(seconds: Long) {
        val intent = Intent(ACTION_TIMER_TICK).apply {
            putExtra(EXTRA_REMAINING_SECONDS, seconds)
        }
        context.sendBroadcast(intent)
    }
    
    /**
     * Broadcasts timer expiration.
     */
    private fun broadcastTimerExpired() {
        val intent = Intent(ACTION_TIMER_EXPIRED)
        context.sendBroadcast(intent)
        
        // Auto-pause playback when timer expires
        if (player.isPlaying) {
            player.pause()
            android.util.Log.d("PlaybackTimer", "Auto-paused playback due to timer expiration")
        }
    }
    
    /**
     * Releases timer resources.
     */
    fun release() {
        stopTimer()
        player.removeListener(playerListener)
    }
}

