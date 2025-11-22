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
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
import java.io.File

/**
 * Native audio player service using Media3 ExoPlayer.
 *
 * This service extends MediaSessionService for proper integration with Android's
 * media controls, Android Auto, Wear OS, and system notifications.
 * 
 * Uses stable Media3 1.6.0 APIs only.
 * 
 * Inspired by lissen-android implementation for better architecture.
 */
class AudioPlayerService : MediaSessionService() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaCacheManager: MediaCacheManager? = null
    private var playbackTimer: PlaybackTimer? = null
    private var currentMetadata: Map<String, String>? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        @Volatile
        private var instance: AudioPlayerService? = null
        
        fun getInstance(): AudioPlayerService? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize cache manager (for future network streaming support)
        mediaCacheManager = MediaCacheManager(this)
        
        initializePlayer()
        
        // Create MediaSessionManager with callbacks for rewind/forward
        mediaSessionManager = MediaSessionManager(this, exoPlayer!!).apply {
            setCallbacks(
                rewindCallback = { rewind(15) },
                forwardCallback = { forward(30) }
            )
        }
        
        // Create MediaSession once in onCreate
        // MediaSessionService will use it via onGetSession()
        mediaSession = mediaSessionManager!!.getMediaSession()
        
        // Create notification manager with MediaSession
        notificationManager = NotificationManager(
            this,
            exoPlayer!!,
            mediaSession,
            currentMetadata
        )
        
        // Initialize playback timer (inspired by lissen-android)
        playbackTimer = PlaybackTimer(this, exoPlayer!!)
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Return MediaSession for MediaSessionService
        // MediaSessionService automatically manages foreground service and notifications
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
     * Initializes ExoPlayer instance with proper configuration for audiobooks.
     * 
     * Note: For local files, caching is not needed as files are already on disk.
     * For future network streaming support, CacheDataSource with SimpleCache
     * can be added here for offline playback and reduced network usage.
     */
    private fun initializePlayer() {
        try {
            // Configure LoadControl for increased buffer (30-60 seconds)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,  // minBufferMs
                    60_000,  // maxBufferMs
                    2_500,   // bufferForPlaybackMs
                    5_000    // bufferForPlaybackAfterRebufferMs
                )
                .build()
            
            // Build ExoPlayer with custom LoadControl
            exoPlayer = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    addListener(playerListener)
                    
                    // Configure AudioAttributes for audiobooks
                    // handleAudioFocus=true means ExoPlayer will automatically:
                    // - Pause when audio focus is lost (incoming calls, other apps)
                    // - Resume when audio focus is regained (call ends)
                    // - Duck volume when temporary focus loss (notifications, navigation)
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // for audiobooks
                        .setUsage(C.USAGE_MEDIA)
                        .build()
                    setAudioAttributes(audioAttributes, true) // handleAudioFocus=true enables automatic focus management
                    
                    // Configure WakeMode for background playback
                    setWakeMode(C.WAKE_MODE_LOCAL)
                    
                    // Handle audio becoming noisy (e.g., headphones unplugged, Bluetooth disconnected)
                    // Inspired by plan: пауза при отключении гарнитуры
                    setHandleAudioBecomingNoisy(true)
                    
                    // Enable scrubbing mode for smooth seeking (Media3 1.8+)
                    // This provides better UX when user drags the seek bar
                    setScrubbingModeEnabled(true)
                    
                    // Initialize repeat and shuffle modes (from plan: синхронизированы между UI, MediaSession и плеером)
                    repeatMode = Player.REPEAT_MODE_OFF
                    shuffleModeEnabled = false
                }
            
            android.util.Log.d("AudioPlayerService", "ExoPlayer initialized successfully with:")
            android.util.Log.d("AudioPlayerService", "  - LoadControl: min=30000ms, max=60000ms")
            android.util.Log.d("AudioPlayerService", "  - AudioAttributes: contentType=SPEECH, usage=MEDIA")
            android.util.Log.d("AudioPlayerService", "  - Scrubbing mode: enabled")
            android.util.Log.d("AudioPlayerService", "  - Handle audio becoming noisy: enabled")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to initialize ExoPlayer", e)
            throw e
        }
    }
    
    /**
     * Sets playlist from file paths.
     *
     * @param filePaths List of absolute file paths to audio files
     * @param metadata Optional metadata map (title, artist, album, etc.)
     */
    fun setPlaylist(filePaths: List<String>, metadata: Map<String, String>? = null) {
        try {
            currentMetadata = metadata
            android.util.Log.d("AudioPlayerService", "Setting playlist with ${filePaths.size} files")
            
            // Create DataSourceFactory with cache support (for future network streaming)
            // Inspired by lissen-android: use DataSourceFactory for proper caching and network support
            val cache = mediaCacheManager?.getCache()
            val dataSourceFactory = MediaDataSourceFactory(this, cache)
            
            // Use ProgressiveMediaSource instead of MediaItem for proper DataSourceFactory integration
            // This allows us to use caching and custom data sources (OkHttp, etc.)
            val mediaSources = filePaths.mapIndexed { index, path ->
                try {
                    // Use Uri.fromFile() for proper local file handling
                    // This ensures ExoPlayer can properly extract metadata from the file
                    val file = File(path)
                    if (!file.exists()) {
                        android.util.Log.w("AudioPlayerService", "File does not exist: $path")
                    }
                    val uri = Uri.fromFile(file)
                    
                    android.util.Log.d("AudioPlayerService", "Creating MediaItem $index: $path (uri: $uri)")
                    
                    // Try to extract embedded artwork using MediaMetadataRetriever as fallback
                    // ExoPlayer should extract it automatically, but we can help it
                    var artworkData: ByteArray? = null
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(path)
                        artworkData = retriever.embeddedPicture
                        retriever.release()
                        
                        if (artworkData != null) {
                            android.util.Log.d("AudioPlayerService", "Found embedded artwork in file $index: ${artworkData.size} bytes")
                        } else {
                            android.util.Log.d("AudioPlayerService", "No embedded artwork found in file $index")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AudioPlayerService", "Failed to extract artwork from file $index: ${e.message}")
                    }
                    
                    // Build MediaItem with metadata
                    // Inspired by lissen-android: set title, artist, and artwork
                    val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
                    
                    // Extract metadata from file name or provided metadata
                    val fileName = file.nameWithoutExtension
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
                    
                    // Set artwork: prefer artworkUri (external cover), then artworkData (embedded)
                    // Inspired by lissen-android's approach: use setArtworkUri() for external covers
                    val artworkUri = metadata?.get("artworkUri")?.takeIf { it.isNotEmpty() }
                    if (artworkUri != null) {
                        try {
                            val uri = android.net.Uri.parse(artworkUri)
                            metadataBuilder.setArtworkUri(uri)
                            android.util.Log.d("AudioPlayerService", "Set artwork URI in MediaItem $index: $artworkUri")
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to parse artwork URI: $artworkUri", e)
                        }
                    }
                    
                    // If no artworkUri, use embedded artwork data (if available)
                    if (artworkUri == null && artworkData != null && artworkData.isNotEmpty()) {
                        metadataBuilder.setArtworkData(artworkData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        android.util.Log.d("AudioPlayerService", "Set embedded artwork data in MediaItem $index: ${artworkData.size} bytes")
                    }
                    
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
            exoPlayer?.setMediaSources(mediaSources)
            exoPlayer?.prepare()
            notificationManager?.updateNotification()
            
            android.util.Log.d("AudioPlayerService", "Playlist set successfully:")
            android.util.Log.d("AudioPlayerService", "  - Sources: ${mediaSources.size}")
            android.util.Log.d("AudioPlayerService", "  - MediaItems: ${exoPlayer?.mediaItemCount ?: 0}")
            android.util.Log.d("AudioPlayerService", "  - Cache: ${if (cache != null) "enabled" else "disabled"}")
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
     */
    fun play() {
        exoPlayer?.play()
    }
    
    /**
     * Pauses playback.
     */
    fun pause() {
        exoPlayer?.pause()
    }
    
    /**
     * Stops playback and resets player.
     */
    fun stop() {
        exoPlayer?.stop()
    }
    
    /**
     * Seeks to specific position.
     *
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        
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
        
        try {
            player.seekTo(positionMs)
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
        exoPlayer?.setPlaybackSpeed(speed)
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
        exoPlayer?.repeatMode = repeatMode
        android.util.Log.d("AudioPlayerService", "Repeat mode set to: $repeatMode")
    }
    
    /**
     * Gets current repeat mode.
     *
     * @return Current repeat mode
     */
    fun getRepeatMode(): Int {
        return exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
    }
    
    /**
     * Sets shuffle mode.
     *
     * @param shuffleModeEnabled true to enable shuffle, false to disable
     */
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        exoPlayer?.shuffleModeEnabled = shuffleModeEnabled
        android.util.Log.d("AudioPlayerService", "Shuffle mode set to: $shuffleModeEnabled")
    }
    
    /**
     * Gets current shuffle mode.
     *
     * @return true if shuffle is enabled, false otherwise
     */
    fun getShuffleModeEnabled(): Boolean {
        return exoPlayer?.shuffleModeEnabled ?: false
    }
    
    /**
     * Skips to next track.
     */
    fun next() {
        exoPlayer?.seekToNextMediaItem()
        android.util.Log.d("AudioPlayerService", "Skipping to next track")
    }
    
    /**
     * Skips to previous track.
     */
    fun previous() {
        exoPlayer?.seekToPreviousMediaItem()
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
                    val mediaItem = player.getMediaItemAt(i)
                    // Try to get duration from MediaItem metadata or estimate
                    // Note: Actual duration is only available after MediaItem is loaded
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
        return exoPlayer?.currentPosition ?: 0L
    }
    
    /**
     * Gets total duration of current media.
     *
     * @return Duration in milliseconds, or 0 if unknown
     */
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
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
                            exoPlayer?.prepare()
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
                            exoPlayer?.prepare()
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
                    if (player.isPlaying) {
                        player.play()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to skip to next track", e)
                }
            } else {
                // No more tracks available, pause playback
                android.util.Log.w("AudioPlayerService", "No more tracks available, pausing playback")
                player.pause()
            }
        }
        
        // onMediaItemTransition is now handled in onEvents() for better performance
        // Keeping this for backward compatibility if needed
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // This is also handled in onEvents, but kept for explicit handling if needed
            val currentIndex = exoPlayer?.currentMediaItemIndex ?: -1
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
        exoPlayer?.release()
        mediaSession?.release()
        mediaSession = null
        mediaSessionManager?.release()
        mediaCacheManager?.release()
        playbackTimer?.release()
        super.onDestroy()
    }
}

