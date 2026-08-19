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
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun canCrossfadeForRepeatMode(repeatMode: Int): Boolean = repeatMode != Player.REPEAT_MODE_ONE

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
    private var transitionTriggerInFlight = false

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
        lastSeenChapterIndex = -1
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    public fun stopMonitoring() {
        isMonitoring = false
        monitoringGeneration += 1L
        handler.removeCallbacks(monitorRunnable)
    }

    private var prefetchedChapterIndex = -1
    private var lastSeenChapterIndex = -1

    private fun checkCrossfade() {
        // While a transition runs, players are mid-swap: re-triggering here would rebuild
        // media sources for the whole book just to be rejected by staleness checks.
        if (crossFadePlayer.isTransitionRunning()) return

        val currentPlayer = service.getActivePlayer()
        if (!currentPlayer.isPlaying) return
        if (!canCrossfadeForRepeatMode(currentPlayer.repeatMode)) return

        val duration = currentPlayer.duration
        val position = currentPlayer.currentPosition
        if (duration == androidx.media3.common.C.TIME_UNSET) return

        val currentChapterIndex = playlistManager.actualTrackIndex
        if (currentChapterIndex != lastSeenChapterIndex) {
            // Playback moved (seek or chapter change): re-enable prefetch of the new neighbour.
            lastSeenChapterIndex = currentChapterIndex
            prefetchedChapterIndex = -1
        }

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
        if (!isCrossfadeEnabled()) return

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
                    crossFadePlayer.setNextMediaSource(nextSource)
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
        // One in-flight transition at a time: an already-running swap or a trigger whose
        // sources are still being built must not fire again on the next 500ms tick.
        if (crossFadePlayer.isTransitionRunning() || transitionTriggerInFlight) return

        val currentPlayer = service.getActivePlayer()
        val currentChapterIndex = playlistManager.actualTrackIndex
        val nextChapterIndex = currentChapterIndex + 1
        val paths = playlistManager.currentFilePaths ?: return
        if (nextChapterIndex !in paths.indices) return
        val metadata = playlistManager.currentMetadata
        val requestGeneration = monitoringGeneration
        transitionTriggerInFlight = true
        service.playerServiceScope.launch {
            try {
                val sources =
                    paths.mapIndexedNotNull { index, _ ->
                        playlistManager.createMediaSource(paths, index, metadata)
                    }

                if (sources.size == paths.size) {
                    withContext(Dispatchers.Main) {
                        if (!isCurrentRequest(requestGeneration, currentPlayer, currentChapterIndex)) {
                            return@withContext
                        }
                        crossFadePlayer.setNextMediaSources(sources, nextChapterIndex)
                        val completionGeneration = monitoringGeneration
                        crossFadePlayer.startCrossFade {
                            if (monitoringGeneration == completionGeneration &&
                                playlistManager.actualTrackIndex == currentChapterIndex
                            ) {
                                playlistManager.actualTrackIndex = nextChapterIndex
                            }
                        }
                    }
                }
            } finally {
                transitionTriggerInFlight = false
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
