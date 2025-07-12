package com.jabook.app.features.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Chapter
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

/** ExoPlayer implementation of PlayerManager interface Handles audiobook playback with Media3 ExoPlayer */
@UnstableApi
@Singleton
class PlayerManagerImpl @Inject constructor(@ApplicationContext private val context: Context, private val debugLogger: IDebugLogger) :
    PlayerManager, Player.Listener, AudioManager.OnAudioFocusChangeListener {

    private var exoPlayer: ExoPlayer? = null
    private var currentAudiobook: Audiobook? = null
    private var chapters: List<Chapter> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerEndTime: Long = 0

    // Audio Focus management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var playWhenReady = false

    private val _playbackState = MutableStateFlow(PlaybackState())

    override fun getExoPlayer(): ExoPlayer? = exoPlayer

    override fun initializePlayer(audiobook: Audiobook) {
        debugLogger.logInfo("PlayerManagerImpl.initializePlayer: ${audiobook.title}")

        try {
            // Release existing player if any
            release()

            // Create new ExoPlayer instance
            exoPlayer =
                ExoPlayer.Builder(context)
                    .setSeekBackIncrementMs(15000) // 15 seconds
                    .setSeekForwardIncrementMs(30000) // 30 seconds
                    .build()

            exoPlayer?.addListener(this)

            // Store current audiobook
            currentAudiobook = audiobook

            // For now, create a single chapter from the audiobook
            // Actual chapter loading from repository will be implemented later
            chapters =
                if (audiobook.localAudioPath != null) {
                    listOf(
                        Chapter(
                            id = "${audiobook.id}_chapter_1",
                            audiobookId = audiobook.id,
                            chapterNumber = 1,
                            title = audiobook.title,
                            filePath = audiobook.localAudioPath,
                            durationMs = audiobook.durationMs,
                            isDownloaded = audiobook.isDownloaded,
                        )
                    )
                } else {
                    emptyList()
                }

            if (chapters.isNotEmpty()) {
                // Create media items from chapters
                val mediaItems = createMediaItems(chapters)

                // Set media items directly
                exoPlayer?.setMediaItems(mediaItems)
                exoPlayer?.prepare()
            }

            // Update playback state
            updatePlaybackState()

            debugLogger.logInfo("PlayerManagerImpl.initializePlayer completed for: ${audiobook.title}")
        } catch (e: Exception) {
            debugLogger.logError("PlayerManagerImpl.initializePlayer failed", e)
            updatePlaybackState(error = "Failed to initialize player: ${e.message}")
        }
    }

    override fun play() {
        debugLogger.logDebug("PlayerManagerImpl.play called")
        playWhenReady = true
        if (requestAudioFocus()) {
            exoPlayer?.play()
        }
    }

    override fun pause() {
        debugLogger.logDebug("PlayerManagerImpl.pause called")
        playWhenReady = false
        exoPlayer?.pause()
    }

    override fun stop() {
        debugLogger.logDebug("PlayerManagerImpl.stop called")
        exoPlayer?.stop()
        cancelSleepTimer()
        updatePlaybackState()
    }

    override fun seekTo(position: Long) {
        debugLogger.logDebug("PlayerManagerImpl.seekTo: $position")
        exoPlayer?.seekTo(position)
    }

    override fun seekToChapter(chapterIndex: Int) {
        debugLogger.logDebug("PlayerManagerImpl.seekToChapter: $chapterIndex")
        if (chapterIndex >= 0 && chapterIndex < chapters.size) {
            exoPlayer?.seekTo(chapterIndex, 0)
        }
    }

    override fun nextChapter() {
        debugLogger.logDebug("PlayerManagerImpl.nextChapter called")
        exoPlayer?.seekToNextMediaItem()
    }

    override fun previousChapter() {
        debugLogger.logDebug("PlayerManagerImpl.previousChapter called")
        exoPlayer?.seekToPreviousMediaItem()
    }

    override fun setPlaybackSpeed(speed: Float) {
        debugLogger.logDebug("PlayerManagerImpl.setPlaybackSpeed: $speed")
        exoPlayer?.setPlaybackSpeed(speed)
        updatePlaybackState()
    }

    override fun setSleepTimer(minutes: Int) {
        debugLogger.logDebug("PlayerManagerImpl.setSleepTimer: $minutes minutes")

        cancelSleepTimer()

        sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000)

        sleepTimerRunnable =
            object : Runnable {
                override fun run() {
                    val remainingTime = sleepTimerEndTime - System.currentTimeMillis()
                    if (remainingTime <= 0) {
                        // Timer expired, pause playback
                        pause()
                        debugLogger.logInfo("PlayerManagerImpl.sleepTimer expired")
                        sleepTimerEndTime = 0
                    } else {
                        // Schedule next check
                        handler.postDelayed(this, 1000)
                    }
                    updatePlaybackState()
                }
            }

        handler.post(sleepTimerRunnable!!)
    }

    override fun cancelSleepTimer() {
        debugLogger.logDebug("PlayerManagerImpl.cancelSleepTimer called")
        sleepTimerRunnable?.let { handler.removeCallbacks(it) }
        sleepTimerRunnable = null
        sleepTimerEndTime = 0
        updatePlaybackState()
    }

    override fun getPlaybackState(): Flow<PlaybackState> {
        return _playbackState
            .asStateFlow()
            .sample(100) // Throttle updates to every 100ms for better performance
            .distinctUntilChanged { old, new ->
                // Only emit if significant changes occurred
                old.isPlaying == new.isPlaying &&
                    old.isPaused == new.isPaused &&
                    old.isBuffering == new.isBuffering &&
                    old.isCompleted == new.isCompleted &&
                    old.currentChapterIndex == new.currentChapterIndex &&
                    old.playbackSpeed == new.playbackSpeed &&
                    old.error == new.error &&
                    kotlin.math.abs(old.currentPosition - new.currentPosition) <
                        1000 && // Only update if position changed by more than 1 second
                    kotlin.math.abs(old.duration - new.duration) < 1000
            }
    }

    override fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0
    }

    override fun getDuration(): Long {
        return exoPlayer?.duration ?: 0
    }

    override fun getCurrentChapter(): Chapter? {
        val currentIndex = exoPlayer?.currentMediaItemIndex ?: return null
        return chapters.getOrNull(currentIndex)
    }

    override fun getPlaybackSpeed(): Float {
        return exoPlayer?.playbackParameters?.speed ?: 1.0f
    }

    override fun getSleepTimerRemaining(): Long {
        return if (sleepTimerEndTime > 0) {
            maxOf(0, sleepTimerEndTime - System.currentTimeMillis())
        } else {
            0
        }
    }

    override fun release() {
        debugLogger.logDebug("PlayerManagerImpl.release called")

        cancelSleepTimer()
        abandonAudioFocus()
        exoPlayer?.removeListener(this)
        exoPlayer?.release()
        exoPlayer = null
        currentAudiobook = null
        chapters = emptyList()

        // Reset playback state
        _playbackState.value = PlaybackState()
    }

    // ExoPlayer.Listener implementation
    override fun onPlaybackStateChanged(playbackState: Int) {
        debugLogger.logDebug("PlayerManagerImpl.onPlaybackStateChanged: $playbackState")
        updatePlaybackState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        debugLogger.logDebug("PlayerManagerImpl.onIsPlayingChanged: $isPlaying")
        updatePlaybackState()
    }

    override fun onPlayerError(error: PlaybackException) {
        debugLogger.logError("PlayerManagerImpl ExoPlayer error", error)
        updatePlaybackState(error = "Playback error: ${error.message}")
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        debugLogger.logDebug("PlayerManagerImpl.onMediaItemTransition: reason=$reason")
        updatePlaybackState()
    }

    /** Create media items from chapters */
    private fun createMediaItems(chapters: List<Chapter>): List<MediaItem> {
        return chapters.mapIndexed { index, chapter ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(chapter.filePath)))
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(chapter.title).setTrackNumber(index + 1).setTotalTrackCount(chapters.size).build()
                )
                .build()
        }
    }

    /** Request audio focus for playback */
    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes =
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()

                audioFocusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(this)
                        .build()

                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION") audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        debugLogger.logDebug("Audio focus request result: $result")
        return hasAudioFocus
    }

    /** Abandon audio focus */
    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return

        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            } else {
                @Suppress("DEPRECATION") audioManager.abandonAudioFocus(this)
            }

        hasAudioFocus = false
        debugLogger.logDebug("Audio focus abandoned, result: $result")
    }

    /** Handle audio focus changes */
    override fun onAudioFocusChange(focusChange: Int) {
        debugLogger.logDebug("Audio focus change: $focusChange")

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback if we were playing before
                if (playWhenReady && exoPlayer?.isPlaying == false) {
                    exoPlayer?.play()
                }
                exoPlayer?.volume = 1.0f
                hasAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Pause playback and abandon focus
                playWhenReady = false
                exoPlayer?.pause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback but keep focus
                if (exoPlayer?.isPlaying == true) {
                    exoPlayer?.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower the volume
                exoPlayer?.volume = 0.3f
            }
        }
        updatePlaybackState()
    }

    /** Update internal playback state */
    private fun updatePlaybackState(error: String? = null) {
        val player = exoPlayer

        val state =
            if (player != null) {
                PlaybackState(
                    isPlaying = player.isPlaying,
                    isPaused = !player.isPlaying && player.playbackState == Player.STATE_READY,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                    isCompleted = player.playbackState == Player.STATE_ENDED,
                    currentPosition = player.currentPosition,
                    duration = player.duration,
                    currentChapterIndex = player.currentMediaItemIndex,
                    playbackSpeed = player.playbackParameters.speed,
                    sleepTimerRemaining = getSleepTimerRemaining(),
                    error = error,
                )
            } else {
                PlaybackState(error = error)
            }

        _playbackState.value = state
    }
}
