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
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.common.AudioAttributes as M3AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
class PlayerService : MediaSessionService(), Player.Listener {

    private val TAG = "PlayerService"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocus = AudioManager.AUDIOFOCUS_NONE

    private var currentMediaItem: MediaItem? = null
    private var isPlaying = false
    private var playbackPosition = 0L
    private val isInitialized = AtomicBoolean(false)
    private val serviceStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        setupAudioFocus()
        setupNotificationChannel()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (exoPlayer?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    /**
     * Build ExoPlayer and MediaSession (Media3).
     */
    private fun initializePlayer() {
        if (isInitialized.get()) return
        try {
            // These attributes are for ExoPlayer (Media3), not for AudioFocus.
            val trackAttrs = M3AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // ← fixed constant
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .build()
                .also { player ->
                    player.addListener(this@PlayerService)
                    // This sets attributes for audio routing and lets player manage focus alongside session.
                    player.setAudioAttributes(trackAttrs, /* handleAudioFocus = */ true)
                }

            mediaSession = MediaSession.Builder(this, exoPlayer!!).build()

            isInitialized.set(true)
            serviceStarted.set(true)
            startForeground(NOTIFICATION_ID, createNotification())

            Media3Log.i(TAG, "Player initialized successfully")
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to initialize player", e)
        }
    }

    /**
     * Configure modern audio focus (API 26+).
     */
    private fun setupAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // For AudioFocusRequest (android.media), usage is sufficient; avoid deprecated content-type constants.
        val frameworkAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(frameworkAttrs)
            .setOnAudioFocusChangeListener { handleAudioFocusChange(it) }
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .build()
    }

    /**
     * Notification channel for foreground playback.
     */
    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "JaBook Playback", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "JaBook Audio Playback"
            setShowBadge(false)
            setSound(null, null)
        }
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager?.createNotificationChannel(channel)
    }

    /**
     * System audio focus callback.
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocus = AudioManager.AUDIOFOCUS_GAIN
                if (!isPlaying) play()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioFocus = focusChange
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioFocus = focusChange
                exoPlayer?.volume = 0.3f
            }
        }
    }

    /**
     * Start playback (requests focus first).
     */
    fun play() {
        try {
            if (!requestAudioFocus()) {
                Media3Log.w(TAG, "Could not get audio focus")
                return
            }
            exoPlayer?.playWhenReady = true
            isPlaying = true
            updateNotification()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to play", e)
        }
    }

    /**
     * Pause playback (and release focus).
     */
    fun pause() {
        try {
            exoPlayer?.playWhenReady = false
            isPlaying = false
            updateNotification()
            abandonAudioFocus()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to pause", e)
        }
    }

    /**
     * Stop playback, clear items and release focus.
     */
    fun stop() {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            isPlaying = false
            playbackPosition = 0L
            currentMediaItem = null
            updateNotification()
            abandonAudioFocus()
            stopSelf()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to stop", e)
        }
    }

    /**
     * Set current item and optional start position.
     */
    fun setMediaItem(mediaItem: MediaItem, position: Long = 0L) {
        try {
            currentMediaItem = mediaItem
            playbackPosition = position
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.seekTo(position)
            // Media3 session reads metadata from MediaItem automatically.
            updateNotification()
        } catch (e: Exception) {
            Media3Log.e(TAG, "Failed to set media item", e)
        }
    }

    /**
     * Always use modern focus API on API 26+.
     */
    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val req = audioFocusRequest ?: return false
        val result = manager.requestAudioFocus(req)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Always use modern abandon API on API 26+.
     */
    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        val req = audioFocusRequest ?: return
        manager.abandonAudioFocusRequest(req)
    }

    /**
     * Build the ongoing playback notification.
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, Class.forName("com.jabook.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
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

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * Optional album art loader (kept for future use).
     */
    private fun loadAlbumArt(uri: String?, callback: (Bitmap?) -> Unit) {
        uri?.let {
            Glide.with(this).asBitmap().load(it)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        callback(resource)
                    }
                    override fun onLoadFailed(errorDrawable: Drawable?) { callback(null) }
                    override fun onLoadCleared(placeholder: Drawable?) { callback(null) }
                })
        } ?: callback(null)
    }

    // --- Player.Listener ---

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> if (exoPlayer?.playWhenReady == true) isPlaying = true
            Player.STATE_ENDED -> { isPlaying = false; stop() }
            else -> isPlaying = false
        }
        updateNotification()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        updateNotification()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        @Player.DiscontinuityReason reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            playbackPosition = exoPlayer?.currentPosition ?: 0L
        }
    }

    private fun cleanup() {
        try {
            scope.cancel()
            exoPlayer?.release(); exoPlayer = null
            mediaSession?.release(); mediaSession = null
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
    }
}