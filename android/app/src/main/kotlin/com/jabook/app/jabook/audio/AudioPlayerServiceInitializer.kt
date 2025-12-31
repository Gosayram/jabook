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
import androidx.media3.session.MediaLibraryService.MediaLibrarySession

/**
 * Handles initialization logic for AudioPlayerService.
 * Extracts complex initialization code from onCreate to improve readability.
 */
class AudioPlayerServiceInitializer(
    private val service: AudioPlayerService,
) {
    @OptIn(UnstableApi::class)
    fun initialize() {
        android.util.Log.i("AudioPlayerService", "Initializing service components...")

        // Initialize helper classes
        service.notificationHelper = NotificationHelper(service)

        // Note: Order matters due to dependencies

        // 1. DurationManager (already initialized as val in Service)
        // service.durationManager is available

        // 2. MetadataManager
        service.metadataManager =
            MetadataManager(
                context = service,
                getActivePlayer = { service.getActivePlayer() },
                getNotificationManager = { service.notificationManager },
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
            )

        // 4. PlaylistManager (Complex dependencies)
        service.playlistManager =
            PlaylistManager(
                context = service,
                mediaCache = service.media3Cache,
                getActivePlayer = { service.getActivePlayer() },
                getNotificationManager = { service.notificationManager },
                playerServiceScope = service.playerServiceScope,
                mediaItemDispatcher = service.mediaItemDispatcher,
                getFlavorSuffix = { AudioPlayerService.getFlavorSuffix(service) },
                setPendingTrackSwitchDeferred = { deferred ->
                    service.playerListener?.setPendingTrackSwitchDeferred(deferred)
                },
                durationManager = service.durationManager,
                playerPersistenceManager = service.playerPersistenceManager,
                playbackController = service.playbackController!!,
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
                getCachedDuration = { service.getCachedDuration(it) },
                saveDurationToCache = { path, duration -> service.saveDurationToCache(path, duration) },
                getDurationForFile = { service.getDurationForFile(it) },
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

        // Ensure ExoPlayer is initialized
        // Note: Hilt initialization check removed to avoid backing field access error
        // We assume Hilt has initialized exoPlayer before onCreate calls initialize()
        service.configurePlayer()

        // Initialize MediaSession (Media3)
        initializeMediaSession()

        service.isFullyInitializedFlag = true

        // Start settings synchronization to MediaSession
        // This ensures system media controls always reflect current app settings
        initializeSettingsSync()

        android.util.Log.i("AudioPlayerService", "Service components initialized successfully")
    }

    @OptIn(UnstableApi::class)
    private fun initializeMediaSession() {
        if (service.mediaLibrarySession != null) return

        try {
            // Create intent for clicking the notification
            val sessionActivity = service.getBackStackedActivity() ?: service.getSingleTopActivity()

            // Create callback instance
            val callback =
                AudioPlayerLibrarySessionCallback(
                    service,
                    service.playerPersistenceManager,
                    service.torrentDownloadRepository,
                    { filePath -> service.getDurationForFile(filePath) },
                )

            // Build MediaLibrarySession - Media3 handles notifications automatically
            // DO NOT set custom notification provider or media button preferences in Builder
            // These must be handled in the Callback's onConnect method
            // CRITICAL FIX: Add BOTH PID AND instance hash to session ID
            // Android can call onCreate() MULTIPLE TIMES with the SAME PID without calling onDestroy()
            // Evidence from logs: PID 8921 had onCreate() called twice (Instance 50442924, then 115225231)
            val sessionId = "jabook_${android.os.Process.myPid()}_${System.identityHashCode(service)}"
            android.util.Log.i("AudioPlayerService", "Creating MediaLibrarySession with ID: $sessionId")

            val sessionBuilder =
                MediaLibrarySession
                    .Builder(
                        service,
                        service.exoPlayer,
                        callback,
                    ).setId(sessionId) // Truly unique session ID: PID + instance hash

            // Set session activity (PendingIntent)
            // This is CRITICAL for Android 12+ media controls to work properly
            if (sessionActivity != null) {
                sessionBuilder.setSessionActivity(sessionActivity)
            } else {
                android.util.Log.w("AudioPlayerService", "Session activity intent is null")
            }

            service.mediaLibrarySession = sessionBuilder.build()

            // Reserve space for skip buttons in notification (prevents jumping when buttons change)
            // Following Media3 official pattern from DemoPlaybackService
            service.mediaLibrarySession?.sessionExtras =
                androidx.core.os.bundleOf(
                    androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV to true,
                    androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT to true,
                )

            // Assign to legacy field for compatibility
            service.mediaSession = service.mediaLibrarySession

            android.util.Log.i(
                "AudioPlayerService",
                "MediaLibrarySession created successfully: ${service.mediaLibrarySession?.token}",
            )

            // Create MediaSessionManager (wraps MediaSequencer)
            service.mediaSessionManager =
                MediaSessionManager(
                    service,
                    service.exoPlayer,
                )

            // Legacy NotificationManager is no longer needed - Media3 handles everything
            // Keep reference null to prevent duplicate notifications
            service.notificationManager = null

            // Note: setMediaNotificationProvider must be called from AudioPlayerService.onCreate()
            // as it's a protected method in MediaSessionService
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to create MediaLibrarySession", e)
        }
    }

    /**
     * Initializes settings synchronization for MediaSession custom commands.
     * Observes user preferences and updates skip durations dynamically.
     */
    private fun initializeSettingsSync() {
        try {
            val settingsSync =
                MediaSessionSettingsSync(
                    settingsRepository = service.settingsRepository,
                    service = service,
                    scope = service.playerServiceScope,
                )
            settingsSync.start()
            android.util.Log.i("AudioPlayerService", "Settings sync initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to initialize settings sync", e)
        }
    }
}
