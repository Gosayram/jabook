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

/**
 * P-78: Manages playback interruption and resume for phone calls.
 *
 * Extracted from the monolithic PhoneCallListener (11k lines).
 * This class handles only the pause/resume logic, leaving
 * telephony state observation to TelephonyEventHandler.
 *
 * @param onShouldPause Called when playback should pause
 * @param onShouldResume Called when playback should resume
 */
public class PlaybackInterruptor(
    private val onShouldPause: () -> Unit,
    private val onShouldResume: () -> Unit,
) {
    private var wasPlayingBeforeInterrupt = false
    private var isInterrupted = false

    /**
     * Called when an interruption starts (phone call, alarm, etc.).
     * Saves the current playback state and pauses if playing.
     *
     * @param isPlaying Whether playback is currently active
     * @param reason Description of the interruption
     */
    public fun onInterruptionStarted(
        isPlaying: Boolean,
        reason: String = "phone_call",
    ) {
        if (isInterrupted) {
            LogUtils.w(TAG, "Already interrupted, ignoring: $reason")
            return
        }

        wasPlayingBeforeInterrupt = isPlaying
        isInterrupted = true

        if (isPlaying) {
            LogUtils.d(TAG, "Pausing for interruption: $reason")
            onShouldPause()
        } else {
            LogUtils.d(TAG, "Interruption while paused: $reason")
        }
    }

    /**
     * Called when the interruption ends.
     * Resumes playback if it was playing before the interruption.
     *
     * @param reason Description of the interruption end
     */
    public fun onInterruptionEnded(reason: String = "phone_call_ended") {
        if (!isInterrupted) {
            LogUtils.w(TAG, "Not interrupted, ignoring end: $reason")
            return
        }

        isInterrupted = false

        if (wasPlayingBeforeInterrupt) {
            LogUtils.d(TAG, "Resuming after interruption: $reason")
            onShouldResume()
        } else {
            LogUtils.d(TAG, "Was not playing before interruption, no resume needed")
        }

        wasPlayingBeforeInterrupt = false
    }

    /**
     * Whether currently in an interrupted state.
     */
    public fun isInterrupted(): Boolean = isInterrupted

    /**
     * Whether playback was active before the current interruption.
     */
    public fun wasPlayingBeforeInterrupt(): Boolean = wasPlayingBeforeInterrupt

    /**
     * Resets all state (e.g., on service destroy).
     */
    public fun reset() {
        wasPlayingBeforeInterrupt = false
        isInterrupted = false
    }

    public companion object {
        private const val TAG = "PlaybackInterruptor"
    }
}
