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
import kotlinx.coroutines.launch

/**
 * Binds core runtime components for [AudioPlayerService] in dependency-safe order.
 */
internal object AudioServiceComponentBinder {
    fun bind(service: AudioPlayerService) {
        // PlaylistManager is the single source of truth for current metadata.
        // setCurrentMetadata callbacks in managers are intentionally no-op.
        var cachedResumeRewindSeconds = 10
        var cachedResumeRewindMode = ResumeRewindMode.FIXED
        var cachedResumeRewindAggressiveness = 1.0f
        var cachedShakeToExtendEnabled = true
        service.playerServiceScope.launch {
            service.settingsRepository.userPreferences.collect { prefs ->
                cachedResumeRewindSeconds = prefs.resumeRewindSeconds
                cachedResumeRewindMode =
                    when (prefs.resumeRewindMode) {
                        com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART -> ResumeRewindMode.SMART
                        else -> ResumeRewindMode.FIXED
                    }
                cachedResumeRewindAggressiveness = prefs.resumeRewindAggressiveness
                cachedShakeToExtendEnabled = prefs.sleepTimerShakeExtendEnabled
            }
        }

        service.metadataManager =
            MetadataManager(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                getEmbeddedArtworkPath = { service.embeddedArtworkPath },
                setEmbeddedArtworkPath = { service.embeddedArtworkPath = it },
                getCurrentMetadata = { service.currentMetadata },
                setCurrentMetadata = { },
            )

        service.playbackController =
            PlaybackController(
                getActivePlayer = { service.getActivePlayer() },
                playerServiceScope = service.playerServiceScope,
                resetInactivityTimer = { source -> service.inactivityTimer?.resetIfApplicable(source) },
                getResumeRewindSeconds = { cachedResumeRewindSeconds },
                getResumeRewindMode = { cachedResumeRewindMode },
                getResumeRewindAggressiveness = { cachedResumeRewindAggressiveness },
                consumeSleepTimerStopFlag = { service.consumeStoppedBySleepTimerFlag() },
                contextualResumeManager =
                    ContextualResumeManager(
                        speechAnalyzer = DefaultSpeechSegmentAnalyzer(),
                    ),
                onSmartResumeSuggested = { context -> service.publishSmartResumeSuggestion(context) },
            )

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
                isShakeToExtendEnabled = { cachedShakeToExtendEnabled },
            )
        service.sleepTimerManager?.restoreTimerState()

        service.playlistManager =
            PlaylistManager(
                context = service,
                mediaCache = service.media3Cache,
                getActivePlayer = { service.getActivePlayer() },
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

        service.positionManager =
            PositionManager(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                packageName = service.packageName,
                sendBroadcast = { service.sendBroadcast(it) },
            )

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
                setCurrentMetadata = { },
                getEmbeddedArtworkPath = { service.embeddedArtworkPath },
                setEmbeddedArtworkPath = { service.embeddedArtworkPath = it },
                saveCurrentPosition = { service.saveCurrentPosition() },
                stopForeground = { removeNotification ->
                    @Suppress("DEPRECATION")
                    service.stopForeground(removeNotification)
                },
                stopSelf = { service.stopSelf() },
            )

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

        service.intentHandler = ServiceIntentHandler(service)
        service.playerConfigurator = PlayerConfigurator(service)

        service.phoneCallListener =
            PhoneCallListener(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                wasPlayingBeforeCall = { service.wasPlayingBeforeCall },
                setWasPlayingBeforeCall = { value -> service.wasPlayingBeforeCall = value },
            )

        service.mediaButtonHandler = MediaButtonHandler()
        service.headsetAutoplayHandler =
            HeadsetAutoplayHandler(
                context = service,
                onHeadsetConnected = {
                    val handler = service.headsetAutoplayHandler
                    if (handler != null && !handler.lastDisconnectWasBluetooth && !service.isPlaying) {
                        service.play(InactivityCommandSource.HEADSET_BUTTON)
                    }
                },
                onHeadsetDisconnected = {
                    service.headsetAutoplayHandler?.recordWasPlaying(service.isPlaying)
                    if (service.isPlaying) {
                        service.saveCurrentPosition()
                        service.pause(InactivityCommandSource.HEADSET_BUTTON)
                        LogUtils.d(
                            "AudioPlayerService",
                            "BT/headset disconnected — paused playback and saved position",
                        )
                    }
                },
            )
        service.headsetAutoplayHandler?.startListening()

        service.audioOutputDeviceMonitor = AudioOutputDeviceMonitor(service).also { it.register() }
        service.configurePlayer()
    }
}
