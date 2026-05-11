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

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.flow.first

/**
 * Handles initialization logic for AudioPlayerService.
 * Extracts complex initialization code from onCreate to improve readability.
 */
public class AudioPlayerServiceInitializer(
    private val service: AudioPlayerService,
) {
    @OptIn(UnstableApi::class)
    public fun initialize() {
        AudioCrossFadeSessionBinder.bind(service)
        android.util.Log.i("AudioPlayerService", "Initializing service components...")

        // NOTE: NotificationHelper is already initialized in onCreate() for immediate startForeground()
        // Only initialize if not already set (for safety)
        if (service.notificationHelper == null) {
            service.notificationHelper = NotificationHelper(service)
        }

        // Note: Order matters due to dependencies

        // 1. DurationManager (already initialized as val in Service)
        // service.durationManager is available

        // 2. MetadataManager
        service.metadataManager =
            MetadataManager(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                // getNotificationManager callback removed - MediaSession handles updates automatically
                getEmbeddedArtworkPath = { service.embeddedArtworkPath },
                setEmbeddedArtworkPath = { service.embeddedArtworkPath = it },
                getCurrentMetadata = { service.currentMetadata },
                setCurrentMetadata = { /* Read-only in Service, no-op here */ },
            )

        // 3. PlaybackController
        service.playbackController =
            PlaybackController(
                getActivePlayer = { service.getActivePlayer() },
                playerServiceScope = service.playerServiceScope,
                resetInactivityTimer = { service.inactivityTimer?.resetTimer() },
                getResumeRewindSeconds = {
                    // Long-pause resume rewind setting (0/5/10/30 sec).
                    try {
                        kotlinx.coroutines.runBlocking {
                            service.settingsRepository.userPreferences
                                .first()
                                .resumeRewindSeconds
                        }
                    } catch (e: Exception) {
                        10
                    }
                },
                getResumeRewindMode = {
                    try {
                        kotlinx.coroutines.runBlocking {
                            when (
                                service.settingsRepository.userPreferences
                                    .first()
                                    .resumeRewindMode
                            ) {
                                com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART ->
                                    ResumeRewindMode.SMART

                                else -> ResumeRewindMode.FIXED
                            }
                        }
                    } catch (e: Exception) {
                        ResumeRewindMode.FIXED
                    }
                },
                getResumeRewindAggressiveness = {
                    try {
                        kotlinx.coroutines.runBlocking {
                            service.settingsRepository.userPreferences
                                .first()
                                .resumeRewindAggressiveness
                        }
                    } catch (e: Exception) {
                        1.0f
                    }
                },
                consumeSleepTimerStopFlag = { service.consumeStoppedBySleepTimerFlag() },
                onSmartResumeSuggested = { context ->
                    service.publishSmartResumeSuggestion(context)
                },
            )

        // 3.1 SleepTimerManager
        service.sleepTimerManager =
            SleepTimerManager(
                context = service,
                packageName = service.packageName,
                playerServiceScope = service.playerServiceScope,
                getActivePlayer = { service.getActivePlayer() },
                sendBroadcast = { service.sendBroadcast(it) },
                saveCurrentPositionOnExpiry = {
                    service.playbackController?.markSleepTimerPause()
                    service.markStoppedBySleepTimer()
                    service.savePositionToRepository()
                },
                isShakeToExtendEnabled = {
                    try {
                        kotlinx.coroutines.runBlocking {
                            service.settingsRepository.userPreferences
                                .first()
                                .sleepTimerShakeExtendEnabled
                        }
                    } catch (e: Exception) {
                        true
                    }
                },
            )
        service.sleepTimerManager?.restoreTimerState()

        // 4. PlaylistManager (Complex dependencies)
        service.playlistManager =
            PlaylistManager(
                context = service,
                mediaCache = service.media3Cache,
                getActivePlayer = { service.getActivePlayer() },
                // getNotificationManager callback removed - MediaSession handles updates automatically
                playerServiceScope = service.playerServiceScope,
                mediaItemDispatcher = service.mediaItemDispatcher,
                dispatchers = service.dispatchers,
                getFlavorSuffix = { AudioPlayerService.getFlavorSuffix(service) },
                setPendingTrackSwitchDeferred = { deferred ->
                    service.playerListener?.setPendingTrackSwitchDeferred(deferred)
                },
                durationManager = service.durationManager,
                playerPersistenceManager = service.playerPersistenceManager,
                playbackController =
                    service.playbackController
                        ?: throw IllegalStateException("PlaybackController must be initialized before PlaylistManager"),
                getCurrentTrackIndex = { service.actualTrackIndex },
            )

        // 5. PositionManager
        service.positionManager =
            PositionManager(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                packageName = service.packageName,
                sendBroadcast = { service.sendBroadcast(it) },
            )

        // 6. CrossfadeHandler
        // Initialize crossfade handler (requires playlistManager)
        service.crossfadeHandler =
            CrossfadeHandler(
                service = service,
                crossFadePlayer =
                    service.crossFadePlayer
                        ?: throw IllegalStateException("CrossFadePlayer must be initialized before CrossfadeHandler"),
                playlistManager =
                    service.playlistManager
                        ?: throw IllegalStateException("PlaylistManager must be initialized before CrossfadeHandler"),
            )

        // 7. UnloadManager
        service.unloadManager =
            UnloadManager(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                getCustomExoPlayer = { service.customExoPlayer },
                releaseCustomExoPlayer = {
                    service.customExoPlayer?.release()
                    service.customExoPlayer = null
                },
                getMediaSession = { service.mediaSession },
                releaseMediaSession = {
                    service.mediaSession?.release()
                    service.mediaSession = null
                },
                getMediaSessionManager = { service.mediaSessionManager },
                releaseMediaSessionManager = {
                    service.mediaSessionManager?.release()
                    service.mediaSessionManager = null
                },
                getInactivityTimer = { service.inactivityTimer },
                releaseInactivityTimer = {
                    service.inactivityTimer?.release()
                    service.inactivityTimer = null
                },
                getPlaybackTimer = { service.playbackTimer },
                releasePlaybackTimer = {
                    service.playbackTimer?.release()
                    service.playbackTimer = null
                },
                getCurrentMetadata = { service.currentMetadata },
                setCurrentMetadata = { /* No-op */ },
                getEmbeddedArtworkPath = { service.embeddedArtworkPath },
                setEmbeddedArtworkPath = { service.embeddedArtworkPath = it },
                saveCurrentPosition = { service.saveCurrentPosition() },
                stopForeground = { removeNotification ->
                    @Suppress("DEPRECATION")
                    service.stopForeground(removeNotification)
                },
                stopSelf = { service.stopSelf() },
            )

        // Initialize helper for player state
        service.playerStateHelper =
            PlayerStateHelper(
                getActivePlayer = { service.getActivePlayer() },
                getCachedDuration = { service.durationManager.getCachedDuration(it) },
                saveDurationToCache = { path, duration -> service.durationManager.saveDurationToCache(path, duration) },
                getDurationForFile = { service.durationManager.getDurationForFile(it) },
                getLastCompletedTrackIndex = { service.lastCompletedTrackIndex },
                getActualPlaylistSize = { service.currentFilePaths?.size ?: service.exoPlayer.mediaItemCount },
                getActualTrackIndex = { service.actualTrackIndex },
                getCurrentFilePaths = { service.currentFilePaths },
                coroutineScope = service.playerServiceScope,
            )

        // Initialize Intent Handler
        service.intentHandler = ServiceIntentHandler(service)

        // Initialize Player Configurator - takes only service
        service.playerConfigurator = PlayerConfigurator(service)

        // Initialize Phone Call Listener for automatic resume after calls
        service.phoneCallListener =
            PhoneCallListener(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                wasPlayingBeforeCall = { service.wasPlayingBeforeCall },
                setWasPlayingBeforeCall = { value -> service.wasPlayingBeforeCall = value },
            )

        // Initialize MediaButtonHandler for multi-click headset support
        service.mediaButtonHandler = MediaButtonHandler()

        // Initialize HeadsetAutoplayHandler (BP-13.2: BT disconnect guard)
        service.headsetAutoplayHandler =
            HeadsetAutoplayHandler(
                context = service,
                onHeadsetConnected = {
                    // Wired headset: auto-resume playback
                    // BT reconnect: HeadsetAutoplayHandler only triggers this when
                    // wasPlayingBeforeBtDisconnect is true — but per BP-13.2 spec,
                    // we don't auto-play. Instead, user manually resumes via UI.
                    // For wired headset (lastDisconnectWasBluetooth=false), auto-play.
                    val handler = service.headsetAutoplayHandler
                    if (handler != null && !handler.lastDisconnectWasBluetooth) {
                        if (!service.isPlaying) {
                            service.play()
                        }
                    }
                    // BT reconnect: no auto-play, user resumes manually via notification/mini-player
                },
                onHeadsetDisconnected = {
                    // BP-13.2: On BT disconnect — save position and pause
                    service.headsetAutoplayHandler?.recordWasPlaying(service.isPlaying)
                    if (service.isPlaying) {
                        service.saveCurrentPosition()
                        service.pause()
                        LogUtils.d(
                            "AudioPlayerService",
                            "BT/headset disconnected — paused playback and saved position",
                        )
                    }
                },
            )
        service.headsetAutoplayHandler?.startListening()

        // BP-13.3: Initialize audio output device routing monitor
        service.audioOutputDeviceMonitor = AudioOutputDeviceMonitor(service).also { it.register() }

        // Ensure ExoPlayer is initialized
        // Note: Hilt initialization check removed to avoid backing field access error
        // We assume Hilt has initialized exoPlayer before onCreate calls initialize()
        service.configurePlayer()

        // Initialize MediaSession (Media3)
        AudioSessionSetup(service).initializeMediaSession()

        // Note: isFullyInitializedFlag will be set after MediaController is created
        // This ensures service is truly ready before components try to use it

        // Start settings synchronization to MediaSession
        // This ensures system media controls always reflect current app settings
        MediaSessionSettingsSyncInitializer.initialize(service)

        android.util.Log.i("AudioPlayerService", "Service components initialized successfully")
    }

    /**
     * Post-initialization setup called after initialize().
     * Handles: playback speed restore, notification provider, audio output, visualizer, enhancer.
     */
    public fun postInitialize() {
        AudioPlayerPostInitCoordinator(service).run()
    }
}
