package com.jabook.app.features.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.jabook.app.R
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/** Foreground service for background audio playback. Handles MediaSession and playback notifications. */
@AndroidEntryPoint
@UnstableApi
class PlayerService : MediaSessionService() {
  @Inject lateinit var playerManager: PlayerManager

  @Inject lateinit var debugLogger: IDebugLogger

  private var mediaSession: MediaSession? = null
  private var currentAudiobook: Audiobook? = null
  private var currentPlaybackState: PlaybackState = PlaybackState()
  private var playbackStateJob: Job? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  companion object {
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "JaBook_Player"
    private const val CHANNEL_NAME = "JaBook Player"

    // Service actions
    const val ACTION_PLAY = "com.jabook.app.PLAY"
    const val ACTION_PAUSE = "com.jabook.app.PAUSE"
    const val ACTION_STOP = "com.jabook.app.STOP"
    const val ACTION_NEXT = "com.jabook.app.NEXT"
    const val ACTION_PREVIOUS = "com.jabook.app.PREVIOUS"
    const val ACTION_SEEK_FORWARD = "com.jabook.app.SEEK_FORWARD"
    const val ACTION_SEEK_BACKWARD = "com.jabook.app.SEEK_BACKWARD"

    // Intent extras
    const val EXTRA_AUDIOBOOK = "audiobook"

    /** Start player service with audiobook */
    fun startService(
      context: Context,
      audiobook: Audiobook,
    ) {
      val intent =
        Intent(context, PlayerService::class.java).apply {
          putExtra(EXTRA_AUDIOBOOK, audiobook)
        }
      ContextCompat.startForegroundService(context, intent)
    }

    /** Stop player service */
    fun stopService(context: Context) {
      val intent = Intent(context, PlayerService::class.java).apply { action = ACTION_STOP }
      context.startService(intent)
    }
  }

  override fun onCreate() {
    super.onCreate()
    debugLogger.logInfo("PlayerService.onCreate")

    createNotificationChannel()
    initializeMediaSession()
    startForegroundService()
    observePlaybackState()
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    super.onStartCommand(intent, flags, startId)
    debugLogger.logDebug("PlayerService.onStartCommand: ${intent?.action}")

    when (intent?.action) {
      ACTION_PLAY -> playerManager.play()
      ACTION_PAUSE -> playerManager.pause()
      ACTION_STOP -> {
        playerManager.stop()
        stopService()
      }
      ACTION_NEXT -> playerManager.nextChapter()
      ACTION_PREVIOUS -> playerManager.previousChapter()
      ACTION_SEEK_FORWARD -> playerManager.seekTo(playerManager.getCurrentPosition() + 30_000)
      ACTION_SEEK_BACKWARD -> playerManager.seekTo(playerManager.getCurrentPosition() - 15_000)
      else -> {
        // Initial start with audiobook
        val audiobook =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_AUDIOBOOK, Audiobook::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_AUDIOBOOK)
          }
        audiobook?.let { initializePlayer(it) }
      }
    }

    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onDestroy() {
    debugLogger.logInfo("PlayerService.onDestroy")
    playbackStateJob?.cancel()
    serviceScope.cancel()
    mediaSession?.release()
    mediaSession = null
    super.onDestroy()
  }

  /** Initialize MediaSession for media controls */
  private fun initializeMediaSession() {
    try {
      playerManager.getExoPlayer()?.let { exo ->
        mediaSession = MediaSession.Builder(this, exo).build()
        debugLogger.logInfo("MediaSession initialized")
      } ?: debugLogger.logError("ExoPlayer not ready for MediaSession", null)
    } catch (e: Exception) {
      debugLogger.logError("Failed to initialize MediaSession", e)
    }
  }

  /** Initialize player with audiobook */
  private fun initializePlayer(audiobook: Audiobook) {
    debugLogger.logInfo("PlayerService.initializePlayer: ${audiobook.title}")
    currentAudiobook = audiobook
    playerManager.initializePlayer(audiobook)
    updateNotification()
  }

  /** Observe playback state changes */
  private fun observePlaybackState() {
    playbackStateJob =
      playerManager
        .getPlaybackState()
        .onEach { state ->
          debugLogger.logDebug("PlayerService received playback state: ${state.isPlaying}")
          currentPlaybackState = state
          updateNotification()
        }.launchIn(serviceScope)
  }

  /** Create notification channel for Android O+ */
  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "JaBook audio player notifications"
          setShowBadge(false)
        }
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.createNotificationChannel(channel)
    }
  }

  /** Start foreground service with empty notification */
  private fun startForegroundService() {
    val notification = createNotification()
    startForeground(NOTIFICATION_ID, notification)
  }

  /** Update notification with current playback state */
  private fun updateNotification() {
    val notification = createNotification()
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(NOTIFICATION_ID, notification)
  }

  /** Create notification for playback controls */
  private fun createNotification(): Notification {
    val audiobook = currentAudiobook
    val playbackState = currentPlaybackState

    // Actions
    val playPauseAction =
      if (playbackState.isPlaying) {
        createNotificationAction(R.drawable.ic_pause_24, "Pause", ACTION_PAUSE)
      } else {
        createNotificationAction(R.drawable.ic_play_arrow_24, "Play", ACTION_PLAY)
      }
    val previousAction = createNotificationAction(R.drawable.ic_skip_previous_24, "Previous", ACTION_PREVIOUS)
    val nextAction = createNotificationAction(R.drawable.ic_skip_next_24, "Next", ACTION_NEXT)

    // Build
    val builder =
      NotificationCompat
        .Builder(this, CHANNEL_ID)
        .setContentTitle(audiobook?.title ?: "JaBook")
        .setContentText(audiobook?.author ?: "No audiobook loaded")
        .setSmallIcon(R.drawable.ic_headphones_24)
        .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground))
        .addAction(previousAction)
        .addAction(playPauseAction)
        .addAction(nextAction)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setContentIntent(createContentIntent())
        .setDeleteIntent(createDeleteIntent())
        .setOngoing(playbackState.isPlaying)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    // Use Media3 helper style with our MediaSession
    mediaSession?.let { session ->
      builder.setStyle(
        MediaStyleNotificationHelper
          .MediaStyle(session)
          .setShowActionsInCompactView(0, 1, 2),
      )
    }

    return builder.build()
  }

  /** Create notification action */
  private fun createNotificationAction(
    iconRes: Int,
    title: String,
    action: String,
  ): NotificationCompat.Action {
    val intent = Intent(this, PlayerService::class.java).apply { this.action = action }
    val pendingIntent =
      PendingIntent.getService(
        this,
        action.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Action.Builder(iconRes, title, pendingIntent).build()
  }

  /** Create content intent to open the app */
  private fun createContentIntent(): PendingIntent {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    return PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  /** Create delete intent to stop the service */
  private fun createDeleteIntent(): PendingIntent {
    val intent = Intent(this, PlayerService::class.java).apply { action = ACTION_STOP }
    return PendingIntent.getService(
      this,
      ACTION_STOP.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  /** Stop the service */
  private fun stopService() {
    debugLogger.logInfo("PlayerService.stopService")
    stopForeground(Service.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }
}
