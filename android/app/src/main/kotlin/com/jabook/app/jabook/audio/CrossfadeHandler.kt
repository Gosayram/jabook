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
    private var monitoringGeneration = 0L

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
        monitoringGeneration += 1L
        prefetchedChapterIndex = -1
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    public fun stopMonitoring() {
        isMonitoring = false
        monitoringGeneration += 1L
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
        val currentChapterIndex = playlistManager.actualTrackIndex
        val nextIndex = currentChapterIndex + 1
        if (nextIndex <= prefetchedChapterIndex) return
        prefetchedChapterIndex = nextIndex

        if (nextIndex >= (playlistManager.currentFilePaths?.size ?: 0)) return

        val requestGeneration = monitoringGeneration
        service.playerServiceScope.launch {
            val nextSource = playlistManager.getNextMediaSource(currentChapterIndex)
            if (nextSource != null) {
                withContext(Dispatchers.Main) {
                    if (!isCurrentRequest(requestGeneration, currentPlayer, currentChapterIndex)) {
                        return@withContext
                    }
                    if (isCrossfadeEnabled()) {
                        crossFadePlayer.setNextMediaSource(nextSource)
                    } else {
                        currentPlayer.addMediaSource(nextSource)
                    }
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
        val currentPlayer = service.getActivePlayer()
        val currentChapterIndex = playlistManager.actualTrackIndex
        val requestGeneration = monitoringGeneration
        service.playerServiceScope.launch {
            val nextSource = playlistManager.getNextMediaSource(currentChapterIndex)

            if (nextSource != null) {
                withContext(Dispatchers.Main) {
                    if (!isCurrentRequest(requestGeneration, currentPlayer, currentChapterIndex)) {
                        return@withContext
                    }
                    crossFadePlayer.setNextMediaSource(nextSource)
                    crossFadePlayer.startCrossFade {
                        if (playlistManager.actualTrackIndex == currentChapterIndex) {
                            playlistManager.actualTrackIndex = currentChapterIndex + 1
                        }
                    }
                }
            }
        }
    }

    private fun isCrossfadeEnabled(): Boolean = service.playerConfigurator?.audioProcessingSettings?.isCrossfadeEnabled == true

    private fun isCurrentRequest(
        requestGeneration: Long,
        requestPlayer: Any,
        requestChapterIndex: Int,
    ): Boolean {
        val activePlayer = service.getActivePlayer()
        return CrossfadeRequestStalenessPolicy.isCurrent(
            activeGeneration = monitoringGeneration,
            requestGeneration = requestGeneration,
            activePlayer = activePlayer,
            requestPlayer = requestPlayer,
            activePlaylistIndex = playlistManager.actualTrackIndex,
            requestPlaylistIndex = requestChapterIndex,
        )
    }
}
