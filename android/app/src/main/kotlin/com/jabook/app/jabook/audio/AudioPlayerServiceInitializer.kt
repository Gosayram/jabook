package com.jabook.app.jabook.audio

import android.content.pm.ServiceInfo
import android.os.Build
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

        // strict mode for foreground service on Android 14+
        // We must start foreground BEFORE creating MediaSession to avoid ANR/Freecess
        try {
            // Create legacy notification channel for "Initializing..." notification
            val notificationHelper = NotificationHelper(service)
            service.notificationHelper = notificationHelper
            // Channel is created inside createMinimalNotification

            val notification = notificationHelper.createMinimalNotification()

            // CRITICAL for Samsung/Android 14+: Specify foreground service type
            if (Build.VERSION.SDK_INT >= 29) {
                service.startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                service.startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            }
            android.util.Log.d("AudioPlayerService", "Started foreground service with Initializing notification")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to start foreground service", e)
        }

        // Initialize helper classes
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
                setPendingTrackSwitchDeferred = { deferred -> service.playerListener?.setPendingTrackSwitchDeferred(deferred) },
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

        // 6. PlaybackPositionSaver
        service.playbackPositionSaver =
            PlaybackPositionSaver(
                getActivePlayer = { service.getActivePlayer() },
                getMethodChannel = { service.methodChannel },
                context = service,
                getGroupPath = { service.currentGroupPath },
                isPlaylistLoading = { service.isPlaylistLoading },
                getActualTrackIndex = { service.actualTrackIndex },
                getCurrentFilePaths = { service.currentFilePaths },
                getDurationForFile = { filePath -> service.getDurationForFile(filePath) },
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
                    if (Build.VERSION.SDK_INT >= 24) {
                        service.stopForeground(removeNotification)
                    } else {
                        // Compat mapping
                        val shouldRemove =
                            removeNotification == android.app.Service.STOP_FOREGROUND_REMOVE ||
                                removeNotification == 1
                        service.stopForeground(shouldRemove)
                    }
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

        // Ensure ExoPlayer is initialized
        // Note: Hilt initialization check removed to avoid backing field access error
        // We assume Hilt has initialized exoPlayer before onCreate calls initialize()
        service.configurePlayer()

        // Initialize MediaSession (Media3)
        initializeMediaSession()

        service.isFullyInitializedFlag = true
        android.util.Log.i("AudioPlayerService", "Service components initialized successfully")
    }

    @OptIn(UnstableApi::class)
    private fun initializeMediaSession() {
        if (service.mediaLibrarySession != null) return

        try {
            // Create intent for clicking the notification
            val sessionActivity = service.getBackStackedActivity() ?: service.getSingleTopActivity()

            // Build MediaLibrarySession
            val sessionBuilder =
                MediaLibrarySession.Builder(
                    service,
                    service.exoPlayer,
                    AudioPlayerLibrarySessionCallback(
                        service,
                        service.playerPersistenceManager,
                        { filePath -> service.getDurationForFile(filePath) },
                    ),
                )

            // Set session activity (PendingIntent)
            // This is CRITICAL for Android 12+ media controls to work properly
            if (sessionActivity != null) {
                sessionBuilder.setSessionActivity(sessionActivity)
            } else {
                android.util.Log.w("AudioPlayerService", "Session activity intent is null")
            }

            service.mediaLibrarySession = sessionBuilder.build()

            // Assign to legacy field for compatibility
            service.mediaSession = service.mediaLibrarySession

            android.util.Log.i("AudioPlayerService", "MediaLibrarySession created successfully: ${service.mediaLibrarySession?.token}")

            // Create MediaSessionManager (wraps MediaSequencer)
            service.mediaSessionManager =
                MediaSessionManager(
                    service,
                    service.exoPlayer,
                )
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to create MediaLibrarySession", e)
        }
    }
}
