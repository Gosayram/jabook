package com.jabook.app.features.player

import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

/** Player manager interface for audiobook playback Based on IDEA.md architecture specification */
interface PlayerManager {
  fun initializePlayer(audiobook: Audiobook)

  fun play()

  fun pause()

  fun stop()

  fun seekTo(position: Long)

  fun seekToChapter(chapterIndex: Int)

  fun nextChapter()

  fun previousChapter()

  fun setPlaybackSpeed(speed: Float)

  fun setSleepTimer(minutes: Int)

  fun cancelSleepTimer()

  fun getPlaybackState(): Flow<PlaybackState>

  fun getCurrentPosition(): Long

  fun getDuration(): Long

  fun getCurrentChapter(): Chapter?

  fun getPlaybackSpeed(): Float

  fun getSleepTimerRemaining(): Long

  fun release()

  /** Return underlying ExoPlayer instance, or null if not initialized */
  fun getExoPlayer(): ExoPlayer?
}

/** Playback state information */
data class PlaybackState(
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isBuffering: Boolean = false,
  val isCompleted: Boolean = false,
  val currentPosition: Long = 0,
  val duration: Long = 0,
  val currentChapterIndex: Int = 0,
  val playbackSpeed: Float = 1.0f,
  val sleepTimerRemaining: Long = 0,
  val error: String? = null,
)

/** Player configuration */
data class PlayerConfig(
  val enableGaplessPlayback: Boolean = true,
  val enableCrossfade: Boolean = false,
  val crossfadeDuration: Long = 3000, // milliseconds
  val skipSilence: Boolean = false,
  val normalizeLoudness: Boolean = false,
  val preferredAudioFormat: AudioFormat = AudioFormat.AUTO,
  val enableAudioEffects: Boolean = false,
  val enableVisualization: Boolean = false,
)

/** Audio format preferences */
enum class AudioFormat {
  AUTO,
  MP3,
  AAC,
  OGG,
  FLAC,
}

/** Sleep timer configuration */
data class SleepTimerConfig(
  val duration: Long, // milliseconds
  val fadeOut: Boolean = true,
  val fadeOutDuration: Long = 5000, // milliseconds
  val pauseOnSilence: Boolean = true,
  val showNotification: Boolean = true,
)

/** Player events for logging and monitoring */
sealed class PlayerEvent {
  data class PlayerInitialized(
    val audiobook: Audiobook,
  ) : PlayerEvent()

  data class PlaybackStarted(
    val audiobook: Audiobook,
    val chapter: Chapter,
  ) : PlayerEvent()

  data class PlaybackPaused(
    val audiobook: Audiobook,
    val position: Long,
  ) : PlayerEvent()

  data class PlaybackStopped(
    val audiobook: Audiobook,
  ) : PlayerEvent()

  data class PlaybackCompleted(
    val audiobook: Audiobook,
  ) : PlayerEvent()

  data class ChapterChanged(
    val audiobook: Audiobook,
    val chapter: Chapter,
  ) : PlayerEvent()

  data class PlaybackSpeedChanged(
    val audiobook: Audiobook,
    val speed: Float,
  ) : PlayerEvent()

  data class SleepTimerSet(
    val audiobook: Audiobook,
    val minutes: Int,
  ) : PlayerEvent()

  data class SleepTimerTriggered(
    val audiobook: Audiobook,
  ) : PlayerEvent()

  data class PlaybackError(
    val audiobook: Audiobook,
    val error: String,
  ) : PlayerEvent()
}

/** Audio focus management */
enum class AudioFocusState {
  GAINED,
  LOST,
  LOST_TRANSIENT,
  LOST_TRANSIENT_CAN_DUCK,
}

/** Playback notification actions */
enum class NotificationAction {
  PLAY_PAUSE,
  NEXT_CHAPTER,
  PREVIOUS_CHAPTER,
  SEEK_FORWARD,
  SEEK_BACKWARD,
  STOP,
}

/** Player statistics for debugging */
data class PlayerStats(
  val totalPlayTime: Long,
  val totalPauseTime: Long,
  val seekCount: Int,
  val skipCount: Int,
  val errorCount: Int,
  val bufferingTime: Long,
  val averagePlaybackSpeed: Float,
  val audioFormatUsed: String,
  val networkUsage: Long,
)
