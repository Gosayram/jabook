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

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * Native audio player service using Media3 ExoPlayer.
 *
 * This service handles audio playback in the background using ExoPlayer
 * and integrates with Android's MediaSession for system controls.
 */
@UnstableApi
class AudioPlayerService : Service() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var notificationManager: NotificationManager? = null
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
        initializePlayer()
        mediaSessionManager = MediaSessionManager(this, exoPlayer!!)
        notificationManager = NotificationManager(
            this,
            exoPlayer!!,
            mediaSessionManager?.getMediaSession(),
            currentMetadata
        )
        startForeground(NOTIFICATION_ID, notificationManager!!.createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle actions from notification
        when (intent?.action) {
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PLAY -> play()
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PAUSE -> pause()
            com.jabook.app.jabook.audio.NotificationManager.ACTION_NEXT -> next()
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PREVIOUS -> previous()
        }
        return START_STICKY
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
                    
                    // Handle audio becoming noisy (e.g., headphones unplugged)
                    setHandleAudioBecomingNoisy(true)
                }
            
            android.util.Log.d("AudioPlayerService", "ExoPlayer initialized successfully")
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
            
            val mediaItems = filePaths.mapIndexed { index, path ->
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
                    
                    // Build MediaItem - ExoPlayer will extract metadata, but we can help with artwork
                    val builder = MediaItem.Builder().setUri(uri)
                    
                    // If we found artwork, set it in metadata
                    if (artworkData != null && artworkData.isNotEmpty()) {
                        builder.setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setArtworkData(artworkData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                        )
                        android.util.Log.d("AudioPlayerService", "Set artwork in MediaItem $index")
                    }
                    
                    builder.build()
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to create MediaItem for path: $path", e)
                    throw e
                }
            }
            
            exoPlayer?.setMediaItems(mediaItems)
            exoPlayer?.prepare()
            notificationManager?.updateNotification()
            
            android.util.Log.d("AudioPlayerService", "Playlist set successfully")
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
        notificationManager?.updateMetadata(metadata)
        mediaSessionManager?.updateMetadata()
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
     * Skips to next track.
     */
    fun next() {
        exoPlayer?.seekToNextMediaItem()
    }
    
    /**
     * Skips to previous track.
     */
    fun previous() {
        exoPlayer?.seekToPreviousMediaItem()
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
            "playbackSpeed" to player.playbackParameters.speed
        )
    }
    
    /**
     * Player event listener.
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // Update notification and media session when state changes
            notificationManager?.updateNotification()
            mediaSessionManager?.updateMetadata()
            
            // Handle errors
            if (playbackState == Player.STATE_IDLE) {
                val error = exoPlayer?.playerError
                if (error != null) {
                    android.util.Log.e("AudioPlayerService", "Playback error: ${error.message}", error)
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            notificationManager?.updateNotification()
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // Get user-friendly error message
            val errorMessage = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    "Network error: Unable to load audio. Please check your internet connection."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                    "File error: Audio file not found or access denied."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    "Format error: Audio file is corrupted or unsupported format."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                    "Decoder error: Unable to decode audio. Format may not be supported."
                }
                else -> {
                    "Playback error: ${error.message ?: "Unknown error occurred"}"
                }
            }
            
            android.util.Log.e("AudioPlayerService", "Player error: $errorMessage", error)
            notificationManager?.updateNotification()
            
            // Store error for retrieval via MethodChannel if needed
            // Error will be automatically propagated through state stream
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Track changed - update notification to show new track's embedded artwork
            mediaSessionManager?.updateMetadata()
            notificationManager?.updateNotification()
        }
        
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            // Metadata (including embedded artwork) was extracted from audio file
            // Media3 1.8: artworkData and artworkUri are NOT deprecated - use them directly
            val title = mediaMetadata.title?.toString() ?: "Unknown"
            val artist = mediaMetadata.artist?.toString() ?: "Unknown"
            
            // Access artworkData directly (not deprecated in Media3 1.8)
            val artworkData = mediaMetadata.artworkData
            val hasArtworkData = artworkData != null && artworkData.isNotEmpty()
            val hasArtworkUri = mediaMetadata.artworkUri != null
            
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
            
            // Update notification and media session to show artwork
            notificationManager?.updateNotification()
            mediaSessionManager?.updateMetadata()
        }
    }
    
    override fun onDestroy() {
        instance = null
        exoPlayer?.release()
        mediaSessionManager?.release()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

