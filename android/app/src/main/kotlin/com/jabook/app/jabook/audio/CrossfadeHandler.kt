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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal fun canCrossfadeForRepeatMode(repeatMode: Int): Boolean = repeatMode != Player.REPEAT_MODE_ONE

/**
 * Handles crossfade scheduling and monitoring.
 */
internal class CrossfadeHandler(
    private val service: AudioPlayerService,
    private val crossFadePlayer: CrossFadePlayer,
    private val playlistManager: PlaylistManager,
) {
    private val checkIntervalMs = 500L
    private var monitoringJob: Job? = null
    private val monitoringGeneration = AtomicLong(0L)
    private val transitionTriggerInFlight = AtomicBoolean(false)

    public fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringGeneration.incrementAndGet()
        prefetchedChapterIndex = -1
        lastSeenChapterIndex = -1
        monitoringJob =
            service.playerServiceScope.launch {
                while (true) {
                    checkCrossfade()
                    delay(checkIntervalMs)
                }
            }
    }

    public fun stopMonitoring() {
        monitoringGeneration.incrementAndGet()
        monitoringJob?.cancel()
        monitoringJob = null
    }

    private var prefetchedChapterIndex = -1
    private var lastSeenChapterIndex = -1

    private fun checkCrossfade() {
        if (crossFadePlayer.isTransitionRunning()) return

        val currentPlayer = service.getActivePlayer()
        if (!currentPlayer.isPlaying) return
        if (!canCrossfadeForRepeatMode(currentPlayer.repeatMode)) return

        val duration = currentPlayer.duration
        val position = currentPlayer.currentPosition
        if (duration == androidx.media3.common.C.TIME_UNSET) return

        val currentChapterIndex = playlistManager.actualTrackIndex
        if (currentChapterIndex != lastSeenChapterIndex) {
            lastSeenChapterIndex = currentChapterIndex
            prefetchedChapterIndex = -1
        }

        val remaining = duration - position

        if (remaining <= PREDICTIVE_PREFETCH_WINDOW_MS && remaining > 0) {
            prefetchNextChapter()
        }

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

        val requestGeneration = monitoringGeneration.get()
        service.playerServiceScope.launch {
            val nextSource = playlistManager.getNextMediaSource(currentChapterIndex)
            if (nextSource != null) {
                if (!isCurrentRequest(requestGeneration, currentPlayer, currentChapterIndex)) {
                    return@launch
                }
                crossFadePlayer.setNextMediaSource(nextSource)
            }
        }
    }

    private companion object {
        private const val PREDICTIVE_PREFETCH_WINDOW_MS = 30_000L
    }

    /**
     * Triggers crossfade transition.
     */
    public fun triggerCrossfadeTransition() {
        if (crossFadePlayer.isTransitionRunning() || transitionTriggerInFlight.get()) return

        val currentPlayer = service.getActivePlayer()
        val currentChapterIndex = playlistManager.actualTrackIndex
        val nextChapterIndex = currentChapterIndex + 1
        val paths = playlistManager.currentFilePaths ?: return
        if (nextChapterIndex !in paths.indices) return
        val requestGeneration = monitoringGeneration.get()
        transitionTriggerInFlight.set(true)
        service.playerServiceScope.launch {
            try {
                // Only the next chapter is built here; the full queue is restored
                // after the swap so absolute chapter indices keep working.
                val nextSource = playlistManager.getNextMediaSource(currentChapterIndex)
                if (nextSource == null) return@launch

                if (!isCurrentRequest(requestGeneration, currentPlayer, currentChapterIndex)) {
                    return@launch
                }
                crossFadePlayer.setNextMediaSources(listOf(nextSource), 0)
                val completionGeneration = monitoringGeneration.get()
                crossFadePlayer.startCrossFade {
                    if (monitoringGeneration.get() == completionGeneration &&
                        playlistManager.actualTrackIndex == currentChapterIndex
                    ) {
                        playlistManager.actualTrackIndex = nextChapterIndex
                        restoreQueueAfterCrossfade(nextChapterIndex)
                    }
                }
            } finally {
                transitionTriggerInFlight.set(false)
            }
        }
    }

    /**
     * Re-adds all chapters except [activeChapterIndex] to the incoming player after a
     * single-source crossfade swap, restoring absolute timeline indices. Mirrors the
     * book-switch crossfade queue fill in AudioPlayerService.
     */
    private fun restoreQueueAfterCrossfade(activeChapterIndex: Int) {
        val paths = playlistManager.currentFilePaths ?: return
        val playlistItems = playlistManager.currentPlaylistItems ?: return
        val metadata = playlistManager.currentMetadata
        val player = service.getActivePlayer()
        for (index in PlaylistSessionStatePolicy.crossfadeRemainingSourceIndices(paths.size, activeChapterIndex)) {
            val source = playlistManager.createMediaSourceForItems(playlistItems, index, metadata)
            if (source != null) {
                if (index < activeChapterIndex) {
                    player.addMediaSource(0, source)
                } else {
                    player.addMediaSource(source)
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
            activeGeneration = monitoringGeneration.get(),
            requestGeneration = requestGeneration,
            activePlayer = activePlayer,
            requestPlayer = requestPlayer,
            activePlaylistIndex = playlistManager.actualTrackIndex,
            requestPlaylistIndex = requestChapterIndex,
        )
    }
}
