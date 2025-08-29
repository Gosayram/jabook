package com.jabook.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.trackselection.TrackSelectionArray
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
// Note: MainActivity import removed - core modules should not depend on app module
// Consider using a base activity interface or context-only approach
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Audio player service for JaBook app
 * Handles background audio playback with ExoPlayer and MediaSession
 */
@UnstableApi
class PlayerService : MediaSessionService(), Player.Listener {
    
    private val TAG = "PlayerService"
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Player components
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    
    // Audio focus
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocus = AudioManager.AUDIOFOCUS_NONE
    
    // Current playback state
    private var currentMediaItem: MediaItem? = null
    private var isPlaying = false
    private var playbackPosition = 0L
    private val isInitialized = AtomicBoolean(false)
    
    // Service state
    private val serviceStarted = AtomicBoolean(false)
    
    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        setupAudioFocus()
        setupNotificationChannel()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (exoPlayer?.playWhenReady != true) {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }
    
    /**
     * Initializes the player and media session
     */
    private fun initializePlayer() {
        if (isInitialized.get()) return
        
        try {
            // Create ExoPlayer with custom configuration
            val trackSelector = DefaultTrackSelector(this)
            val audioSink = createAudioSink()
            
            exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setAudioSink(audioSink)
                .setUseLazyPreparation(true)
                .build()
                .also {
                    it.addListener(this@PlayerService)
                }
            
            // Create media session
            mediaSession = MediaSession.Builder(this, exoPlayer!!).build().also {
                it.setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        play()
                    }
                    
                    override fun onPause() {
                        pause()
                    }
                    
                    override fun onSkipToNext() {
                        exoPlayer?.seekToNext()
                    }
                    
                    override fun onSkipToPrevious() {
                        exoPlayer?.seekToPrevious()
                    }
                    
                    override fun onSeekTo(pos: Long) {
                        exoPlayer?.seekTo(pos)
                    }
                    
                    override fun onStop() {
                        stop()
                    }
                })
                
                it.setActive(true)
            }
            
            isInitialized.set(true)
            serviceStarted.set(true)
            startForeground(NOTIFICATION_ID, createNotification())
            
            Media3Log.i(TAG, "Player initialized successfully")
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to initialize player", e)
        }
    }
    
    /**
     * Creates audio sink with modern configuration
     */
    private fun createAudioSink(): AudioSink {
        return DefaultAudioSink.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build()
            )
            .setOffloadEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            .build()
    }
    
    /**
     * Sets up audio focus handling
     */
    private fun setupAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .build()
        }
    }
    
    /**
     * Sets up notification channel
     */
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JaBook Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JaBook Audio Playback"
                setShowBadge(false)
                setSound(null, null)
            }
            
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Handles audio focus changes
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocus = AudioManager.AUDIOFOCUS_GAIN
                if (!isPlaying) {
                    play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                audioFocus = AudioManager.AUDIOFOCUS_LOSS
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioFocus = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioFocus = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                // Lower volume instead of pausing
                exoPlayer?.volume = 0.3f
            }
        }
    }
    
    /**
     * Plays the current media item
     */
    fun play() {
        try {
            if (!requestAudioFocus()) {
                Media3Log.w(TAG, "Could not get audio focus")
                return
            }
            
            exoPlayer?.playWhenReady = true
            isPlaying = true
            updatePlaybackState()
            updateNotification()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to play", e)
        }
    }
    
    /**
     * Pauses playback
     */
    fun pause() {
        try {
            exoPlayer?.playWhenReady = false
            isPlaying = false
            updatePlaybackState()
            updateNotification()
            abandonAudioFocus()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to pause", e)
        }
    }
    
    /**
     * Stops playback and releases resources
     */
    fun stop() {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            isPlaying = false
            playbackPosition = 0L
            currentMediaItem = null
            updatePlaybackState()
            updateNotification()
            abandonAudioFocus()
            stopSelf()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to stop", e)
        }
    }
    
    /**
     * Sets media item for playback
     */
    fun setMediaItem(mediaItem: MediaItem, position: Long = 0L) {
        try {
            currentMediaItem = mediaItem
            playbackPosition = position
            
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.seekTo(position)
            
            updateMediaSessionMetadata(mediaItem)
            updateNotification()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to set media item", e)
        }
    }
    
    /**
     * Requests audio focus
     */
    private fun requestAudioFocus(): Boolean {
        audioManager?.let { manager ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            } else {
                manager.requestAudioFocus(
                    { focusChange -> handleAudioFocusChange(focusChange) },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return false
    }
    
    /**
     * Abandons audio focus
     */
    private fun abandonAudioFocus() {
        audioManager?.let { manager ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                manager.abandonAudioFocus({ focusChange -> handleAudioFocusChange(focusChange) })
            }
        }
    }
    
    /**
     * Updates media session metadata
     */
    private fun updateMediaSessionMetadata(mediaItem: MediaItem) {
        mediaSession?.setPlayerMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(mediaItem.mediaMetadata.title?.toString() ?: "")
                .setArtist(mediaItem.mediaMetadata.artist?.toString() ?: "")
                .setAlbumTitle(mediaItem.mediaMetadata.albumTitle?.toString() ?: "")
                .setDurationMs(mediaItem.mediaMetadata.durationMillis ?: C.TIME_UNSET.toLong())
                .build()
        )
    }
    
    /**
     * Updates playback state
     */
    private fun updatePlaybackState() {
        val state = if (isPlaying) Player.STATE_READY else Player.STATE_IDLE
        val position = exoPlayer?.currentPosition ?: 0L
        
        mediaSession?.setPlayerState(
            Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_BACKWARD,
                    Player.COMMAND_STOP
                )
                .build(),
            state,
            position,
            1.0f
        )
    }
    
    /**
     * Creates notification for foreground service
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, Class.forName("com.jabook.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentMediaItem?.mediaMetadata?.title?.toString() ?: "JaBook")
            .setContentText(currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Playing audio")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Updates notification with current state
     */
    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * Loads album art for notification
     */
    private fun loadAlbumArt(uri: String?, callback: (Bitmap?) -> Unit) {
        uri?.let {
            Glide.with(this)
                .asBitmap()
                .load(it)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        callback(resource)
                    }
                    
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        callback(null)
                    }
                    
                    override fun onLoadCleared(placeholder: Drawable?) {
                        callback(null)
                    }
                })
        } ?: callback(null)
    }
    
    /**
     * Player listener callbacks
     */
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                if (exoPlayer?.playWhenReady == true) {
                    isPlaying = true
                }
            }
            Player.STATE_ENDED -> {
                isPlaying = false
                stop()
            }
            else -> {
                isPlaying = false
            }
        }
        updatePlaybackState()
        updateNotification()
    }
    
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        updatePlaybackState()
        updateNotification()
    }
    
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            playbackPosition = exoPlayer?.currentPosition ?: 0L
            updatePlaybackState()
        }
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        try {
            scope.cancel()
            
            exoPlayer?.release()
            exoPlayer = null
            
            mediaSession?.release()
            mediaSession = null
            
            abandonAudioFocus()
            
            serviceStarted.set(false)
            isInitialized.set(false)
            
            Media3Log.i(TAG, "Player service cleaned up")
        } catch (e: Exception) {
            Media3Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "jabook_playback"
        
        /**
         * Gets the current player instance
         */
        fun getPlayer(context: Context): ExoPlayer? {
            val service = context.applicationContext as? PlayerService
            return service?.exoPlayer
        }
    }
}