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
import android.net.Uri
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi

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
     * Initializes ExoPlayer instance.
     */
    private fun initializePlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(this).build().apply {
                addListener(playerListener)
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
                    val uri = Uri.parse("file://$path")
                    MediaItem.Builder()
                        .setUri(uri)
                        .apply {
                            metadata?.let { meta ->
                                setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(meta["title"] ?: "")
                                        .setArtist(meta["artist"] ?: "")
                                        .setAlbumTitle(meta["album"] ?: "")
                                        .build()
                                )
                            }
                        }
                        .build()
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
        if (trackIndex >= 0 && trackIndex < player.mediaItemCount) {
            player.seekTo(trackIndex, positionMs)
        }
    }
    
    /**
     * Updates metadata for current track.
     *
     * @param metadata Metadata map (title, artist, album, coverPath)
     */
    fun updateMetadata(metadata: Map<String, String>) {
        currentMetadata = metadata
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
        exoPlayer?.seekTo(positionMs)
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
            "duration" to (player.duration ?: 0L),
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
            android.util.Log.e("AudioPlayerService", "Player error: ${error.message}", error)
            notificationManager?.updateNotification()
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Track changed
            mediaSessionManager?.updateMetadata()
            notificationManager?.updateNotification()
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

