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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.audio.processors.AudioProcessorFactory
import com.jabook.app.jabook.audio.processors.LoudnessNormalizer
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages player configuration, including:
 * - Creating and configuring the PlayerListener
 * - Handling AudioProcessingSettings and creating custom ExoPlayers with processors
 * - Managing player recreation and state restoration
 */
internal class PlayerConfigurator(
    private val service: AudioPlayerService,
) {
    private var offloadListenerTarget: ExoPlayer? = null
    private val audioOffloadListener =
        object : ExoPlayer.AudioOffloadListener {
            override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Audio offload scheduling changed: sleepingForOffload=$isSleepingForOffload",
                )
            }

            override fun onOffloadedPlayback(isOffloadedPlayback: Boolean) {
                service.audioVisualizerManager?.setSuspendedForAudioOffload(isOffloadedPlayback)
                service.audioVisualizerStateBridge.updateIsAudioOffloaded(isOffloadedPlayback)
                LogUtils.d(
                    "AudioPlayerService",
                    "Audio offload playback changed: isOffloadedPlayback=$isOffloadedPlayback",
                )
            }
        }

    /**
     * Player event listener instance.
     */
    var playerListener: PlayerListener? = null
        private set
    private var playerListenerTarget: ExoPlayer? = null

    /**
     * Custom ExoPlayer instance with AudioProcessors.
     */
    var customExoPlayer: ExoPlayer? = null
        private set

    /**
     * Active LoudnessNormalizer instance (if available).
     */
    var loudnessNormalizer: LoudnessNormalizer? = null

    private val loudnessNormalizers = mutableMapOf<ExoPlayer, LoudnessNormalizer?>()

    /**
     * Audio underrun monitor (BP-13.1).
     * Tracks AudioTrack underruns via AnalyticsListener and reports bursts.
     */
    private var underrunMonitor: AudioUnderrunMonitor? = null

    /**
     * Current audio processing settings.
     */
    var audioProcessingSettings: AudioProcessingSettings = AudioProcessingSettings()
        private set

    /**
     * Gets the active ExoPlayer instance (custom with processors or singleton).
     * @param defaultPlayer The singleton ExoPlayer instance to return if no custom player exists
     */
    public fun getActivePlayer(defaultPlayer: ExoPlayer): ExoPlayer = customExoPlayer ?: defaultPlayer

    /**
     * Configures ExoPlayer instance (already created via Hilt).
     *
     * ExoPlayer is provided as singleton via Dagger Hilt MediaModule.
     * LoadControl and AudioAttributes are already configured in MediaModule.
     * This method only adds listener and configures additional settings.
     *
     * Inspired by lissen-android: lightweight configuration, no heavy operations.
     */
    public fun configurePlayer() {
        try {
            // Match lissen-android: just add listener, no additional configuration
            // ExoPlayer is already configured in MediaModule with AudioAttributes
            val activePlayer = service.getActivePlayer()

            // Create PlayerListener with dependencies
            playerListener =
                PlayerListener(
                    context = service,
                    getActivePlayer = { service.getActivePlayer() },
                    // getNotificationManager callback removed - MediaSession handles updates automatically
                    getIsBookCompleted = { service.playlistManager?.isBookCompleted ?: false },
                    setIsBookCompleted = { service.playlistManager?.isBookCompleted = it },
                    getSleepTimerEndOfChapter = { service.sleepTimerManager?.sleepTimerEndOfChapter ?: false },
                    getSleepTimerEndOfTrack = { service.sleepTimerManager?.sleepTimerEndOfTrack ?: false },
                    cancelSleepTimer = { service.sleepTimerManager?.cancelSleepTimer() },
                    sendTimerExpiredEvent = { service.sleepTimerManager?.notifyTimerExpired() },
                    markSleepTimerPause = {
                        service.playbackController?.markSleepTimerPause()
                        service.markStoppedBySleepTimer()
                    },
                    saveCurrentPosition = { service.saveCurrentPosition() },
                    getEmbeddedArtworkPath = { service.embeddedArtworkPath },
                    setEmbeddedArtworkPath = { service.embeddedArtworkPath = it },
                    getCurrentMetadata = { service.playlistManager?.currentMetadata },
                    setLastCompletedTrackIndex = { index ->
                        service.lastCompletedTrackIndex = index
                    }, // Delegated to PlaylistManager via Service property
                    getLastCompletedTrackIndex = { service.lastCompletedTrackIndex }, // Delegated
                    getActualPlaylistSize = { service.playlistManager?.currentFilePaths?.size ?: 0 },
                    updateActualTrackIndex = { index -> service.updateActualTrackIndex(index) },
                    isPlaylistLoading = { service.playlistManager?.isPlaylistLoading ?: false },
                    updateLastPlayedTimestamp = { bookId ->
                        service.playerServiceScope.launch {
                            service.playerPersistenceManager.updateLastPlayed(bookId)
                        }
                    },
                    markBookCompleted = { bookId ->
                        service.playerServiceScope.launch {
                            service.playerPersistenceManager.markCompleted(bookId)
                            service.playerPersistenceManager.incrementPlayCount(bookId)
                        }
                    },
                    getCurrentBookId = { service.currentGroupPath },
                    preloadNextTrack = { nextIndex ->
                        // Preload next track for smooth transition (inspired by Easybook)
                        service.playlistManager?.preloadNextTrack(nextIndex)
                    },
                    optimizeMemoryUsage = { currentIndex ->
                        // Optimize memory usage for large playlists (inspired by Easybook)
                        service.playlistManager?.optimizeMemoryUsage(currentIndex)
                    },
                    updateAudioVisualizer = { audioSessionId ->
                        // Update audio visualizer when session ID changes (following Rhythm pattern)
                        service.audioVisualizerManager?.initialize(audioSessionId)
                        // Re-attach EQ + notify external EQ apps: AudioEffect control resets
                        // on every new AudioTrack (e.g. BT routing change) — without this the
                        // equalizer stays bound to a dead session (Gramophone pattern).
                        service.audioEqualizerManager.attachToAudioSession(audioSessionId)
                        service.broadcastAudioEffectSession(audioSessionId)
                    },
                    getCrossfadeHandler = { service.crossfadeHandler },
                    coroutineScope = service.playerServiceScope, // Pass coroutine scope for debounce
                    onIsPlayingChanged = { isPlaying -> service.onPlaybackIsPlayingChanged(isPlaying) },
                    onTerminalPlaybackError = service::reportTerminalPlaybackError,
                )

            playerListener?.let {
                activePlayer.addListener(it)
                playerListenerTarget = activePlayer
            }

            // BP-13.1: Register audio underrun monitor
            underrunMonitor = AudioUnderrunMonitor(activePlayer).also { it.register() }
            registerAudioOffloadListener(activePlayer)

            // Match lissen-android: don't set WakeMode or ScrubbingMode
            // These may interfere with AudioFocus handling

            // Initialize repeat and shuffle modes (lissen-android doesn't set these either, but it's safe)
            activePlayer.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer.shuffleModeEnabled = false

            LogUtils.d("AudioPlayerService", "ExoPlayer configured (provided via Hilt)")
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to configure ExoPlayer", e)
            throw e
        }
    }

    /**
     * Configures ExoPlayer with AudioProcessors based on settings.
     *
     * In Media3, AudioProcessors must be set during ExoPlayer creation.
     * This method creates a new ExoPlayer instance with processors if needed,
     * or uses the singleton ExoPlayer if no processing is required.
     *
     * @param settings Audio processing settings
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    public fun configureExoPlayer(settings: AudioProcessingSettings) {
        try {
            // Snapshot before changing routing: enabling crossfade makes PlayerFacade
            // resolve the initially empty CrossFadePlayer instead of the current player.
            val activePlayer = service.getActivePlayer()

            // Create processor chain — pass the device output buffer size so
            // SkipSilenceAudioProcessor can align silence-transition boundaries.
            val chainResult =
                AudioProcessorFactory.createProcessorChain(
                    settings,
                    AudioOutputBufferInfo.outputFramesPerBuffer(service),
                )
            val processors = chainResult.processors
            loudnessNormalizer = chainResult.loudnessNormalizer

            // Publish the new routing only after the current player was captured.
            this.audioProcessingSettings = settings

            LogUtils.d(
                "AudioPlayerService",
                "Audio processing settings updated: " +
                    "normalizeVolume=${settings.normalizeVolume}, " +
                    "volumeBoost=${settings.volumeBoostLevel}, " +
                    "drc=${settings.drcLevel}, " +
                    "speechEnhancer=${settings.speechEnhancer}, " +
                    "autoLeveling=${settings.autoVolumeLeveling}, " +
                    "processors=${processors.size}, " +
                    "hasNormalizer=${loudnessNormalizer != null}",
            )

            // Save current playback state before recreating player
            // BUT only if playlist is not currently loading (prevent saving stale state)
            val wasPlaying = activePlayer.isPlaying
            val currentIndex = activePlayer.currentMediaItemIndex
            val currentPosition = activePlayer.currentPosition
            val hasPlaylist = activePlayer.mediaItemCount > 0
            val playlistManager =
                service.playlistManager ?: run {
                    LogUtils.w(
                        "AudioPlayerService",
                        "PlaylistManager is null, skipping playback state save/restore during player reconfiguration",
                    )
                    return
                }

            // Save state if we have a playlist AND playlist is not currently loading
            // This prevents saving incorrect state when a new playlist is being set
            val filePathsForSave = playlistManager.currentFilePaths
            val isPlaylistLoading = playlistManager.isPlaylistLoading

            if (hasPlaylist && filePathsForSave != null && filePathsForSave.isNotEmpty() && !isPlaylistLoading) {
                playlistManager.savedPlaybackState =
                    SavedPlaybackState(
                        currentIndex = currentIndex,
                        currentPosition = currentPosition,
                        isPlaying = wasPlaying,
                    )
                LogUtils.d(
                    "AudioPlayerService",
                    "Saved playback state before player recreation: index=$currentIndex, position=$currentPosition, isPlaying=$wasPlaying",
                )
            } else if (isPlaylistLoading) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Skipping state save: playlist is currently loading (index=$currentIndex would be stale)",
                )
            }

            val crossFadePlayer = service.crossFadePlayer
            if (settings.isCrossfadeEnabled && crossFadePlayer != null) {
                val normalizers = mutableMapOf<ExoPlayer, LoudnessNormalizer?>()
                var firstChain: AudioProcessorFactory.ProcessorChainResult? = chainResult

                crossFadePlayer.recreatePlayers(
                    factory = { context, handleAudioFocus ->
                        val chain =
                            firstChain
                                ?: AudioProcessorFactory.createProcessorChain(
                                    settings,
                                    AudioOutputBufferInfo.outputFramesPerBuffer(context),
                                )
                        firstChain = null
                        MediaModule
                            .createExoPlayerWithProcessors(
                                context = context,
                                settings = settings,
                                handleAudioFocus = handleAudioFocus,
                                processorChain = chain,
                            ).also { player ->
                                normalizers[player] = chain.loudnessNormalizer
                            }
                    },
                    sourcePlayer = activePlayer,
                )
                releaseCustomExoPlayer()
                loudnessNormalizers.clear()
                loudnessNormalizers.putAll(normalizers)
                loudnessNormalizer = loudnessNormalizers[crossFadePlayer.getActivePlayer()]
                playerListener?.loudnessNormalizer = loudnessNormalizer
                LogUtils.i("AudioPlayerService", "Recreated crossfade players with AudioProcessors")
            } else {
                if (crossFadePlayer?.getActivePlayer() === activePlayer) {
                    crossFadePlayer.pause()
                }

                if (processors.isNotEmpty()) {
                    releaseCustomExoPlayer()

                    // Create new ExoPlayer with processors
                    customExoPlayer =
                        MediaModule.createExoPlayerWithProcessors(
                            context = service,
                            settings = settings,
                            processorChain = chainResult,
                        )
                    loudnessNormalizers.clear()
                    loudnessNormalizers[customExoPlayer!!] = loudnessNormalizer
                    registerAudioOffloadListener(customExoPlayer)

                    // Copy listener from singleton player (using instance from this class)
                    playerListener?.let {
                        it.loudnessNormalizer = loudnessNormalizer // Update listener with new normalizer
                    }

                    LogUtils.i(
                        "AudioPlayerService",
                        "Created custom ExoPlayer with ${processors.size} AudioProcessors",
                    )
                } else {
                    releaseCustomExoPlayer()
                    loudnessNormalizers.clear()
                    loudnessNormalizer = null
                    playerListener?.loudnessNormalizer = null
                    registerAudioOffloadListener(service.exoPlayer)
                    LogUtils.d("AudioPlayerService", "No processors needed, using singleton ExoPlayer")
                }
            }

            // NotificationManager removed - MediaSession handles notification updates automatically
            // service.notificationManager?.updatePlayer(service.getActivePlayer())
            service.rebindActivePlayer()
            LogUtils.d("AudioPlayerService", "Player recreation complete (MediaSession handles notifications)")

            // Restore playlist and position if we had a playlist before
            // BUT only if we're not already loading a playlist (prevent conflicts)
            // CRITICAL: Also check if playlist was loaded recently (within 2 seconds) - if so, don't restore stale state
            // This prevents restoration of stale state after a new playlist loads
            val lastPlaylistLoadTime: Long = playlistManager.lastPlaylistLoadTime
            val timeSinceLastLoad: Long = System.currentTimeMillis() - lastPlaylistLoadTime
            val wasRecentlyLoaded = timeSinceLastLoad < 2000L // 2 seconds

            val savedStateForRestore = playlistManager.savedPlaybackState
            val filePathsForRestore = playlistManager.currentFilePaths

            if (savedStateForRestore != null &&
                filePathsForRestore != null &&
                filePathsForRestore.isNotEmpty() &&
                !isPlaylistLoading
            ) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Restoring playlist and position: ${filePathsForRestore.size} items, index=${savedStateForRestore.currentIndex}, position=${savedStateForRestore.currentPosition}",
                )

                // CRITICAL: Initialize actualTrackIndex from saved state
                // We access playlistManager.actualTrackIndex directly or via service method if needed, but safer via manager
                playlistManager.actualTrackIndex =
                    savedStateForRestore.currentIndex.coerceIn(0, filePathsForRestore.size - 1)
                LogUtils.d(
                    "AudioPlayerService",
                    "Initialized actualTrackIndex to ${playlistManager.actualTrackIndex} (from savedState.currentIndex=${savedStateForRestore.currentIndex})",
                )

                // Mark as loading to prevent conflicts
                playlistManager.isPlaylistLoading = true
                playlistManager.currentLoadingPlaylist = filePathsForRestore

                // Restore playlist asynchronously
                service.playerServiceScope.launch {
                    try {
                        playlistManager.preparePlaybackOptimized(
                            filePaths = filePathsForRestore,
                            metadata = playlistManager.currentMetadata,
                            initialTrackIndex = savedStateForRestore.currentIndex,
                            initialPosition = savedStateForRestore.currentPosition,
                        )

                        // Position is already applied in preparePlaybackOptimized when the target
                        // track is the first loaded track, so wait for READY then restore.
                        var attempts = 0
                        while (attempts < 50) {
                            val newPlayer = service.getActivePlayer()
                            if (newPlayer.playbackState == Player.STATE_READY ||
                                newPlayer.playbackState == Player.STATE_BUFFERING
                            ) {
                                break
                            }
                            delay(100L)
                            attempts++
                        }

                        LogUtils.d(
                            "AudioPlayerService",
                            "Position applied in preparePlaybackOptimized: index=${savedStateForRestore.currentIndex}, position=${savedStateForRestore.currentPosition}",
                        )

                        // Restore playback state
                        val newPlayer = service.getActivePlayer()
                        if (savedStateForRestore.isPlaying) {
                            newPlayer.playWhenReady = true
                            LogUtils.d("AudioPlayerService", "Restored playback: playing")
                        }

                        // Clear saved state
                        playlistManager.savedPlaybackState = null

                        // MediaLibraryService automatically updates notification when Player state changes
                    } catch (e: Exception) {
                        LogUtils.e(
                            "AudioPlayerService",
                            "Failed to restore playlist after player recreation",
                            e,
                        )
                        playlistManager.savedPlaybackState = null
                    } finally {
                        // Clear loading flag when done
                        playlistManager.isPlaylistLoading = false
                        playlistManager.currentLoadingPlaylist = null
                    }
                }
            } else if (wasRecentlyLoaded) {
                // Only log if we have state but chose not to restore it (which shouldn't happen now as we restore if state exists)
                // But if savedStateForRestore is null, we might still log this context
                LogUtils.d(
                    "AudioPlayerService",
                    "No saved state to restore (playlist loaded ${timeSinceLastLoad}ms ago), using provided position",
                )
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to configure ExoPlayer with processors", e)
        }
    }

    public fun release() {
        unregisterAudioOffloadListener(service.exoPlayer)
        unregisterAudioOffloadListener(customExoPlayer)

        // BP-13.1: Unregister underrun monitor
        underrunMonitor?.unregister()
        underrunMonitor = null

        playerListener?.let { listener ->
            try {
                service.exoPlayer.removeListener(listener)
            } catch (e: Exception) {
                LogUtils.w("PlayerConfigurator", "Failed to remove listener from singleton ExoPlayer", e)
            }
            try {
                customExoPlayer?.removeListener(listener)
            } catch (e: Exception) {
                LogUtils.w("PlayerConfigurator", "Failed to remove listener from custom ExoPlayer", e)
            }
            listener.release()
        }
        customExoPlayer?.release()
        customExoPlayer = null
        playerListenerTarget = null
        playerListener = null
    }

    fun rebindListeners(activePlayer: ExoPlayer) {
        if (playerListenerTarget === activePlayer) return
        playerListener?.let { listener ->
            playerListenerTarget?.removeListener(listener)
            activePlayer.addListener(listener)
        }
        underrunMonitor?.unregister()
        underrunMonitor = AudioUnderrunMonitor(activePlayer).also { it.register() }
        registerAudioOffloadListener(activePlayer)
        playerListenerTarget = activePlayer
        playerListener?.loudnessNormalizer = loudnessNormalizers[activePlayer]
    }

    private fun releaseCustomExoPlayer() {
        playerListener?.let { listener ->
            if (playerListenerTarget === customExoPlayer) {
                customExoPlayer?.removeListener(listener)
                playerListenerTarget = null
            }
        }
        unregisterAudioOffloadListener(customExoPlayer)
        loudnessNormalizers.remove(customExoPlayer)
        customExoPlayer?.release()
        customExoPlayer = null
    }

    private fun registerAudioOffloadListener(player: ExoPlayer?) {
        if (player == null) return
        if (offloadListenerTarget === player) return

        unregisterAudioOffloadListener(offloadListenerTarget)
        player.addAudioOffloadListener(audioOffloadListener)
        offloadListenerTarget = player
    }

    private fun unregisterAudioOffloadListener(player: ExoPlayer?) {
        if (player == null) return
        try {
            player.removeAudioOffloadListener(audioOffloadListener)
        } catch (e: Exception) {
            LogUtils.w("PlayerConfigurator", "Failed to unregister audio offload listener", e)
        } finally {
            if (offloadListenerTarget === player) {
                offloadListenerTarget = null
            }
        }
    }
}
