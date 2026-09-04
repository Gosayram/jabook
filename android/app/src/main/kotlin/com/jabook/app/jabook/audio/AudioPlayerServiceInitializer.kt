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

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CacheBitmapLoader // verify: androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles initialization logic for AudioPlayerService.
 * Extracts complex initialization code from onCreate to improve readability.
 */
public class AudioPlayerServiceInitializer(
    private val service: AudioPlayerService,
) {
    // Held reference so the sync can be cleaned up on service destruction.
    // The coroutines run in playerServiceScope which is cancelled in onDestroy.
    private var settingsSync: MediaSessionSettingsSync? = null

    // Cached user preferences: kept fresh by a collector in playerServiceScope.
    // Playback getters read this synchronously instead of runBlocking on the main thread.
    @Volatile
    private var cachedUserPreferences: UserPreferences? = null

    @OptIn(UnstableApi::class)
    public fun initialize() {
        service.lifecycleManager = ServiceLifecycleManager(service)
        // Publish the active-player getter before any writer can resolve a player.
        service.activePlayerRef.set { service.getActivePlayer() }
        initializeCrossFadePlayer()
        LogUtils.i("AudioPlayerService", "Initializing service components...")

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

        // 2.5 User preferences cache (cold path; single blocking read acceptable at service init)
        startUserPreferencesCache()

        // 3. PlaybackController
        service.playbackController =
            PlaybackController(
                getActivePlayer = { service.getActivePlayer() },
                playerServiceScope = service.playerServiceScope,
                resetInactivityTimer = { service.inactivityTimer?.resetTimer() },
                getResumeRewindSeconds = {
                    // Long-pause resume rewind setting (0/5/10/30 sec).
                    cachedUserPreferences?.resumeRewindSeconds ?: 10
                },
                getResumeRewindMode = {
                    if (cachedUserPreferences?.resumeRewindMode ==
                        com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART
                    ) {
                        ResumeRewindMode.SMART
                    } else {
                        ResumeRewindMode.FIXED
                    }
                },
                getResumeRewindAggressiveness = {
                    cachedUserPreferences?.resumeRewindAggressiveness ?: 1.0f
                },
                consumeSleepTimerStopFlag = { service.consumeStoppedBySleepTimerFlag() },
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
                audioFader = service.audioFader,
                settingsRepository = service.settingsRepository,
                saveSleepTimerStateToDataStore = { state ->
                    // Fire-and-forget: DataStore is the sole persistence sink for
                    // this manager (no SharedPreferences fallback is wired).
                    service.playerServiceScope.launch {
                        try {
                            service.settingsRepository.updateSleepTimerState(state)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LogUtils.w("AudioPlayerService", "Failed to save sleep timer state to DataStore", e)
                        }
                    }
                    Unit
                },
            )
        service.playerServiceScope.launch {
            try {
                service.sleepTimerManager?.restoreTimerState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.w("AudioPlayerService", "Failed to restore sleep timer state", e)
            }
        }

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
                okHttpClient = service.okHttpClient,
            )

        // 5. PositionManager
        service.positionManager = PositionManager()

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
                getMediaSession = { service.mediaLibrarySession },
                releaseMediaSession = {
                    // Cancel the sessionExtras loop with the session — otherwise it keeps
                    // looping after unload and a re-init would stack a second job.
                    service.sessionExtrasJob?.cancel()
                    service.sessionExtrasJob = null
                    service.mediaLibrarySession?.release()
                    service.mediaLibrarySession = null
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
                stopForeground = { flags ->
                    androidx.core.app.ServiceCompat
                        .stopForeground(service, flags)
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
                appContext = service.applicationContext,
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
                getCurrentBookId = { service.currentGroupPath },
                getCurrentChapterIndex = { service.getActivePlayer().currentMediaItemIndex },
                getCurrentPositionMs = { service.getActivePlayer().currentPosition },
                autoBookmarkTrigger = service.autoBookmarkTrigger,
                onCallEndedWithBookmark = { AudioPlayerService.phoneCallBookmarkCreated = true },
                isSleepTimerActive = { service.isSleepTimerActive() },
            )

        // Initialize MediaButtonHandler for multi-click headset support
        service.mediaButtonHandler = MediaButtonHandler()

        // Initialize HeadsetAutoplayHandler (BP-13.2: BT disconnect guard)
        service.headsetAutoplayHandler =
            HeadsetAutoplayHandler(
                context = service,
                onHeadsetConnected = {
                    // Wired headset: auto-resume playback only when the user opted in
                    // via the headset autoplay setting (default: disabled).
                    // BT reconnect: HeadsetAutoplayHandler only triggers this when
                    // wasPlayingBeforeBtDisconnect is true — but per BP-13.2 spec,
                    // we don't auto-play. Instead, user manually resumes via UI.
                    val handler = service.headsetAutoplayHandler
                    if (handler != null && !handler.lastDisconnectWasBluetooth) {
                        // Never autoplay mid-call: plugging a wired headset during a call
                        // must not blast audiobook audio over the conversation.
                        if (!service.isPlaying &&
                            cachedUserPreferences?.headsetAutoplayEnabled == true &&
                            service.phoneCallListener?.isInCall() != true
                        ) {
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

        // Ensure ExoPlayer is initialized
        // Note: Hilt initialization check removed to avoid backing field access error
        // We assume Hilt has initialized exoPlayer before onCreate calls initialize()
        service.configurePlayer()

        // Initialize MediaSession (Media3)
        initializeMediaSession()

        // Start settings synchronization to MediaSession
        // This ensures system media controls always reflect current app settings
        initializeSettingsSync()

        LogUtils.i("AudioPlayerService", "Service components initialized successfully")
    }

    /**
     * Post-initialization setup called after initialize().
     * Handles: playback speed restore, notification provider, audio output, visualizer, enhancer.
     */
    public fun postInitialize() {
        setupAudioOutputManager()
        initializeVisualizer()
    }

    @OptIn(UnstableApi::class)
    private fun initializeCrossFadePlayer() {
        service.crossFadePlayer =
            CrossFadePlayer(service, { context, handleAudioFocus ->
                androidx.media3.exoplayer.ExoPlayer
                    .Builder(context)
                    .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(context))
                    .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
                    .setHandleAudioBecomingNoisy(true)
                    .setAudioAttributes(
                        androidx.media3.common.AudioAttributes
                            .Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        handleAudioFocus,
                    ).build()
            }, service.playerServiceScope, service.volumeWriteCoordinator)
        service.crossFadePlayer?.onPlayerChanged = { newPlayer ->
            try {
                // The incoming player already owns a complete crossfade queue. An older
                // incremental loader must not append its sources after this swap.
                service.playlistManager?.cancelAsyncLoadingForPlayerSwitch()
                service.rebindActivePlayer(newPlayer)
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error updating MediaSession player after crossfade", e)
            }
        }
    }

    /**
     * Reads user preferences once (cold init path) and keeps the cache fresh
     * via a collector in playerServiceScope.
     */
    private fun startUserPreferencesCache() {
        // Apply defaults immediately; the collector below corrects the value shortly after.
        cachedUserPreferences = null
        service.playerServiceScope.launch {
            try {
                service.settingsRepository.userPreferences.collect { cachedUserPreferences = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "User preferences cache collector failed", e)
            }
        }
    }

    private fun setupAudioOutputManager() {
        service.audioOutputPlayerListener?.let { listener ->
            service.audioOutputPlayerTarget?.removeListener(listener)
        }

        service.audioOutputPlayerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        service.audioOutputManager.startMonitoring()
                    } else {
                        service.audioOutputManager.stopMonitoring()
                    }
                }
            }
        service.audioOutputPlayerTarget = null
        service.rebindAudioOutputPlayer()
    }

    private fun initializeVisualizer() {
        service.audioVisualizerManager = AudioVisualizerManager(service)
        service.visualizerBridgeJob?.cancel()
        service.visualizerBridgeJob =
            service.playerServiceScope.launch {
                service.audioVisualizerManager
                    ?.waveformData
                    ?.collect { waveform ->
                        service.audioVisualizerStateBridge.updateWaveform(waveform)
                    }
            }
    }

    @OptIn(UnstableApi::class)
    private fun initializeMediaSession() {
        if (service.mediaLibrarySession != null) return

        // Unique session ID: PID + instance hash (survives onCreate() called twice)
        val sessionId = "jabook_${android.os.Process.myPid()}_${System.identityHashCode(service)}"

        try {
            // Create intent for clicking the notification
            val sessionActivity = service.getBackStackedActivity() ?: service.getSingleTopActivity()

            // Create callback instance
            val callback =
                AudioPlayerLibrarySessionCallback(
                    service,
                    service.playerPersistenceManager,
                    service.torrentDownloadRepository,
                    service.mediaButtonHandler,
                    { filePath -> service.durationManager.getDurationForFile(filePath) },
                )
            val notificationProvider = AudioPlayerNotificationProvider(service)

            // Build the session and notification around one artwork loader.
            // CRITICAL FIX: Add BOTH PID AND instance hash to session ID
            // Android can call onCreate() MULTIPLE TIMES with the SAME PID without calling onDestroy()
            // Evidence from logs: PID 8921 had onCreate() called twice (Instance 50442924, then 115225231)
            LogUtils.i("AudioPlayerService", "Creating MediaLibrarySession with ID: $sessionId")

            val sessionBuilder =
                MediaLibrarySession
                    .Builder(
                        service,
                        service.getActivePlayer(),
                        callback,
                    ).setId(sessionId) // Truly unique session ID: PID + instance hash
                    .setBitmapLoader(CacheBitmapLoader(notificationProvider.bitmapLoader))

            // Set session activity (PendingIntent)
            // This is CRITICAL for Android 12+ media controls to work properly
            if (sessionActivity != null) {
                sessionBuilder.setSessionActivity(sessionActivity)
            } else {
                LogUtils.w("AudioPlayerService", "Session activity intent is null")
            }

            service.mediaLibrarySession = sessionBuilder.build()
            service.setNotificationProvider(notificationProvider)

            // Reserve space for skip buttons in notification (prevents jumping when buttons change)
            // Following Media3 official pattern from DemoPlaybackService
            service.mediaLibrarySession?.sessionExtras =
                Bundle().apply {
                    putBoolean(androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
                    putBoolean(androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
                }

            // Periodically update sessionExtras with dynamic state so Auto / WearOS
            // can read sleep timer remaining and playback speed without custom commands.
            service.sessionExtrasJob =
                service.playerServiceScope.launch {
                    var lastRemaining = Int.MIN_VALUE
                    var lastSpeed = Float.NaN
                    var lastTimerActive = false
                    while (isActive) {
                        delay(5_000L)
                        // 0 when inactive — never a negative sentinel (Auto/Wear consumers read this).
                        val remaining = service.getSleepTimerRemainingSeconds() ?: 0
                        val speed = service.getPlaybackSpeed()
                        val isTimerActive = service.isSleepTimerActive()
                        // Only rebuild sessionExtras when the published values actually changed.
                        if (remaining == lastRemaining && speed == lastSpeed && isTimerActive == lastTimerActive) continue
                        lastRemaining = remaining
                        lastSpeed = speed
                        lastTimerActive = isTimerActive
                        service.mediaLibrarySession?.sessionExtras =
                            Bundle().apply {
                                putBoolean(androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
                                putBoolean(androidx.media3.session.MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
                                putLong(AudioPlayerService.EXTRA_SLEEP_TIMER_REMAINING_MS, remaining * 1000L)
                                putFloat(AudioPlayerService.EXTRA_PLAYBACK_SPEED, speed)
                                putBoolean(AudioPlayerService.EXTRA_IS_SLEEP_TIMER_ACTIVE, isTimerActive)
                            }
                    }
                }

            LogUtils.i(
                "AudioPlayerService",
                "MediaLibrarySession created successfully: ${service.mediaLibrarySession?.token}",
            )

            // Create MediaSessionManager (wraps MediaSequencer)
            service.mediaSessionManager =
                MediaSessionManager(
                    service,
                    service.getActivePlayer(),
                    playCallback = service::onMediaSessionPlaybackStarted,
                    pauseCallback = service::onMediaSessionPlaybackPaused,
                )

            // The session is ready as soon as it is built. Media3 applies button preferences
            // directly to the session; connecting a controller to our own service adds no state.
            service.setInitialMediaButtonPreferences()
            service.isFullyInitializedFlag = true
            service.markInitializationComplete()

            // Allow updating player reference if crossfade happens
            // service.crossFadePlayer?.onPlayerChanged will handle this via updatePlayer()

            // Note: setMediaNotificationProvider must be called from AudioPlayerService.onCreate()
            // as it's a protected method in MediaSessionService
        } catch (e: Exception) {
            // CRITICAL: never swallow session-init failure. If the session cannot be
            // built, mediaLibrarySession stays null and every MediaController connection
            // is silently rejected in onGetSession() — the app looks like "player doesn't
            // work" with no visible error. Fail loudly so onCreate() rethrows and the
            // GlobalExceptionHandler surfaces the real cause.
            LogUtils.e("AudioPlayerService", "FATAL: failed to create MediaLibrarySession", e)
            com.jabook.app.jabook.crash.CrashDiagnostics.reportNonFatal(
                tag = "media_session_init_failed",
                throwable = e,
                attributes = mapOf("session_id" to sessionId),
            )
            service.markInitializationFailed(e)
            throw e
        }
    }

    /**
     * Initializes settings synchronization for MediaSession custom commands.
     * Observes user preferences and updates skip durations dynamically.
     */
    private fun initializeSettingsSync() {
        try {
            val sync =
                MediaSessionSettingsSync(
                    settingsRepository = service.settingsRepository,
                    service = service,
                    scope = service.playerServiceScope,
                )
            sync.start()
            settingsSync = sync
            LogUtils.i("AudioPlayerService", "Settings sync initialized successfully")
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to initialize settings sync", e)
        }
    }
}
