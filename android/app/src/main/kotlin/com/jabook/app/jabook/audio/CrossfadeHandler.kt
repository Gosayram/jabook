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

import android.os.Handler
import android.os.Looper

/**
 * Handles crossfade scheduling and monitoring.
 */
internal class CrossfadeHandler(
    private val service: AudioPlayerService,
    private val crossFadePlayer: CrossFadePlayer,
    private val playlistManager: PlaylistManager,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val checkIntervalMs = 500L
    private var isMonitoring = false

    private val monitorRunnable =
        object : Runnable {
            override fun run() {
                if (!isMonitoring) return

                checkCrossfade()
                handler.postDelayed(this, checkIntervalMs)
            }
        }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    private fun checkCrossfade() {
        // Only if crossfade enabled
        val settings = service.playerConfigurator?.audioProcessingSettings ?: return
        if (!settings.isCrossfadeEnabled) return

        val currentPlayer = service.getActivePlayer()
        if (!currentPlayer.isPlaying) return

        val duration = currentPlayer.duration
        val position = currentPlayer.currentPosition
        if (duration == androidx.media3.common.C.TIME_UNSET) return

        val remaining = duration - position
        val crossfadeDuration = settings.crossfadeDurationMs

        // If within crossfade window AND next track not yet started
        if (remaining <= crossfadeDuration && remaining > 0) {
            // Trigger crossfade if not already fading
            // But we need to ensure next track is ready via CrossFadePlayer
            // logic is handled inside service/playlist manager interaction
            service.triggerCrossfadeTransition()
        }
    }
}
