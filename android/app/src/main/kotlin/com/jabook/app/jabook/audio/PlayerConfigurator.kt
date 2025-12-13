package com.jabook.app.jabook.audio

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.audio.processors.AudioProcessorFactory
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
    /**
     * Player event listener instance.
     */
    var playerListener: PlayerListener? = null
        private set

    /**
     * Custom ExoPlayer instance with AudioProcessors.
     */
    var customExoPlayer: ExoPlayer? = null
        private set

    /**
     * Current audio processing settings.
     */
    var audioProcessingSettings: AudioProcessingSettings = AudioProcessingSettings()
        private set

    /**
     * Gets the active ExoPlayer instance (custom with processors or singleton).
     * @param defaultPlayer The singleton ExoPlayer instance to return if no custom player exists
     */
    fun getActivePlayer(defaultPlayer: ExoPlayer): ExoPlayer = customExoPlayer ?: defaultPlayer

    /**
     * Configures ExoPlayer instance (already created via Hilt).
     *
     * ExoPlayer is provided as singleton via Dagger Hilt MediaModule.
     * LoadControl and AudioAttributes are already configured in MediaModule.
     * This method only adds listener and configures additional settings.
     *
     * Inspired by lissen-android: lightweight configuration, no heavy operations.
     */
    fun configurePlayer() {
        try {
            // Match lissen-android: just add listener, no additional configuration
            // ExoPlayer is already configured in MediaModule with AudioAttributes
            val activePlayer = service.getActivePlayer()

            // Create PlayerListener with dependencies
            playerListener =
                PlayerListener(
                    context = service,
                    getActivePlayer = { service.getActivePlayer() },
                    getNotificationManager = { service.notificationManager },
                    getIsBookCompleted = { service.playlistManager?.isBookCompleted ?: false },
                    setIsBookCompleted = { service.playlistManager?.isBookCompleted = it },
                    getSleepTimerEndOfChapter = { service.sleepTimerManager?.sleepTimerEndOfChapter ?: false },
                    getSleepTimerEndTime = { service.sleepTimerManager?.sleepTimerEndTime ?: 0L },
                    cancelSleepTimer = { service.sleepTimerManager?.cancelSleepTimer() },
                    sendTimerExpiredEvent = { /* Handled by SleepTimerManager */ },
                    saveCurrentPosition = { service.saveCurrentPosition() },
                    startSleepTimerCheck = { service.sleepTimerManager?.startSleepTimerCheck() },
                    getEmbeddedArtworkPath = { service.embeddedArtworkPath },
                    setEmbeddedArtworkPath = { service.embeddedArtworkPath = it },
                    getCurrentMetadata = { service.playlistManager?.currentMetadata },
                    setLastCompletedTrackIndex = { index ->
                        service.lastCompletedTrackIndex = index
                    }, // Delegated to PlaylistManager via Service property
                    getLastCompletedTrackIndex = { service.lastCompletedTrackIndex }, // Delegated
                    getActualPlaylistSize = { service.playlistManager?.currentFilePaths?.size ?: 0 },
                    // playbackPositionSaver removed - Flutter bridge no longer needed
                    updateActualTrackIndex = { index -> service.updateActualTrackIndex(index) },
                    isPlaylistLoading = { service.playlistManager?.isPlaylistLoading ?: false },
                    storeCurrentMediaItem = { }, // TODO: Flutter bridge removed
                )

            activePlayer.addListener(playerListener!!)

            // Match lissen-android: don't set WakeMode or ScrubbingMode
            // These may interfere with AudioFocus handling

            // Initialize repeat and shuffle modes (lissen-android doesn't set these either, but it's safe)
            activePlayer.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer.shuffleModeEnabled = false

            android.util.Log.d("AudioPlayerService", "ExoPlayer configured (provided via Hilt)")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to configure ExoPlayer", e)
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
    fun configureExoPlayer(settings: AudioProcessingSettings) {
        try {
            // Store settings
            audioProcessingSettings = settings

            // Create processor chain
            val processors = AudioProcessorFactory.createProcessorChain(settings)

            android.util.Log.d(
                "AudioPlayerService",
                "Audio processing settings updated: " +
                    "normalizeVolume=${settings.normalizeVolume}, " +
                    "volumeBoost=${settings.volumeBoostLevel}, " +
                    "drc=${settings.drcLevel}, " +
                    "speechEnhancer=${settings.speechEnhancer}, " +
                    "autoLeveling=${settings.autoVolumeLeveling}, " +
                    "processors=${processors.size}",
            )

            // Save current playback state before recreating player
            // BUT only if playlist is not currently loading (prevent saving stale state)
            val activePlayer = service.getActivePlayer()
            val wasPlaying = activePlayer.isPlaying
            val currentIndex = activePlayer.currentMediaItemIndex
            val currentPosition = activePlayer.currentPosition
            val hasPlaylist = activePlayer.mediaItemCount > 0
            val playlistManager = service.playlistManager

            // Save state if we have a playlist AND playlist is not currently loading
            // This prevents saving incorrect state when Flutter is setting a new playlist
            val filePathsForSave = playlistManager?.currentFilePaths
            val isPlaylistLoading = playlistManager?.isPlaylistLoading ?: false

            if (hasPlaylist && filePathsForSave != null && filePathsForSave.isNotEmpty() && !isPlaylistLoading) {
                playlistManager?.savedPlaybackState =
                    SavedPlaybackState(
                        currentIndex = currentIndex,
                        currentPosition = currentPosition,
                        isPlaying = wasPlaying,
                    )
                android.util.Log.d(
                    "AudioPlayerService",
                    "Saved playback state before player recreation: index=$currentIndex, position=$currentPosition, isPlaying=$wasPlaying",
                )
            } else if (isPlaylistLoading) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Skipping state save: playlist is currently loading (index=$currentIndex would be stale)",
                )
            }

            // If processors are needed, create custom ExoPlayer
            if (processors.isNotEmpty()) {
                // Release old custom player if exists
                customExoPlayer?.release()
                customExoPlayer = null

                // Create new ExoPlayer with processors
                customExoPlayer = MediaModule.createExoPlayerWithProcessors(service, settings)

                // Copy listener from singleton player (using instance from this class)
                playerListener?.let { customExoPlayer?.addListener(it) }
                // TODO: Flutter bridge removed - bridgePlayerListener not needed

                android.util.Log.i(
                    "AudioPlayerService",
                    "Created custom ExoPlayer with ${processors.size} AudioProcessors",
                )
            } else {
                // No processors needed, release custom player if exists
                customExoPlayer?.release()
                customExoPlayer = null
                android.util.Log.d("AudioPlayerService", "No processors needed, using singleton ExoPlayer")
            }

            // Update NotificationManager with new player reference
            service.notificationManager?.updatePlayer(service.getActivePlayer())
            android.util.Log.d("AudioPlayerService", "Updated NotificationManager with new player reference")

            // Restore playlist and position if we had a playlist before
            // BUT only if we're not already loading a playlist (prevent conflicts)
            // CRITICAL: Also check if playlist was loaded recently (within 2 seconds) - if so, don't restore stale state
            // This prevents restoration of incorrect state after Flutter loads correct playlist
            val lastPlaylistLoadTime = playlistManager?.lastPlaylistLoadTime ?: 0L
            val timeSinceLastLoad = System.currentTimeMillis() - lastPlaylistLoadTime
            val wasRecentlyLoaded = timeSinceLastLoad < 2000L // 2 seconds

            val savedStateForRestore = playlistManager?.savedPlaybackState
            val filePathsForRestore = playlistManager?.currentFilePaths

            if (savedStateForRestore != null &&
                filePathsForRestore != null &&
                filePathsForRestore.isNotEmpty() &&
                !isPlaylistLoading
            ) {
                android.util.Log.d(
                    "AudioPlayerService",
                    "Restoring playlist and position: ${filePathsForRestore.size} items, index=${savedStateForRestore.currentIndex}, position=${savedStateForRestore.currentPosition}",
                )

                // CRITICAL: Initialize actualTrackIndex from saved state
                // We access playlistManager.actualTrackIndex directly or via service method if needed, but safer via manager
                playlistManager?.actualTrackIndex =
                    savedStateForRestore.currentIndex.coerceIn(0, filePathsForRestore.size - 1)
                android.util.Log.d(
                    "AudioPlayerService",
                    "Initialized actualTrackIndex to ${playlistManager?.actualTrackIndex} (from savedState.currentIndex=${savedStateForRestore.currentIndex})",
                )

                // Mark as loading to prevent conflicts
                playlistManager?.isPlaylistLoading = true
                playlistManager?.currentLoadingPlaylist = filePathsForRestore

                // Restore playlist asynchronously
                service.playerServiceScope.launch {
                    try {
                        playlistManager?.preparePlaybackOptimized(
                            filePathsForRestore,
                            playlistManager.currentMetadata,
                            savedStateForRestore.currentIndex,
                            savedStateForRestore.currentPosition,
                        ) ?: throw IllegalStateException("PlaylistManager not initialized")

                        // Position is already applied in preparePlaybackOptimized if firstTrackIndex == savedState.currentIndex
                        // Only wait for player to be ready and restore playback state
                        var attempts = 0
                        while (attempts < 50) {
                            val newPlayer = service.getActivePlayer()
                            if (newPlayer.playbackState == Player.STATE_READY ||
                                newPlayer.playbackState == Player.STATE_BUFFERING
                            ) {
                                break
                            }
                            delay(100)
                            attempts++
                        }

                        // Check if position needs to be applied (if target track differs from first loaded track)
                        val newPlayer = service.getActivePlayer()
                        val firstTrackIndex =
                            savedStateForRestore.currentIndex.coerceIn(
                                0,
                                filePathsForRestore.size - 1,
                            )
                        if (firstTrackIndex != savedStateForRestore.currentIndex &&
                            newPlayer.mediaItemCount > savedStateForRestore.currentIndex
                        ) {
                            newPlayer.seekTo(savedStateForRestore.currentIndex, savedStateForRestore.currentPosition)
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Restored position (target differs from first track): index=${savedStateForRestore.currentIndex}, position=${savedStateForRestore.currentPosition}",
                            )
                        } else {
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Position already applied in preparePlaybackOptimized: index=${savedStateForRestore.currentIndex}, position=${savedStateForRestore.currentPosition}",
                            )
                        }

                        // Restore playback state
                        if (savedStateForRestore.isPlaying) {
                            newPlayer.playWhenReady = true
                            android.util.Log.d("AudioPlayerService", "Restored playback: playing")
                        }

                        // Clear saved state
                        playlistManager?.savedPlaybackState = null

                        // MediaLibraryService automatically updates notification when Player state changes
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "AudioPlayerService",
                            "Failed to restore playlist after player recreation",
                            e,
                        )
                        playlistManager?.savedPlaybackState = null
                    } finally {
                        // Clear loading flag when done
                        playlistManager?.isPlaylistLoading = false
                        playlistManager?.currentLoadingPlaylist = null
                    }
                }
            } else if (wasRecentlyLoaded) {
                // Only log if we have state but chose not to restore it (which shouldn't happen now as we restore if state exists)
                // But if savedStateForRestore is null, we might still log this context
                android.util.Log.d(
                    "AudioPlayerService",
                    "No saved state to restore (playlist loaded ${timeSinceLastLoad}ms ago), using Flutter-provided position",
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to configure ExoPlayer with processors", e)
        }
    }

    fun release() {
        customExoPlayer?.release()
        customExoPlayer = null
        playerListener = null
    }
}
