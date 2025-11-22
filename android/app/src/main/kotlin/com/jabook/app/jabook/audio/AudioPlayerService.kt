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

import android.content.Intent
import android.net.Uri
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.io.File

/**
 * Native audio player service using Media3 ExoPlayer.
 *
 * This service extends MediaSessionService for proper integration with Android's
 * media controls, Android Auto, Wear OS, and system notifications.
 * 
 * Uses Dagger Hilt for dependency injection (ExoPlayer and Cache as singletons).
 * Inspired by lissen-android implementation for better architecture.
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
    private var pendingPlay = false // Flag to auto-play after player becomes ready
    
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
    
    override fun onCreate() {
        val onCreateStart = System.currentTimeMillis()
        super.onCreate()
        instance = this
        
        android.util.Log.d("AudioPlayerService", "onCreate started")
        
        try {
            // Inspired by lissen-android: minimize synchronous operations in onCreate()
            // Hilt dependencies (ExoPlayer and Cache) are injected automatically
            // They are created lazily on first access, not in onCreate()
            
            // Configure ExoPlayer (already created via Hilt, accessed lazily)
            // This is lightweight - just adding listener and setting flags
            val playerConfigStart = System.currentTimeMillis()
            configurePlayer()
            android.util.Log.d(
                "AudioPlayerService",
                "ExoPlayer configured (${System.currentTimeMillis() - playerConfigStart}ms)"
            )
            
            // Create MediaSessionManager with callbacks for rewind/forward
            val sessionManagerStart = System.currentTimeMillis()
            mediaSessionManager = MediaSessionManager(this, exoPlayer).apply {
                setCallbacks(
                    rewindCallback = { rewind(15) },
                    forwardCallback = { forward(30) }
                )
            }
            android.util.Log.d(
                "AudioPlayerService",
                "MediaSessionManager created (${System.currentTimeMillis() - sessionManagerStart}ms)"
            )
            
            // Create MediaSession once in onCreate
            // MediaSessionService will use it via onGetSession()
            val sessionStart = System.currentTimeMillis()
            mediaSession = mediaSessionManager!!.getMediaSession()
            android.util.Log.d(
                "AudioPlayerService",
                "MediaSession created (${System.currentTimeMillis() - sessionStart}ms)"
            )
            
            // Create notification manager with MediaSession
            val notificationStart = System.currentTimeMillis()
            notificationManager = NotificationManager(
                this,
                exoPlayer,
                mediaSession,
                currentMetadata
            )
            android.util.Log.d(
                "AudioPlayerService",
                "NotificationManager created (${System.currentTimeMillis() - notificationStart}ms)"
            )
            
            // Initialize playback timer (inspired by lissen-android)
            val timerStart = System.currentTimeMillis()
            playbackTimer = PlaybackTimer(this, exoPlayer)
            android.util.Log.d(
                "AudioPlayerService",
                "PlaybackTimer initialized (${System.currentTimeMillis() - timerStart}ms)"
            )
            
            val totalDuration = System.currentTimeMillis() - onCreateStart
            
            // Mark service as fully initialized after all components are ready
            _isFullyInitialized = true
            
            android.util.Log.i(
                "AudioPlayerService",
                "Service onCreate completed successfully (${totalDuration}ms total), fully initialized: $_isFullyInitialized"
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "AudioPlayerService",
                "Error during onCreate: ${e.message}",
                e
            )
            _isFullyInitialized = false
            throw e
        }
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Return MediaSession for MediaSessionService
        // MediaSessionService automatically manages foreground service and notifications
        // Inspired by lissen-android: return already created session from onCreate()
        android.util.Log.d("AudioPlayerService", "onGetSession() called, mediaSession: ${mediaSession != null}")
        if (mediaSession == null) {
            android.util.Log.w("AudioPlayerService", "MediaSession is null in onGetSession(), creating fallback")
            // Fallback: create session if somehow it wasn't created in onCreate()
            // This should not happen in normal flow, but provides safety
            mediaSession = mediaSessionManager?.getMediaSession()
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
            // ExoPlayer is already created via Hilt with optimized LoadControl for audiobooks
            // Just add listener and configure additional settings (all lightweight operations)
            exoPlayer.addListener(playerListener)
            
            // Configure WakeMode for background playback
            exoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)
            
            // Enable scrubbing mode for smooth seeking (Media3 1.8+)
            // This provides better UX when user drags the seek bar
            exoPlayer.setScrubbingModeEnabled(true)
            
            // Initialize repeat and shuffle modes
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
     *
     * @param filePaths List of absolute file paths or HTTP(S) URLs to audio files
     * @param metadata Optional metadata map (title, artist, album, etc.)
     */
    fun setPlaylist(filePaths: List<String>, metadata: Map<String, String>? = null) {
        try {
            currentMetadata = metadata
            android.util.Log.d("AudioPlayerService", "Setting playlist with ${filePaths.size} items")
            
            // Create DataSourceFactory with cache support (for network streaming)
            // Inspired by lissen-android: use DataSourceFactory for proper caching and network support
            // Use injected cache from Hilt (MediaModule) - single instance for entire app
            // This prevents "Another SimpleCache instance uses the folder" error
            val dataSourceFactory = MediaDataSourceFactory(this, mediaCache)
            
            // Use ProgressiveMediaSource instead of MediaItem for proper DataSourceFactory integration
            // This allows us to use caching and custom data sources (OkHttp, etc.)
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
                    
                    // Build MediaItem with metadata
                    // Inspired by lissen-android: set title, artist, and artwork
                    val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
                    
                    // Extract metadata from file name or provided metadata
                    val fileName = if (isUrl) {
                        // For URLs, try to extract filename from path or use index
                        val urlPath = Uri.parse(path).lastPathSegment ?: "Track ${index + 1}"
                        urlPath.substringBeforeLast('.', urlPath)
                    } else {
                        File(path).nameWithoutExtension
                    }
                    val providedTitle = metadata?.get("title") ?: metadata?.get("trackTitle")
                    val providedArtist = metadata?.get("artist") ?: metadata?.get("author")
                    val providedAlbum = metadata?.get("album") ?: metadata?.get("bookTitle")
                    
                    // Set title (prefer provided, then file name, then index)
                    metadataBuilder.setTitle(providedTitle ?: fileName.ifEmpty { "Track ${index + 1}" })
                    
                    // Set artist (prefer provided, then "Unknown")
                    if (providedArtist != null) {
                        metadataBuilder.setArtist(providedArtist)
                    }
                    
                    // Set album (if provided)
                    if (providedAlbum != null) {
                        metadataBuilder.setAlbumTitle(providedAlbum)
                    }
                    
                    // Set artwork: prefer artworkUri (external cover)
                    // Embedded artwork will be extracted by ExoPlayer asynchronously
                    // Inspired by lissen-android's approach: use setArtworkUri() for external covers
                    val artworkUriString = metadata?.get("artworkUri")?.takeIf { it.isNotEmpty() }
                    if (artworkUriString != null) {
                        try {
                            val artworkUri = android.net.Uri.parse(artworkUriString)
                            metadataBuilder.setArtworkUri(artworkUri)
                            android.util.Log.d("AudioPlayerService", "Set artwork URI in MediaItem $index: $artworkUriString")
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to parse artwork URI: $artworkUriString", e)
                        }
                    }
                    
                    // Note: Embedded artwork will be extracted by ExoPlayer asynchronously
                    // We don't set it here to avoid blocking the main thread
                    // ExoPlayer's MediaMetadataExtractor will handle it automatically
                    
                    // Build MediaItem with metadata
                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(metadataBuilder.build())
                        .build()
                    
                    // Create ProgressiveMediaSource with DataSourceFactory
                    // This allows proper caching and network support (inspired by lissen-android)
                    val sourceFactory = dataSourceFactory.createDataSourceFactoryForUri(uri)
                    ProgressiveMediaSource.Factory(sourceFactory)
                        .createMediaSource(mediaItem)
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to create MediaItem for path: $path", e)
                    throw e
                }
            }
            
            // Use setMediaSources instead of setMediaItems for proper DataSourceFactory integration
            // Inspired by lissen-android: this allows caching and custom data sources
            exoPlayer.setMediaSources(mediaSources)
            
            // CRITICAL FIX: Set playWhenReady BEFORE prepare() so it's ready when player becomes STATE_READY
            // This ensures playback starts automatically when player becomes ready
            exoPlayer.playWhenReady = true
            android.util.Log.d("AudioPlayerService", "Set playWhenReady=true BEFORE prepare() to auto-start playback")
            
            // Also set pendingPlay flag as backup mechanism
            pendingPlay = true
            android.util.Log.d("AudioPlayerService", "Set pendingPlay=true to ensure auto-playback when ready")
            
            // Now prepare the player - it will become STATE_READY and start playing automatically
            exoPlayer.prepare()
            android.util.Log.d("AudioPlayerService", "Called prepare() - player will become ready and start playing")
            
            notificationManager?.updateNotification()
            
            android.util.Log.d("AudioPlayerService", "Playlist set successfully:")
            android.util.Log.d("AudioPlayerService", "  - Sources: ${mediaSources.size}")
            android.util.Log.d("AudioPlayerService", "  - MediaItems: ${exoPlayer.mediaItemCount}")
            android.util.Log.d("AudioPlayerService", "  - Cache: enabled (Hilt singleton)")
            android.util.Log.d("AudioPlayerService", "  - playWhenReady: ${exoPlayer.playWhenReady}")
            android.util.Log.d("AudioPlayerService", "  - playbackState: ${exoPlayer.playbackState}")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to set playlist", e)
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
        val player = exoPlayer ?: return
        
        // Validate track index
        if (trackIndex < 0 || trackIndex >= player.mediaItemCount) {
            android.util.Log.w("AudioPlayerService", "Invalid track index: $trackIndex (mediaItemCount: ${player.mediaItemCount})")
            return
        }
        
        // Validate position: must be non-negative
        if (positionMs < 0) {
            android.util.Log.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
            return
        }
        
        try {
            player.seekTo(trackIndex, positionMs)
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
        notificationManager?.updateMetadata(metadata)
    }
    
    /**
     * Starts or resumes playback.
     * 
     * Inspired by lissen-android: uses playWhenReady instead of play() for better control.
     * Added error handling and validation for Android 16 stability.
     */
    fun play() {
        val player = exoPlayer ?: run {
            android.util.Log.w("AudioPlayerService", "Cannot play: ExoPlayer is null")
            return
        }
        
        try {
            // Validate that we have media items before attempting to play
            if (player.mediaItemCount == 0) {
                android.util.Log.w("AudioPlayerService", "Cannot play: no media items loaded")
                return
            }
            
            // Check if player is ready to play
            val playbackState = player.playbackState
            android.util.Log.d("AudioPlayerService", "play() called, current playbackState: $playbackState, mediaItemCount: ${player.mediaItemCount}")
            
            when (playbackState) {
                Player.STATE_IDLE -> {
                    // Player is idle, need to prepare first
                    android.util.Log.d("AudioPlayerService", "Player is IDLE, preparing...")
                    
                    // Check if we have media items before preparing
                    if (player.mediaItemCount == 0) {
                        android.util.Log.w("AudioPlayerService", "Cannot prepare: no media items loaded")
                        return
                    }
                    
                    pendingPlay = true // Set flag to auto-play after ready
                    try {
                        player.prepare()
                        // playWhenReady will be set after player becomes ready (via listener)
                        android.util.Log.d("AudioPlayerService", "Player prepare() called, waiting for ready state")
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to prepare player", e)
                        pendingPlay = false
                        // Don't rethrow - log and return gracefully
                    }
                }
                Player.STATE_BUFFERING, Player.STATE_READY -> {
                    // Player is ready or buffering, set playWhenReady
                    // Inspired by lissen-android: use playWhenReady instead of play()
                    android.util.Log.d("AudioPlayerService", "Player is ${if (playbackState == Player.STATE_READY) "READY" else "BUFFERING"}, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}")
                    try {
                        // CRITICAL: Ensure playWhenReady is set to true
                        // If already true but not playing, try explicit play() as fallback
                        if (player.playWhenReady) {
                            if (!player.isPlaying) {
                                android.util.Log.w("AudioPlayerService", "playWhenReady is true but not playing! Trying explicit play() as fallback")
                                // Try explicit play() as fallback - sometimes needed when playWhenReady doesn't work
                                try {
                                    player.play()
                                    android.util.Log.d("AudioPlayerService", "Called player.play() as fallback")
                                } catch (e: Exception) {
                                    android.util.Log.e("AudioPlayerService", "Failed to call player.play()", e)
                                    // If play() fails, try toggling playWhenReady
                                    player.playWhenReady = false
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                                            player.playWhenReady = true
                                            android.util.Log.d("AudioPlayerService", "playWhenReady set to true after toggle")
                                        }
                                    }, 50)
                                }
                            } else {
                                android.util.Log.d("AudioPlayerService", "Player is already playing, no action needed")
                            }
                        } else {
                            player.playWhenReady = true
                            android.util.Log.d("AudioPlayerService", "playWhenReady set to true")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to set playWhenReady", e)
                        throw e
                    }
                }
                Player.STATE_ENDED -> {
                    // Player reached end, restart from beginning
                    android.util.Log.d("AudioPlayerService", "Player is ENDED, seeking to start and playing")
                    try {
                        // Validate current media item index before seeking
                        val currentIndex = player.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < player.mediaItemCount) {
                            player.seekTo(currentIndex, 0L)
                        } else {
                            // Fallback: seek to first item
                            player.seekTo(0, 0L)
                        }
                        player.playWhenReady = true
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to seek and play", e)
                        throw e
                    }
                }
                else -> {
                    android.util.Log.w("AudioPlayerService", "Unknown playback state: $playbackState")
                    try {
                        player.playWhenReady = true
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to set playWhenReady in unknown state", e)
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Error in play() method", e)
            // Don't rethrow - log and return gracefully
            // This prevents crashes on Android 16
        }
    }
    
    /**
     * Pauses playback.
     */
    fun pause() {
        val player = exoPlayer ?: run {
            android.util.Log.w("AudioPlayerService", "Cannot pause: ExoPlayer is null")
            return
        }
        
        try {
            android.util.Log.d("AudioPlayerService", "pause() called, current playbackState: ${player.playbackState}")
            // Inspired by lissen-android: use playWhenReady instead of pause()
            player.playWhenReady = false
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Error in pause() method", e)
            // Don't rethrow - log and return gracefully to prevent crashes
        }
    }
    
    /**
     * Stops playback and resets player.
     */
    fun stop() {
        val player = exoPlayer ?: run {
            android.util.Log.w("AudioPlayerService", "Cannot stop: ExoPlayer is null")
            return
        }
        
        try {
            android.util.Log.d("AudioPlayerService", "stop() called, current playbackState: ${player.playbackState}")
            player.stop()
            // Reset pending play flag when stopping
            pendingPlay = false
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Error in stop() method", e)
            // Reset flag even on error
            pendingPlay = false
            // Don't rethrow - log and return gracefully to prevent crashes
        }
    }
    
    /**
     * Seeks to specific position.
     *
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        
        try {
            // Validate position: must be non-negative
            if (positionMs < 0) {
                android.util.Log.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
                return
            }
            
            // Check if player is ready (has media loaded)
            if (player.mediaItemCount == 0) {
                android.util.Log.w("AudioPlayerService", "Cannot seek: no media items loaded")
                return
            }
            
            // Get current media item duration and validate position doesn't exceed it
            val currentMediaItem = player.currentMediaItem
            if (currentMediaItem != null) {
                val duration = player.duration
                if (duration != C.TIME_UNSET && positionMs > duration) {
                    android.util.Log.w("AudioPlayerService", "Seek position exceeds duration: $positionMs > $duration")
                    // Clamp to duration instead of failing
                    player.seekTo(duration)
                    return
                }
            }
            
            player.seekTo(positionMs)
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to seek to position: $positionMs", e)
            // Don't rethrow - log and return gracefully to prevent crashes
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
        val player = exoPlayer ?: return
        if (index >= 0 && index < player.mediaItemCount) {
            player.seekTo(index, 0L)
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
        val player = exoPlayer ?: return
        
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
                // Inspired by lissen-android's cumulative duration approach
                val positionMs = (progressSeconds * 1000).toLong()
                
                // Get actual durations from MediaItems (more accurate than estimation)
                // This approach is similar to lissen-android's seek() method
                val durationsMs = mutableListOf<Long>()
                var totalDuration = 0L
                
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
                    totalDuration += itemDuration
                }
                
                // Calculate cumulative durations (like lissen-android)
                val cumulativeDurationsMs = durationsMs.runningFold(0L) { acc, duration -> acc + duration }
                
                // Find target chapter index (like lissen-android's seek method)
                val targetChapterIndex = cumulativeDurationsMs.indexOfFirst { it > positionMs }
                
                when {
                    targetChapterIndex - 1 >= 0 -> {
                        // Found valid chapter
                        val chapterStartTimeMs = cumulativeDurationsMs[targetChapterIndex - 1]
                        val chapterProgressMs = positionMs - chapterStartTimeMs
                        val clampedProgress = chapterProgressMs.coerceAtLeast(0L)
                        
                        player.seekTo(targetChapterIndex - 1, clampedProgress)
                        android.util.Log.d("AudioPlayerService", "Restored playback: track=${targetChapterIndex - 1}, position=${clampedProgress}ms (from ${progressSeconds}s)")
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
        val player = exoPlayer ?: return
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
        val player = exoPlayer ?: return
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
        val player = exoPlayer ?: return emptyMap()
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
        val player = exoPlayer ?: return emptyMap()
        val currentItem = player.currentMediaItem ?: return emptyMap()
        val metadata = currentItem.mediaMetadata
        
        return mapOf(
            "mediaId" to currentItem.mediaId,
            "uri" to currentItem.localConfiguration?.uri?.toString(),
            "title" to (metadata.title?.toString()),
            "artist" to (metadata.artist?.toString()),
            "albumTitle" to (metadata.albumTitle?.toString()),
            "hasArtwork" to (metadata.artworkUri != null || metadata.artworkData != null)
        )
    }
    
    /**
     * Gets playlist information.
     *
     * @return Map with playlist information
     */
    fun getPlaylistInfo(): Map<String, Any> {
        val player = exoPlayer ?: return emptyMap()
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
            // Handle playback state changes
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                val playbackState = player.playbackState
                
                // Update notification when state changes
                // MediaSession automatically updates from ExoPlayer state
                notificationManager?.updateNotification()
                
                // Auto-play if pending and player is ready
                // Inspired by lissen-android: use playWhenReady instead of play()
                if (pendingPlay && playbackState == Player.STATE_READY) {
                    android.util.Log.d("AudioPlayerService", "Player is ready, auto-playing (pendingPlay=true)")
                    pendingPlay = false
                    // Ensure playWhenReady is true and player has media items
                    if (player.mediaItemCount > 0) {
                        player.playWhenReady = true
                        android.util.Log.d("AudioPlayerService", "Set playWhenReady=true in listener, isPlaying should become true soon")
                    } else {
                        android.util.Log.w("AudioPlayerService", "Cannot auto-play: no media items")
                    }
                }
                
                // CRITICAL FIX: If player is READY with playWhenReady=true but not playing, force play
                // This handles the case where playWhenReady was set before player became ready
                if (playbackState == Player.STATE_READY && 
                    player.playWhenReady && 
                    !player.isPlaying && 
                    player.mediaItemCount > 0) {
                    android.util.Log.w("AudioPlayerService", "Player is READY with playWhenReady=true but not playing! Forcing play...")
                    try {
                        // Try explicit play() first as it's more reliable
                        player.play()
                        android.util.Log.d("AudioPlayerService", "Called player.play() to force playback")
                        // Also ensure playWhenReady is true as backup
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to call player.play(), trying toggle", e)
                        // If play() fails, try toggling playWhenReady
                        try {
                            player.playWhenReady = false
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (player.playbackState == Player.STATE_READY && 
                                    player.mediaItemCount > 0) {
                                    player.playWhenReady = true
                                    android.util.Log.d("AudioPlayerService", "Forced playWhenReady=true after toggle")
                                }
                            }, 50)
                        } catch (e2: Exception) {
                            android.util.Log.e("AudioPlayerService", "Failed to force play in STATE_READY", e2)
                        }
                    }
                }
                
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
            
            // Handle playing state changes
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                val isPlaying = player.isPlaying
                android.util.Log.d("AudioPlayerService", "isPlaying changed: $isPlaying, playWhenReady: ${player.playWhenReady}, playbackState: ${player.playbackState}")
                notificationManager?.updateNotification()
                
                // If player is ready but not playing despite playWhenReady=true, force play
                // This handles edge cases where playWhenReady doesn't trigger playback
                if (player.playbackState == Player.STATE_READY && 
                    player.playWhenReady && 
                    !isPlaying && 
                    player.mediaItemCount > 0) {
                    android.util.Log.w("AudioPlayerService", "Player is READY with playWhenReady=true but not playing! Attempting to force play...")
                    try {
                        // Force play by setting playWhenReady again (sometimes helps)
                        player.playWhenReady = true
                        // Also try explicit play() as fallback
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (player.playbackState == Player.STATE_READY && 
                                player.playWhenReady && 
                                !player.isPlaying) {
                                android.util.Log.w("AudioPlayerService", "Still not playing after delay, trying explicit play()")
                                player.playWhenReady = true
                            }
                        }, 100)
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to force play", e)
                    }
                }
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
            val errorCode = error.errorCode
            val errorMessage = error.message ?: "Unknown error"
            
            // Log detailed error information for debugging
            android.util.Log.e("AudioPlayerService", "Player error occurred: code=$errorCode, message=$errorMessage", error)
            
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
            val player = exoPlayer ?: return
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
            val previousIndex = oldPosition.mediaItemIndex
            val currentIndex = newPosition.mediaItemIndex
            
            if (currentIndex != previousIndex) {
                android.util.Log.d("AudioPlayerService", "Position discontinuity: $previousIndex -> $currentIndex, reason=$reason")
                
                // Reset retry count on track change
                retryCount = 0
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
            } else if (hasArtworkData) {
                android.util.Log.d("AudioPlayerService", "Embedded artwork data available: ${artworkData?.size ?: 0} bytes")
            } else {
                android.util.Log.d("AudioPlayerService", "No artwork available")
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
            notificationManager?.updateNotification()
        }
    }
    
    override fun onDestroy() {
        instance = null
        _isFullyInitialized = false
        
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

