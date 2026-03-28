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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Applies transient ducking when playback is suppressed by audio focus and restores volume smoothly on gain.
 */
internal class AudioFocusDuckingController(
    private val getActivePlayer: () -> ExoPlayer,
    private val scope: CoroutineScope?,
    private val onDuckApplied: (() -> Unit)? = null,
    private val duckVolume: Float = 0.2f,
    private val restoreDurationMs: Long = 500L,
    private val restoreSteps: Int = 10,
) {
    private var restoreJob: Job? = null
    private var isDucked: Boolean = false
    private var volumeBeforeDuck: Float = 1.0f

    fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
            applyDuck()
        } else {
            restoreIfNeeded()
        }
    }

    fun release() {
        restoreJob?.cancel()
        restoreJob = null
        if (isDucked) {
            getActivePlayer().volume = volumeBeforeDuck.coerceIn(0f, 1f)
            isDucked = false
        }
    }

    private fun applyDuck() {
        restoreJob?.cancel()
        restoreJob = null

        val player = getActivePlayer()
        val currentVolume = player.volume.coerceIn(0f, 1f)
        val wasDucked = isDucked
        if (!wasDucked) {
            volumeBeforeDuck = currentVolume
        }

        val targetVolume = minOf(currentVolume, duckVolume.coerceIn(0f, 1f))
        if (targetVolume < currentVolume) {
            player.volume = targetVolume
        }
        isDucked = true
        if (!wasDucked) {
            onDuckApplied?.invoke()
        }
    }

    private fun restoreIfNeeded() {
        if (!isDucked) {
            return
        }

        val player = getActivePlayer()
        val startVolume = player.volume.coerceIn(0f, 1f)
        val targetVolume = volumeBeforeDuck.coerceIn(0f, 1f)
        isDucked = false

        if (scope == null || restoreDurationMs <= 0L || restoreSteps <= 0 || startVolume == targetVolume) {
            player.volume = targetVolume
            return
        }

        val stepDelayMs = (restoreDurationMs / restoreSteps).coerceAtLeast(1L)
        restoreJob?.cancel()
        restoreJob =
            scope.launch {
                for (step in 1..restoreSteps) {
                    val progress = step.toFloat() / restoreSteps.toFloat()
                    player.volume = startVolume + (targetVolume - startVolume) * progress
                    if (step < restoreSteps) {
                        delay(stepDelayMs)
                    }
                }
            }
    }
}
