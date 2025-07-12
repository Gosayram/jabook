package com.jabook.app.features.player

import android.content.Context
import android.net.Uri
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

/** ExoPlayer implementation of PlayerManager interface Handles audiobook playback with Media3 ExoPlayer */
@UnstableApi
@Singleton
class PlayerManagerImpl @Inject constructor(@ApplicationContext private val context: Context, private val debugLogger: IDebugLogger) :
    PlayerManager, Player.Listener {

    private var exoPlayer: ExoPlayer? = null
    private var currentAudiobook: Audiobook? = null
    private var chapters: List<Chapter> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerEndTime: Long = 0

    private val _playbackState = MutableStateFlow(PlaybackState())

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
            // TODO: Load actual chapters from repository
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
        exoPlayer?.play()
    }

    override fun pause() {
        debugLogger.logDebug("PlayerManagerImpl.pause called")
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
        return _playbackState.asStateFlow()
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
