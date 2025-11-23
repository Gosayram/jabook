// Copyright 2025 Jabook Contributors
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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.datasource.cache.Cache
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.File

/**
 * Native audio player service using Media3 ExoPlayer.
 *
 * This service extends MediaSessionService for proper integration with Android's
 * media controls, Android Auto, Wear OS, and system notifications.
 * 
 * Uses Dagger Hilt for dependency injection (ExoPlayer and Cache as singletons).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioPlayerService : MediaSessionService() {
    @Inject
    lateinit var exoPlayer: ExoPlayer
    
    @Inject
    lateinit var mediaCache: Cache
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var playbackTimer: PlaybackTimer? = null
    private var currentMetadata: Map<String, String>? = null
    private var embeddedArtworkPath: String? = null // Path to saved embedded artwork
    
    private val playerServiceScope = MainScope()
    
    // Manual AudioFocus management
    private var audioManager: AudioManager? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    companion object {
        private const val NOTIFICATION_ID = 1
        @Volatile
        private var instance: AudioPlayerService? = null
        
        fun getInstance(): AudioPlayerService? = instance
    }
    
    /**
     * Flag indicating if service is fully initialized and ready to use.
     * Service is ready when MediaSession is created and all components are initialized.
     */
    @Volatile
    private var _isFullyInitialized = false
    
    /**
     * Checks if service is fully initialized and ready to use.
     * 
     * @return true if service is ready, false otherwise
     */
    fun isFullyInitialized(): Boolean = _isFullyInitialized && mediaSession != null
    
    /**
     * Gets the MediaSession instance.
     * Used by AudioPlayerMethodHandler to check if service is fully ready.
     */
    fun getMediaSession(): MediaSession? = mediaSession
    
    /**
     * Creates a minimal notification for startForeground() call.
     * This is required for Android 14+ to prevent crashes.
     * The notification will be updated later with full media controls.
     * 
     * @return Minimal notification for foreground service
     */
    private fun createMinimalNotification(): Notification {
        // Create notification channel if not exists
        val channelId = "jabook_audio_playback"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JaBook Audio Playback",
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("JaBook Audio")
            .setContentText("Initializing audio player...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        android.util.Log.d("AudioPlayerService", "onCreate started")
        
        try {
            // CRITICAL FIX for Android 14+: Call startForeground() immediately
            // MediaSessionService requires explicit startForeground() call within 5 seconds
            // This prevents crash: "Context.startForegroundService() did not then call Service.startForeground()"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val tempNotification = createMinimalNotification()
                startForeground(NOTIFICATION_ID, tempNotification)
                android.util.Log.d("AudioPlayerService", "startForeground() called for Android 14+ (critical fix)")
            }
            
            // Check Hilt initialization before using @Inject fields
            // This prevents crashes if Hilt is not ready
            try {
                if (!::exoPlayer.isInitialized || !::mediaCache.isInitialized) {
                    android.util.Log.w("AudioPlayerService", "Hilt dependencies not initialized, waiting...")
                    throw IllegalStateException("Hilt dependencies not ready")
                }
            } catch (e: UninitializedPropertyAccessException) {
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Hilt not initialized")
                throw IllegalStateException("Hilt dependencies not ready", e)
            }
            
            // Validate Android 14+ requirements before initialization
            if (!ErrorHandler.validateAndroid14Requirements(this)) {
                android.util.Log.e("AudioPlayerService", "Android 14+ requirements validation failed")
                throw IllegalStateException("Android 14+ requirements not met")
            }
            
            // Validate Color OS specific requirements (if applicable)
            if (ErrorHandler.isColorOS()) {
                if (!ErrorHandler.validateColorOSRequirements(this)) {
                    android.util.Log.e("AudioPlayerService", "Color OS requirements validation failed")
                    throw IllegalStateException("Color OS requirements not met")
                }
                android.util.Log.d("AudioPlayerService", "Color OS detected, special handling enabled")
            }
            
            // Inspired by lissen-android: minimize synchronous operations in onCreate()
            // Hilt dependencies (ExoPlayer and Cache) are injected automatically
            // They are created lazily on first access, not in onCreate()
            
            // Configure ExoPlayer (already created via Hilt, accessed lazily)
            // This is lightweight - just adding listener and setting flags
            configurePlayer()
            
            // Create MediaSessionManager with callbacks for rewind/forward
            mediaSessionManager = MediaSessionManager(this, exoPlayer).apply {
                setCallbacks(
                    rewindCallback = { rewind(15) },
                    forwardCallback = { forward(30) }
                )
            }
            
            // Create MediaSession once in onCreate (inspired by lissen-android)
            // MediaSessionService will use it via onGetSession()
            mediaSession = mediaSessionManager!!.getMediaSession()
            android.util.Log.i("AudioPlayerService", "MediaSession created, session: ${mediaSession != null}")
            
            // Create notification manager with MediaSession
            notificationManager = NotificationManager(
                this,
                exoPlayer,
                mediaSession,
                currentMetadata,
                embeddedArtworkPath
            )
            
            // Update notification with full media controls after MediaSession is created
            // This replaces the minimal notification used for startForeground()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    notificationManager?.updateNotification()
                    android.util.Log.d("AudioPlayerService", "Notification updated with media controls")
                } catch (e: Exception) {
                    android.util.Log.w("AudioPlayerService", "Failed to update notification, using minimal notification", e)
                    // Continue with minimal notification - service is still functional
                }
            }
            
            // Initialize playback timer (inspired by lissen-android)
            playbackTimer = PlaybackTimer(this, exoPlayer)
            
            // ExoPlayer manages AudioFocus automatically when handleAudioFocus=true
            // No need for manual AudioFocus management
            
            // Mark service as fully initialized after all components are ready
            _isFullyInitialized = true
            
            android.util.Log.i("AudioPlayerService", "Service onCreate completed successfully, fully initialized: $_isFullyInitialized")
        } catch (e: Exception) {
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Service initialization failed")
            _isFullyInitialized = false
            throw e
        }
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Return MediaSession for MediaSessionService
        // MediaSessionService automatically manages foreground service and notifications
        // Inspired by lissen-android: return already created session from onCreate()
        android.util.Log.i("AudioPlayerService", "onGetSession() called by: ${controllerInfo.packageName}, mediaSession: ${mediaSession != null}")
        if (mediaSession == null) {
            android.util.Log.w("AudioPlayerService", "MediaSession is null in onGetSession(), creating fallback")
            // Fallback: create session if somehow it wasn't created in onCreate()
            // This should not happen in normal flow, but provides safety
            mediaSession = mediaSessionManager?.getMediaSession()
            android.util.Log.w("AudioPlayerService", "Fallback MediaSession created: ${mediaSession != null}")
        }
        return mediaSession
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Handle actions from notification and timer
        when (intent?.action) {
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PLAY -> play()
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PAUSE -> pause()
            com.jabook.app.jabook.audio.NotificationManager.ACTION_NEXT -> next()
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PREVIOUS -> previous()
            
            // Handle timer actions (inspired by lissen-android)
            PlaybackTimer.ACTION_TIMER_EXPIRED -> {
                // Timer expired - playback should already be paused by PlaybackTimer
                android.util.Log.d("AudioPlayerService", "Timer expired, playback paused")
            }
        }
        return START_STICKY
    }
    
    /**
     * Starts sleep timer.
     *
     * @param delayInSeconds Timer duration in seconds
     * @param option Timer option (FIXED_DURATION or CURRENT_TRACK)
     */
    fun startTimer(delayInSeconds: Double, option: Int = 0) {
        val timerOption = when (option) {
            1 -> PlaybackTimer.TimerOption.CURRENT_TRACK
            else -> PlaybackTimer.TimerOption.FIXED_DURATION
        }
        playbackTimer?.startTimer(delayInSeconds, timerOption)
    }
    
    /**
     * Stops sleep timer.
     */
    fun stopTimer() {
        playbackTimer?.stopTimer()
    }
    
    /**
     * Configures ExoPlayer instance (already created via Hilt).
     * 
     * ExoPlayer is provided as singleton via Dagger Hilt MediaModule.
     * LoadControl and AudioAttributes are already configured in MediaModule.
     * This method only adds listener and configures additional settings.
     * 
     * Inspired by lissen-android: lightweight configuration, no heavy operations.
     */
    private fun configurePlayer() {
        try {
            // Match lissen-android: just add listener, no additional configuration
            // ExoPlayer is already configured in MediaModule with AudioAttributes
            exoPlayer.addListener(playerListener)
            
            // Match lissen-android: don't set WakeMode or ScrubbingMode
            // These may interfere with AudioFocus handling
            
            // Initialize repeat and shuffle modes (lissen-android doesn't set these either, but it's safe)
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            exoPlayer.shuffleModeEnabled = false
            
            android.util.Log.d("AudioPlayerService", "ExoPlayer configured (provided via Hilt)")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to configure ExoPlayer", e)
            throw e
        }
    }
    
    /**
     * Sets playlist from file paths or URLs.
     *
     * Supports both local file paths and HTTP(S) URLs for network streaming.
     * Uses coroutines for async operations (inspired by lissen-android).
     * 
     * CRITICAL: This method is asynchronous and uses coroutines to avoid blocking.
     * Flutter should wait for completion via MethodChannel.Result callback.
     *
     * @param filePaths List of absolute file paths or HTTP(S) URLs to audio files
     * @param metadata Optional metadata map (title, artist, album, etc.)
     * @param callback Optional callback to notify when playlist is ready (for Flutter)
     */
    fun setPlaylist(
        filePaths: List<String>, 
        metadata: Map<String, String>? = null,
        callback: ((Boolean, Exception?) -> Unit)? = null
    ) {
        currentMetadata = metadata
        android.util.Log.d("AudioPlayerService", "Setting playlist with ${filePaths.size} items")
        
        playerServiceScope.launch {
            try {
                preparePlayback(filePaths, metadata)
                android.util.Log.d("AudioPlayerService", "Playlist prepared successfully")
                withContext(Dispatchers.Main) {
                    callback?.invoke(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to prepare playback", e)
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "preparePlayback failed")
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, e)
                }
            }
        }
    }
    
    /**
     * Prepares playback asynchronously.
     * 
     * @param filePaths List of file paths or URLs
     * @param metadata Optional metadata
     */
    @OptIn(UnstableApi::class)
    private suspend fun preparePlayback(
        filePaths: List<String>,
        metadata: Map<String, String>?
    ) = withContext(Dispatchers.IO) {
        try {
            val dataSourceFactory = MediaDataSourceFactory(this@AudioPlayerService, mediaCache)
            
            val mediaSources = filePaths.mapIndexed { index, path ->
                try {
                    // Determine if path is a URL or file path
                    val isUrl = path.startsWith("http://") || path.startsWith("https://")
                    val uri: Uri
                    
                    if (isUrl) {
                        // Handle HTTP(S) URL
                        uri = Uri.parse(path)
                        android.util.Log.d("AudioPlayerService", "Creating MediaItem $index from URL: $path")
                        // For URLs, ExoPlayer will extract metadata automatically
                    } else {
                        // Handle local file path
                        val file = File(path)
                        if (!file.exists()) {
                            android.util.Log.w("AudioPlayerService", "File does not exist: $path")
                        }
                        uri = Uri.fromFile(file)
                        android.util.Log.d("AudioPlayerService", "Creating MediaItem $index from file: $path (uri: $uri)")
                        
                        // CRITICAL: Do NOT use MediaMetadataRetriever here - it blocks main thread!
                        // For 286 files, this would cause ANR on Android 16.
                        // ExoPlayer will extract metadata (including artwork) asynchronously when loading the file.
                        // This is much faster and doesn't block the UI thread.
                        // Only extract artwork for the first file if needed (optional optimization)
                        if (index == 0 && metadata?.get("artworkUri") == null) {
                            // Optional: extract artwork only for first file as preview
                            // But skip it to avoid blocking - ExoPlayer will do it anyway
                            android.util.Log.d("AudioPlayerService", "Skipping artwork extraction for file $index - ExoPlayer will extract it asynchronously")
                        }
                    }
                    
                    val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
                    
                    val fileName = if (isUrl) {
                        val urlPath = Uri.parse(path).lastPathSegment ?: "Track ${index + 1}"
                        urlPath.substringBeforeLast('.', urlPath)
                    } else {
                        File(path).nameWithoutExtension
                    }
                    val providedTitle = metadata?.get("title") ?: metadata?.get("trackTitle")
                    val providedArtist = metadata?.get("artist") ?: metadata?.get("author")
                    val providedAlbum = metadata?.get("album") ?: metadata?.get("bookTitle")
                    
                    metadataBuilder.setTitle(providedTitle ?: fileName.ifEmpty { "Track ${index + 1}" })
                    
                    if (providedArtist != null) {
                        metadataBuilder.setArtist(providedArtist)
                    }
                    
                    if (providedAlbum != null) {
                        metadataBuilder.setAlbumTitle(providedAlbum)
                    }
                    
                    val artworkUriString = metadata?.get("artworkUri")?.takeIf { it.isNotEmpty() }
                    if (artworkUriString != null) {
                        try {
                            val artworkUri = android.net.Uri.parse(artworkUriString)
                            metadataBuilder.setArtworkUri(artworkUri)
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to parse artwork URI: $artworkUriString", e)
                        }
                    }
                    
                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(metadataBuilder.build())
                        .build()
                    
                    val sourceFactory = dataSourceFactory.createDataSourceFactoryForUri(uri)
                    ProgressiveMediaSource.Factory(sourceFactory)
                        .createMediaSource(mediaItem)
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to create MediaItem for path: $path", e)
                    throw e
                }
            }
            
            withContext(Dispatchers.Main) {
                exoPlayer.playWhenReady = false
                exoPlayer.setMediaSources(mediaSources)
                exoPlayer.prepare()
                
                notificationManager?.updateNotification()
                
                android.util.Log.i("AudioPlayerService", "Playlist set: ${mediaSources.size} sources, ${exoPlayer.mediaItemCount} items, state=${exoPlayer.playbackState}")
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to prepare playback", e)
            throw e
        }
    }
    
    /**
     * Seeks to specific track and position.
     *
     * @param trackIndex Track index in playlist
     * @param positionMs Position in milliseconds within the track
     */
    fun seekToTrackAndPosition(trackIndex: Int, positionMs: Long) {
        if (!::exoPlayer.isInitialized) return
        val player = exoPlayer
        
        if (trackIndex < 0 || trackIndex >= player.mediaItemCount) {
            android.util.Log.w("AudioPlayerService", "Invalid track index: $trackIndex (mediaItemCount: ${player.mediaItemCount})")
            return
        }
        
        if (positionMs < 0) {
            android.util.Log.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
            return
        }
        
        try {
            val playWhenReadyBeforeSeek = player.playWhenReady
            player.seekTo(trackIndex, positionMs)
            
            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to seek to track and position: trackIndex=$trackIndex, positionMs=$positionMs", e)
        }
    }
    
    /**
     * Updates metadata for current track.
     *
     * @param metadata Metadata map (title, artist, album)
     * Note: Artwork is automatically extracted from audio file metadata by ExoPlayer
     * We don't update MediaItem here to preserve embedded artwork extracted by ExoPlayer
     */
    fun updateMetadata(metadata: Map<String, String>) {
        currentMetadata = metadata
        // Just update notification - ExoPlayer already has the embedded artwork in MediaItem
        // Don't replace MediaItem to avoid losing embedded artwork
        // MediaSession automatically updates metadata from ExoPlayer, no manual update needed
        notificationManager?.updateMetadata(metadata, embeddedArtworkPath)
    }
    
    /**
     * Requests AudioFocus for playback.
     * 
     * @return true if AudioFocus was granted, false otherwise
     */
    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        val listener = audioFocusListener ?: return false
        
        if (hasAudioFocus) {
            android.util.Log.d("AudioPlayerService", "AudioFocus already held")
            return true
        }
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create AudioFocusRequest once and reuse it
            if (audioFocusRequest == null) {
                audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(true) // Allow delayed focus gain
                    .build()
                android.util.Log.d("AudioPlayerService", "Created AudioFocusRequest")
            }
            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasAudioFocus = granted
        android.util.Log.i("AudioPlayerService", "AudioFocus request result: $result (granted=$granted)")
        return granted
    }
    
    /**
     * Abandons AudioFocus when playback stops.
     */
    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        
        if (!hasAudioFocus) {
            android.util.Log.d("AudioPlayerService", "AudioFocus not held, nothing to abandon")
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the same AudioFocusRequest that was used for request
            audioFocusRequest?.let { request ->
                am.abandonAudioFocusRequest(request)
                android.util.Log.i("AudioPlayerService", "AudioFocus abandoned using saved request")
            } ?: run {
                android.util.Log.w("AudioPlayerService", "AudioFocusRequest is null, cannot abandon properly")
            }
        } else {
            @Suppress("DEPRECATION")
            audioFocusListener?.let { listener ->
                am.abandonAudioFocus(listener)
                android.util.Log.i("AudioPlayerService", "AudioFocus abandoned (deprecated API)")
            }
        }
        
        hasAudioFocus = false
    }
    
    /**
     * Handles AudioFocus changes.
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        android.util.Log.i("AudioPlayerService", "AudioFocus change: $focusChange")
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // AudioFocus regained - resume playback
                android.util.Log.i("AudioPlayerService", "AudioFocus GAIN - resuming playback")
                hasAudioFocus = true
                if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                    exoPlayer.playWhenReady = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss - pause playback
                android.util.Log.i("AudioPlayerService", "AudioFocus LOSS - pausing playback")
                hasAudioFocus = false
                exoPlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss - pause playback
                android.util.Log.i("AudioPlayerService", "AudioFocus LOSS_TRANSIENT - pausing playback")
                exoPlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can duck - reduce volume
                android.util.Log.i("AudioPlayerService", "AudioFocus LOSS_TRANSIENT_CAN_DUCK - reducing volume")
                // For audiobooks, we pause instead of ducking
                exoPlayer.playWhenReady = false
            }
        }
    }
    
    /**
     * Starts or resumes playback.
     * 
     * Simplified implementation matching lissen-android approach.
     */
    fun play() {
        android.util.Log.i("AudioPlayerService", "play() called")
        
        if (!::exoPlayer.isInitialized) {
            android.util.Log.w("AudioPlayerService", "Cannot play: ExoPlayer is not initialized")
            return
        }
        
        val player = exoPlayer
        if (player.mediaItemCount == 0) {
            android.util.Log.w("AudioPlayerService", "Cannot play: no media items loaded")
            return
        }
        
        // Match lissen-android: simple approach - just set playWhenReady=true in coroutine
        // ExoPlayer will handle AudioFocus automatically
        playerServiceScope.launch(Dispatchers.Main) {
            try {
                // Only call prepare() if player is in IDLE state
                if (player.playbackState == Player.STATE_IDLE) {
                    android.util.Log.d("AudioPlayerService", "play() - player is IDLE, calling prepare()")
                    player.prepare()
                }
                
                // Match lissen-android: simply set playWhenReady=true
                // ExoPlayer manages AudioFocus automatically when handleAudioFocus=true
                player.playWhenReady = true
                android.util.Log.d("AudioPlayerService", "play() - set playWhenReady=true, letting ExoPlayer handle AudioFocus")
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to start playback", e)
                e.printStackTrace()
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Play method execution")
            }
        }
    }
    
    /**
     * Pauses playback.
     */
    fun pause() {
        if (!::exoPlayer.isInitialized) {
            android.util.Log.w("AudioPlayerService", "Cannot pause: ExoPlayer is not initialized")
            return
        }
        
        playerServiceScope.launch {
            try {
                exoPlayer.playWhenReady = false
                // Note: We don't abandon AudioFocus on pause - we keep it for quick resume
                // AudioFocus will be abandoned when service is stopped
            } catch (e: Exception) {
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Pause method execution")
            }
        }
    }
    
    /**
     * Stops playback and resets player.
     */
    fun stop() {
        if (!::exoPlayer.isInitialized) {
            android.util.Log.w("AudioPlayerService", "Cannot stop: ExoPlayer is not initialized")
            return
        }
        val player = exoPlayer
        
        try {
            android.util.Log.d("AudioPlayerService", "stop() called, current playbackState: ${player.playbackState}")
            player.stop()
            // ExoPlayer manages AudioFocus automatically, no need to abandon manually
        } catch (e: Exception) {
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Stop method execution")
        }
    }
    
    /**
     * Seeks to specific position.
     *
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        if (!::exoPlayer.isInitialized) return
        val player = exoPlayer
        
        try {
            if (positionMs < 0) {
                android.util.Log.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
                return
            }
            
            if (player.mediaItemCount == 0) {
                android.util.Log.w("AudioPlayerService", "Cannot seek: no media items loaded")
                return
            }
            
            val playWhenReadyBeforeSeek = player.playWhenReady
            val duration = player.duration
            val seekPosition = if (duration != C.TIME_UNSET && positionMs > duration) {
                duration
            } else {
                positionMs
            }
            
            player.seekTo(seekPosition)
            
            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to seek to position: $positionMs", e)
        }
    }
    
    /**
     * Sets playback speed.
     *
     * @param speed Playback speed (0.5x to 2.0x)
     */
    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }
    
    /**
     * Sets repeat mode.
     *
     * @param repeatMode Repeat mode:
     *   - REPEAT_MODE_OFF: No repeat
     *   - REPEAT_MODE_ONE: Repeat current track
     *   - REPEAT_MODE_ALL: Repeat all tracks
     */
    fun setRepeatMode(repeatMode: Int) {
        exoPlayer.repeatMode = repeatMode
        android.util.Log.d("AudioPlayerService", "Repeat mode set to: $repeatMode")
    }
    
    /**
     * Gets current repeat mode.
     *
     * @return Current repeat mode
     */
    fun getRepeatMode(): Int {
        return exoPlayer.repeatMode
    }
    
    /**
     * Sets shuffle mode.
     *
     * @param shuffleModeEnabled true to enable shuffle, false to disable
     */
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        exoPlayer.shuffleModeEnabled = shuffleModeEnabled
        android.util.Log.d("AudioPlayerService", "Shuffle mode set to: $shuffleModeEnabled")
    }
    
    /**
     * Gets current shuffle mode.
     *
     * @return true if shuffle is enabled, false otherwise
     */
    fun getShuffleModeEnabled(): Boolean {
        return exoPlayer.shuffleModeEnabled
    }
    
    /**
     * Skips to next track.
     */
    fun next() {
        exoPlayer.seekToNextMediaItem()
        android.util.Log.d("AudioPlayerService", "Skipping to next track")
    }
    
    /**
     * Skips to previous track.
     */
    fun previous() {
        exoPlayer.seekToPreviousMediaItem()
        android.util.Log.d("AudioPlayerService", "Skipping to previous track")
    }
    
    /**
     * Seeks to specific track by index.
     *
     * @param index Track index in playlist
     */
    fun seekToTrack(index: Int) {
        if (!::exoPlayer.isInitialized) return
        val player = exoPlayer
        if (index >= 0 && index < player.mediaItemCount) {
            val playWhenReadyBeforeSeek = player.playWhenReady
            player.seekTo(index, 0L)
            
            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Sets playback progress from saved position.
     * 
     * Inspired by lissen-android: restores playback position across multiple tracks/chapters
     * using cumulative durations for accurate seeking.
     * 
     * @param filePaths List of file paths (for reference, actual durations come from MediaItems)
     * @param progressSeconds Progress in seconds (overall position across all tracks)
     */
    fun setPlaybackProgress(filePaths: List<String>, progressSeconds: Double?) {
        if (!::exoPlayer.isInitialized) return
        val player = exoPlayer
        
        if (filePaths.isEmpty() || player.mediaItemCount == 0) {
            android.util.Log.w("AudioPlayerService", "Cannot set playback progress: empty file list or no media items")
            return
        }
        
        when (progressSeconds) {
            null, 0.0 -> {
                // No saved progress, start from beginning
                player.seekTo(0, 0L)
                android.util.Log.d("AudioPlayerService", "No saved progress, starting from beginning")
            }
            else -> {
                // Calculate which track and position to seek to
                // Use ChapterUtils functions inspired by lissen-android
                val positionMs = (progressSeconds * 1000).toLong()
                
                // Get actual durations from MediaItems (more accurate than estimation)
                val durationsMs = mutableListOf<Long>()
                
                // Collect actual durations from MediaItems if available
                for (i in 0 until player.mediaItemCount) {
                    // Note: mediaItem is retrieved but duration is not available in metadata
                    // Actual duration is only available after MediaItem is loaded
                    // For now, we'll use a hybrid approach: try to get from player if available
                    val itemDuration = if (i == player.currentMediaItemIndex && player.duration != C.TIME_UNSET) {
                        // For current item, use player's duration
                        player.duration
                    } else {
                        // For other items, estimate based on average or use a default
                        // In a full implementation, you'd preload durations or store them
                        // For now, estimate 5 minutes per track (conservative estimate)
                        5 * 60 * 1000L
                    }
                    durationsMs.add(itemDuration)
                }
                
                // Use ChapterUtils to calculate target chapter index and position
                val targetChapterIndex = calculateChapterIndexMs(durationsMs, positionMs)
                val chapterPositionMs = calculateChapterPositionMs(durationsMs, positionMs)
                
                when {
                    targetChapterIndex >= 0 && targetChapterIndex < player.mediaItemCount -> {
                        // Found valid chapter
                        val clampedProgress = chapterPositionMs.coerceAtLeast(0L)
                        player.seekTo(targetChapterIndex, clampedProgress)
                        android.util.Log.d("AudioPlayerService", "Restored playback: track=$targetChapterIndex, position=${clampedProgress}ms (from ${progressSeconds}s)")
                    }
                    else -> {
                        // Position is beyond all tracks or at the end, seek to last track
                        val lastChapterIndex = player.mediaItemCount - 1
                        if (lastChapterIndex >= 0) {
                            val lastChapterDuration = durationsMs.lastOrNull() ?: 0L
                            player.seekTo(lastChapterIndex, lastChapterDuration)
                            android.util.Log.d("AudioPlayerService", "Progress beyond all tracks, seeking to last track $lastChapterIndex at end")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Rewinds playback by specified seconds.
     *
     * @param seconds Number of seconds to rewind (default: 15)
     */
    fun rewind(seconds: Int = 15) {
        if (!::exoPlayer.isInitialized) return
        val player = exoPlayer
        val currentPosition = player.currentPosition
        val newPosition = (currentPosition - seconds * 1000L).coerceAtLeast(0L)
        player.seekTo(newPosition)
        android.util.Log.d("AudioPlayerService", "Rewind: ${seconds}s (from ${currentPosition}ms to ${newPosition}ms)")
    }
    
    /**
     * Forwards playback by specified seconds.
     *
     * @param seconds Number of seconds to forward (default: 30)
     */
    fun forward(seconds: Int = 30) {
        if (!::exoPlayer.isInitialized) return
        val player = exoPlayer
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration != C.TIME_UNSET) {
            val newPosition = (currentPosition + seconds * 1000L).coerceAtMost(duration)
            player.seekTo(newPosition)
            android.util.Log.d("AudioPlayerService", "Forward: ${seconds}s (from ${currentPosition}ms to ${newPosition}ms)")
        }
    }
    
    /**
     * Gets current playback position.
     *
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }
    
    /**
     * Gets total duration of current media.
     *
     * @return Duration in milliseconds, or 0 if unknown
     */
    fun getDuration(): Long {
        return exoPlayer.duration
    }
    
    /**
     * Gets current player state.
     *
     * @return Map with player state information
     */
    fun getPlayerState(): Map<String, Any> {
        if (!::exoPlayer.isInitialized) return emptyMap()
        val player = exoPlayer
        return mapOf(
            "isPlaying" to player.isPlaying,
            "playWhenReady" to player.playWhenReady, // Added for debugging
            "playbackState" to player.playbackState,
            "currentPosition" to player.currentPosition,
            "duration" to (if (player.duration == C.TIME_UNSET) 0L else player.duration),
            "currentIndex" to (player.currentMediaItemIndex),
            "playbackSpeed" to player.playbackParameters.speed,
            "repeatMode" to player.repeatMode,
            "shuffleModeEnabled" to player.shuffleModeEnabled,
            "mediaItemCount" to player.mediaItemCount
        )
    }
    
    /**
     * Gets information about current media item.
     *
     * @return Map with current media item information, or empty map if no item
     */
    fun getCurrentMediaItemInfo(): Map<String, Any?> {
        if (!::exoPlayer.isInitialized) return emptyMap()
        val player = exoPlayer
        val currentItem = player.currentMediaItem ?: return emptyMap()
        val metadata = currentItem.mediaMetadata
        
        // Get artwork path - prefer embedded artwork if available
        val artworkPath = embeddedArtworkPath?.takeIf { 
            val file = File(it)
            file.exists() && file.length() > 0
        } ?: metadata.artworkUri?.toString()
        
        return mapOf(
            "mediaId" to currentItem.mediaId,
            "uri" to currentItem.localConfiguration?.uri?.toString(),
            "title" to (metadata.title?.toString()),
            "artist" to (metadata.artist?.toString()),
            "albumTitle" to (metadata.albumTitle?.toString()),
            "hasArtwork" to (metadata.artworkUri != null || metadata.artworkData != null),
            "artworkPath" to artworkPath // Path to artwork (embedded or external)
        )
    }
    
    /**
     * Extracts embedded artwork from audio file metadata using Android MediaMetadataRetriever.
     *
     * This method runs in a background thread to avoid blocking the main thread.
     *
     * @param filePath Path to the audio file
     * @return Path to saved artwork file, or null if no artwork found
     */
    fun extractArtworkFromFile(filePath: String): String? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        android.util.Log.w("AudioPlayerService", "File does not exist: $filePath")
                        return@withContext null
                    }
                    
                    // Use Android MediaMetadataRetriever to extract embedded artwork
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(filePath)
                        
                        // Try to get embedded picture (album art)
                        val picture = retriever.embeddedPicture
                        if (picture != null && picture.isNotEmpty()) {
                            // Save artwork to cache
                            val cacheDir = applicationContext.cacheDir
                            val artworkFile = File(cacheDir, "embedded_artwork_${filePath.hashCode()}.jpg")
                            artworkFile.outputStream().use { it.write(picture) }
                            android.util.Log.i("AudioPlayerService", "Extracted and saved artwork from $filePath to ${artworkFile.absolutePath}")
                            return@withContext artworkFile.absolutePath
                        }
                        
                        android.util.Log.d("AudioPlayerService", "No embedded artwork found in $filePath")
                        null
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to extract artwork from $filePath", e)
                    null
                }
            }
        }
    }
    
    /**
     * Gets playlist information.
     *
     * @return Map with playlist information
     */
    fun getPlaylistInfo(): Map<String, Any> {
        if (!::exoPlayer.isInitialized) return emptyMap()
        val player = exoPlayer
        return mapOf(
            "itemCount" to player.mediaItemCount,
            "currentIndex" to player.currentMediaItemIndex,
            "hasNext" to player.hasNextMediaItem(),
            "hasPrevious" to player.hasPreviousMediaItem(),
            "repeatMode" to player.repeatMode,
            "shuffleModeEnabled" to player.shuffleModeEnabled
        )
    }
    
    /**
     * Player event listener with improved error handling and retry logic.
     * 
     * Inspired by lissen-android implementation for better error recovery.
     * Uses onEvents() for more efficient event handling (Media3 1.8+).
     */
    private val playerListener = object : Player.Listener {
        private var retryCount = 0
        private val maxRetries = 3
        private val retryDelayMs = 2000L // 2 seconds
        
        // Use onEvents() for more efficient event handling (inspired by lissen-android)
        // This allows handling multiple events in one callback for better performance
        override fun onEvents(player: Player, events: Player.Events) {
            // Log all events for debugging
            android.util.Log.d("AudioPlayerService", "onEvents called: ${events.toString()}")
            
            // Handle playback state changes
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                val playbackState = player.playbackState
                val stateName = when(playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                android.util.Log.i("AudioPlayerService", "EVENT_PLAYBACK_STATE_CHANGED: $stateName, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, mediaItemCount=${player.mediaItemCount}")
                
                // Update notification when state changes
                // MediaSession automatically updates from ExoPlayer state
                notificationManager?.updateNotification()
                
                // Reset retry count on successful playback
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    retryCount = 0
                }
                
                // Handle errors
                if (playbackState == Player.STATE_IDLE) {
                    val error = player.playerError
                    if (error != null) {
                        android.util.Log.e("AudioPlayerService", "Playback error: ${error.message}", error)
                        handlePlayerError(error)
                    }
                }
            }
            
            // Handle playWhenReady changes (important for AudioFocus debugging)
            if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                android.util.Log.i("AudioPlayerService", "EVENT_PLAY_WHEN_READY_CHANGED: playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, playbackState=${player.playbackState}, mediaItemCount=${player.mediaItemCount}")
                // Match lissen-android: just log, don't interfere with ExoPlayer's AudioFocus handling
                notificationManager?.updateNotification()
            }
            
            // Handle playing state changes
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                val isPlaying = player.isPlaying
                val playbackState = player.playbackState
                val stateName = when(playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                android.util.Log.i("AudioPlayerService", "EVENT_IS_PLAYING_CHANGED: isPlaying=$isPlaying, playWhenReady=${player.playWhenReady}, playbackState=$stateName, mediaItemCount=${player.mediaItemCount}")
                
                // Don't reset playWhenReady automatically - let ExoPlayer handle AudioFocus
                // The previous check was too aggressive and was preventing playback from starting
                
                notificationManager?.updateNotification()
            }
            
            // Handle media item transitions
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                // Track changed - update notification to show new track's embedded artwork
                // MediaSession automatically updates from ExoPlayer
                notificationManager?.updateNotification()
                
                // Log track transition for debugging (inspired by lissen-android logging)
                val currentIndex = player.currentMediaItemIndex
                val currentItem = player.currentMediaItem
                val title = currentItem?.mediaMetadata?.title?.toString() ?: "Unknown"
                android.util.Log.d("AudioPlayerService", "Media item transition:")
                android.util.Log.d("AudioPlayerService", "  - Index: $currentIndex")
                android.util.Log.d("AudioPlayerService", "  - Title: $title")
                android.util.Log.d("AudioPlayerService", "  - Total items: ${player.mediaItemCount}")
                
                // Reset retry count on track change (new track might work even if previous failed)
                retryCount = 0
            }
            
            // Handle playback parameters changes (speed, pitch, etc.)
            if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                val params = player.playbackParameters
                android.util.Log.d("AudioPlayerService", "Playback parameters changed: speed=${params.speed}, pitch=${params.pitch}")
            }
            
            // Handle repeat mode changes
            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                android.util.Log.d("AudioPlayerService", "Repeat mode changed: ${player.repeatMode}")
            }
            
            // Handle shuffle mode changes
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                android.util.Log.d("AudioPlayerService", "Shuffle mode changed: ${player.shuffleModeEnabled}")
            }
        }
        
        // Keep individual listeners for backward compatibility and specific handling
        override fun onPlaybackStateChanged(playbackState: Int) {
            // This is also handled in onEvents, but kept for explicit handling
            notificationManager?.updateNotification()
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // This is also handled in onEvents, but kept for explicit handling
            notificationManager?.updateNotification()
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e("AudioPlayerService", "Player error occurred", error)
            handlePlayerError(error)
        }
        
        /**
         * Handles player errors with automatic retry for network errors.
         * 
         * Inspired by lissen-android: improved error handling with detailed messages.
         * 
         * @param error The playback error that occurred
         */
        private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
            ErrorHandler.handlePlaybackError("AudioPlayerService", error, "Player error during playback")
            
            val errorCode = error.errorCode
            val userFriendlyMessage = when (errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                    // Network errors - try to retry automatically
                    if (retryCount < maxRetries) {
                        retryCount++
                        android.util.Log.w("AudioPlayerService", "Network connection failed, retrying ($retryCount/$maxRetries)...")
                        
                        // Retry after delay with exponential backoff
                        val backoffDelay = retryDelayMs * retryCount
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            exoPlayer.prepare()
                            android.util.Log.d("AudioPlayerService", "Retry attempt $retryCount after network error (delay: ${backoffDelay}ms)")
                        }, backoffDelay)
                        
                        return // Don't show error message yet, wait for retry
                    }
                    "Network error: Unable to connect. Please check your internet connection."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    if (retryCount < maxRetries) {
                        retryCount++
                        android.util.Log.w("AudioPlayerService", "Network timeout, retrying ($retryCount/$maxRetries)...")
                        val backoffDelay = retryDelayMs * retryCount
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            exoPlayer.prepare()
                        }, backoffDelay)
                        return
                    }
                    "Network timeout: Connection timed out. Please try again."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    "Server error: Unable to load audio from server. Please try again later."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                    // File not found - try to skip to next available track
                    handleFileNotFound()
                    "File not found: Audio file is missing or has been moved."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                    "Permission denied: Cannot access audio file. Please check file permissions."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    "Format error: Audio file is corrupted or in an unsupported format."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                    "Decoder error: Unable to decode audio. The format may not be supported on this device."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> {
                    "Audio error: Unable to initialize audio playback. Please try again."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                    "Audio error: Failed to write audio data. Please try again."
                }
                else -> {
                    val errorMessage = error.message ?: "Unknown error"
                    "Playback error: $errorMessage (code: $errorCode)"
                }
            }
            
            android.util.Log.e("AudioPlayerService", "Player error (user-friendly): $userFriendlyMessage")
            notificationManager?.updateNotification()
            
            // Store error for retrieval via MethodChannel if needed
            // Error will be automatically propagated through state stream
        }
        
        /**
         * Handles file not found errors by attempting to skip to next available track.
         * 
         * Inspired by lissen-android's approach to handle missing files gracefully.
         */
        private fun handleFileNotFound() {
            if (!::exoPlayer.isInitialized) return
            val player = exoPlayer
            val currentIndex = player.currentMediaItemIndex
            val totalTracks = player.mediaItemCount
            
            if (totalTracks <= 1) {
                // Only one track or no tracks, can't skip
                android.util.Log.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
                return
            }
            
            // Try to skip to next track
            val nextIndex = (currentIndex + 1) % totalTracks
            if (nextIndex != currentIndex) {
                android.util.Log.w("AudioPlayerService", "File not found at index $currentIndex, skipping to next track $nextIndex")
                try {
                    player.seekTo(nextIndex, 0L)
                    // Auto-play next track if player was playing
                    // Use playWhenReady instead of play() for better compatibility
                    if (player.isPlaying || player.playWhenReady) {
                        player.playWhenReady = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to skip to next track", e)
                    // Don't rethrow - log and continue
                }
            } else {
                // No more tracks available, pause playback
                android.util.Log.w("AudioPlayerService", "No more tracks available, pausing playback")
                try {
                    player.playWhenReady = false
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to pause playback", e)
                }
            }
        }
        
        // onMediaItemTransition is now handled in onEvents() for better performance
        // Keeping this for backward compatibility if needed
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // This is also handled in onEvents, but kept for explicit handling if needed
            val currentIndex = exoPlayer.currentMediaItemIndex
            android.util.Log.d("AudioPlayerService", "Media item transition (explicit): index=$currentIndex, reason=$reason")
        }
        
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Handle position discontinuities (e.g., track changes, seeks)
            // Inspired by lissen-android: handle unavailable tracks gracefully
            val previousIndex = oldPosition.mediaItemIndex
            val currentIndex = newPosition.mediaItemIndex
            
            if (currentIndex != previousIndex) {
                android.util.Log.d("AudioPlayerService", "Position discontinuity: $previousIndex -> $currentIndex, reason=$reason")
                
                // Reset retry count on track change
                retryCount = 0
                
                // Inspired by lissen-android: check if current track is available
                // If track is not available, try to find next available track
                val currentItem = exoPlayer.currentMediaItem
                if (currentItem != null) {
                    // Check if track URI is accessible
                    val uri = currentItem.localConfiguration?.uri
                    if (uri != null) {
                        // For file URIs, check if file exists
                        if (uri.scheme == "file") {
                            val file = File(uri.path ?: "")
                            if (!file.exists() || !file.canRead()) {
                                android.util.Log.w("AudioPlayerService", "Current track file not accessible: ${uri.path}, trying to skip")
                                // Try to skip to next available track
                                skipToNextAvailableTrack(currentIndex, previousIndex)
                            }
                        }
                    }
                }
            }
        }
        
        /**
         * Skips to next available track if current track is unavailable.
         * Inspired by lissen-android's PlaybackNotificationService.
         * 
         * @param currentIndex Current track index
         * @param previousIndex Previous track index
         */
        private fun skipToNextAvailableTrack(currentIndex: Int, previousIndex: Int) {
            if (!::exoPlayer.isInitialized) return
            val player = exoPlayer
            
            if (player.mediaItemCount <= 1) {
                // Only one track or no tracks, can't skip
                android.util.Log.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
                return
            }
            
            // Determine direction (forward or backward)
            val direction = when {
                currentIndex > previousIndex || (currentIndex == 0 && previousIndex == player.mediaItemCount - 1) -> 1 // FORWARD
                else -> -1 // BACKWARD
            }
            
            // Try to find next available track
            var nextIndex = currentIndex
            var attempts = 0
            val maxAttempts = player.mediaItemCount
            
            while (attempts < maxAttempts) {
                nextIndex = when (direction) {
                    1 -> (nextIndex + 1) % player.mediaItemCount
                    else -> if (nextIndex - 1 < 0) player.mediaItemCount - 1 else nextIndex - 1
                }
                
                // Check if this track is available
                val item = player.getMediaItemAt(nextIndex)
                val uri = item.localConfiguration?.uri
                
                if (uri != null) {
                    val isAvailable = when (uri.scheme) {
                        "file" -> {
                            val file = File(uri.path ?: "")
                            file.exists() && file.canRead()
                        }
                        "http", "https" -> true // Assume network URLs are available
                        else -> true // Assume other schemes are available
                    }
                    
                    if (isAvailable) {
                        android.util.Log.d("AudioPlayerService", "Found available track at index $nextIndex, seeking to it")
                        try {
                            player.seekTo(nextIndex, 0L)
                            // Restore playWhenReady if was playing
                            if (player.playWhenReady) {
                                player.playWhenReady = true
                            }
                            return
                        } catch (e: Exception) {
                            android.util.Log.e("AudioPlayerService", "Failed to seek to available track", e)
                        }
                    }
                }
                
                attempts++
            }
            
            // No available tracks found, pause playback
            android.util.Log.w("AudioPlayerService", "No available tracks found, pausing playback")
            try {
                player.playWhenReady = false
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to pause playback", e)
            }
        }
        
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            // Metadata (including embedded artwork) was extracted from audio file
            // Media3 1.8: artworkData and artworkUri are NOT deprecated - use them directly
            // Inspired by lissen-android: prefer artworkUri over artworkData for better performance
            val title = mediaMetadata.title?.toString() ?: "Unknown"
            val artist = mediaMetadata.artist?.toString() ?: "Unknown"
            val album = mediaMetadata.albumTitle?.toString()
            
            // Log metadata extraction for debugging
            android.util.Log.d("AudioPlayerService", "Metadata changed: title=$title, artist=$artist, album=$album")
            
            // Check if artwork is available (prefer URI, then data)
            val artworkUri = mediaMetadata.artworkUri
            val artworkData = mediaMetadata.artworkData
            val hasArtworkData = artworkData != null && artworkData.isNotEmpty()
            val hasArtworkUri = artworkUri != null
            
            if (artworkUri != null) {
                android.util.Log.d("AudioPlayerService", "Artwork URI available: $artworkUri")
                // Clear embedded artwork path if external URI is available
                embeddedArtworkPath = null
            } else if (hasArtworkData) {
                android.util.Log.d("AudioPlayerService", "Embedded artwork data available: ${artworkData?.size ?: 0} bytes")
                // Save embedded artwork to temporary file for Flutter access
                try {
                    val cacheDir = applicationContext.cacheDir
                    val artworkFile = File(cacheDir, "embedded_artwork_${System.currentTimeMillis()}.jpg")
                    artworkFile.outputStream().use { it.write(artworkData) }
                    embeddedArtworkPath = artworkFile.absolutePath
                    android.util.Log.i("AudioPlayerService", "Saved embedded artwork to: $embeddedArtworkPath")
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to save embedded artwork", e)
                    embeddedArtworkPath = null
                }
            } else {
                android.util.Log.d("AudioPlayerService", "No artwork available")
                embeddedArtworkPath = null
            }
            
            android.util.Log.d("AudioPlayerService", "Media metadata changed:")
            android.util.Log.d("AudioPlayerService", "  Title: $title")
            android.util.Log.d("AudioPlayerService", "  Artist: $artist")
            android.util.Log.d("AudioPlayerService", "  Has artworkData: $hasArtworkData (${artworkData?.size ?: 0} bytes)")
            android.util.Log.d("AudioPlayerService", "  Has artworkUri: $hasArtworkUri (${mediaMetadata.artworkUri})")
            
            if (hasArtworkData || hasArtworkUri) {
                android.util.Log.i("AudioPlayerService", "Artwork found! Updating notification...")
            } else {
                android.util.Log.w("AudioPlayerService", "No artwork found in metadata")
            }
            
            // Update notification to show artwork
            // MediaSession automatically updates from ExoPlayer
            notificationManager?.updateMetadata(currentMetadata, embeddedArtworkPath)
        }
    }
    
    override fun onDestroy() {
        instance = null
        _isFullyInitialized = false
        
        // ExoPlayer manages AudioFocus automatically, no need to abandon manually
        
        // Cancel coroutine scope (inspired by lissen-android)
        playerServiceScope.cancel()
        
        // IMPORTANT: Do NOT call exoPlayer.release() - it's a singleton via Hilt!
        // Hilt automatically manages the lifecycle of ExoPlayer
        // Just clear MediaItems, but don't release the player
        exoPlayer.clearMediaItems()
        
        // Cleanup other resources
        // NOTE: Do NOT release mediaCache - it's a singleton via Hilt and will be managed by Hilt
        
        mediaSession?.release()
        mediaSession = null
        mediaSessionManager?.release()
        playbackTimer?.release()
        super.onDestroy()
    }
}


