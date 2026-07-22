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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    public fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    public fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    private var prefetchedChapterIndex = -1

    private fun checkCrossfade() {
        val currentPlayer = service.getActivePlayer()
        if (!currentPlayer.isPlaying) return

        val duration = currentPlayer.duration
        val position = currentPlayer.currentPosition
        if (duration == androidx.media3.common.C.TIME_UNSET) return

        val remaining = duration - position

        if (remaining <= PREDICTIVE_PREFETCH_WINDOW_MS && remaining > 0) {
            prefetchNextChapter()
        }

        // Existing crossfade logic (wrapped in the settings check)
        val settings = service.playerConfigurator?.audioProcessingSettings ?: return
        if (settings.isCrossfadeEnabled) {
            val crossfadeDuration = settings.crossfadeDurationMs
            if (remaining <= crossfadeDuration && remaining > 0) {
                triggerCrossfadeTransition()
            }
        }
    }

    private fun prefetchNextChapter() {
        val currentPlayer = service.getActivePlayer()
        val nextIndex = currentPlayer.currentMediaItemIndex + 1
        if (nextIndex <= prefetchedChapterIndex) return
        prefetchedChapterIndex = nextIndex

        if (nextIndex >= currentPlayer.mediaItemCount) return

        service.playerServiceScope.launch {
            val nextSource = playlistManager.getNextMediaSource(currentPlayer.currentMediaItemIndex)
            if (nextSource != null) {
                withContext(Dispatchers.Main) {
                    currentPlayer.addMediaSource(nextSource)
                }
            }
        }
    }

    private companion object {
        private const val PREDICTIVE_PREFETCH_WINDOW_MS = 30_000L
    }

    /**
     * Triggers crossfade transition.
     * Prepares next track on secondary player and starts crossfade.
     */
    public fun triggerCrossfadeTransition() {
        service.playerServiceScope.launch {
            val currentPlayer = service.getActivePlayer()
            val nextSource = playlistManager.getNextMediaSource(currentPlayer.currentMediaItemIndex)

            if (nextSource != null) {
                withContext(Dispatchers.Main) {
                    crossFadePlayer.setNextMediaSource(nextSource)
                    crossFadePlayer.startCrossFade()
                }
            }
        }
    }
}
