package com.jabook.app.features.player

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Chapter
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import javax.inject.Inject
import javax.inject.Singleton

private class SleepTimerDelegate(
    private val sleepTimerManager: SleepTimerManager,
    private val onTimerFinished: () -> Unit,
    private val onStateChanged: () -> Unit,
) {
    fun setSleepTimer(minutes: Int) {
        sleepTimerManager.setSleepTimer(minutes) {
            onTimerFinished()
            onStateChanged()
        }
    }
    fun cancelSleepTimer() {
        sleepTimerManager.cancelSleepTimer()
        onStateChanged()
    }
    fun getSleepTimerRemaining(): Long = sleepTimerManager.getSleepTimerRemaining()
}

/** ExoPlayer implementation of PlayerManager interface Handles audiobook playback with Media3 ExoPlayer */
@UnstableApi
@Singleton
class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugLogger: IDebugLogger,
    audioFocusManager: AudioFocusManager,
    sleepTimerManager: SleepTimerManager,
    private val mediaItemManager: MediaItemManager,
    private val playbackStateManager: PlaybackStateManager,
) : PlayerManager, Player.Listener {

    private var currentAudiobook: Audiobook? = null
    private var chapters: List<Chapter> = emptyList()
    private var playWhenReady = false

    private val _playbackState = MutableStateFlow(PlaybackState())
    private val exoPlayerHandler: ExoPlayerHandler = ExoPlayerHandler(context, debugLogger, mediaItemManager, this)
    private val chapterHandler: ChapterHandler = ChapterHandler(mediaItemManager)
    private val audioFocusHandler: AudioFocusHandler = AudioFocusHandler(audioFocusManager, this)

    private val sleepTimerDelegate = SleepTimerDelegate(
        sleepTimerManager = sleepTimerManager,
        onTimerFinished = { exoPlayerHandler.pause() },
        onStateChanged = { updatePlaybackState() },
    )

    override fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer? = exoPlayerHandler.exoPlayer

    override fun initializePlayer(audiobook: Audiobook) {
        debugLogger.logInfo("PlayerManagerImpl.initializePlayer: ${audiobook.title}")

        try {
            // Store current audiobook
            currentAudiobook = audiobook

            // Create chapters
            chapters = chapterHandler.createChapters(audiobook)

            // Initialize player with chapters
            exoPlayerHandler.initializePlayer(chapters)

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
        if (audioFocusHandler.requestAudioFocus()) {
            exoPlayerHandler.play()
        }
    }

    override fun pause() {
        debugLogger.logDebug("PlayerManagerImpl.pause called")
        playWhenReady = false
        exoPlayerHandler.pause()
    }

    override fun stop() {
        debugLogger.logDebug("PlayerManagerImpl.stop called")
        exoPlayerHandler.stop()
        sleepTimerDelegate.cancelSleepTimer()
        updatePlaybackState()
    }

    override fun seekTo(position: Long) {
        debugLogger.logDebug("PlayerManagerImpl.seekTo: $position")
        exoPlayerHandler.seekTo(position)
    }

    override fun seekToChapter(chapterIndex: Int) {
        debugLogger.logDebug("PlayerManagerImpl.seekToChapter: $chapterIndex")
        exoPlayerHandler.seekToChapter(chapterIndex, chapters)
    }

    override fun nextChapter() {
        debugLogger.logDebug("PlayerManagerImpl.nextChapter called")
        exoPlayerHandler.nextChapter()
    }

    override fun previousChapter() {
        debugLogger.logDebug("PlayerManagerImpl.previousChapter called")
        exoPlayerHandler.previousChapter()
    }

    override fun setPlaybackSpeed(speed: Float) {
        debugLogger.logDebug("PlayerManagerImpl.setPlaybackSpeed: $speed")
        exoPlayerHandler.setPlaybackSpeed(speed)
        updatePlaybackState()
    }

    override fun setSleepTimer(minutes: Int) = sleepTimerDelegate.setSleepTimer(minutes)
    override fun cancelSleepTimer() = sleepTimerDelegate.cancelSleepTimer()
    override fun getSleepTimerRemaining(): Long = sleepTimerDelegate.getSleepTimerRemaining()

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
        return exoPlayerHandler.getCurrentPosition()
    }

    override fun getDuration(): Long {
        return exoPlayerHandler.getDuration()
    }

    override fun getCurrentChapter(): Chapter? {
        val currentIndex = exoPlayerHandler.getCurrentChapterIndex() ?: return null
        return chapters.getOrNull(currentIndex)
    }

    override fun getPlaybackSpeed(): Float {
        return exoPlayerHandler.getPlaybackSpeed()
    }

    override fun release() {
        debugLogger.logDebug("PlayerManagerImpl.release called")

        sleepTimerDelegate.cancelSleepTimer()
        audioFocusHandler.abandonAudioFocus()
        exoPlayerHandler.release()
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

    /** Update internal playback state */
    private fun updatePlaybackState(error: String? = null) {
        val state = playbackStateManager.createPlaybackState(
            player = exoPlayerHandler.exoPlayer,
            sleepTimerRemaining = getSleepTimerRemaining(),
            error = error,
        )
        _playbackState.value = state
    }

    // AudioManager.OnAudioFocusChangeListener implementation
    override fun onAudioFocusChange(focusChange: Int) {
        debugLogger.logDebug("Audio focus change: $focusChange")

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback if we were playing before
                if (playWhenReady && exoPlayerHandler.exoPlayer?.isPlaying == false) {
                    exoPlayerHandler.play()
                }
                exoPlayerHandler.exoPlayer?.volume = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Pause playback and abandon focus
                playWhenReady = false
                exoPlayerHandler.pause()
                audioFocusHandler.abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback but keep focus
                if (exoPlayerHandler.exoPlayer?.isPlaying == true) {
                    exoPlayerHandler.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower the volume
                exoPlayerHandler.exoPlayer?.volume = 0.3f
            }
        }
        updatePlaybackState()
    }
}
