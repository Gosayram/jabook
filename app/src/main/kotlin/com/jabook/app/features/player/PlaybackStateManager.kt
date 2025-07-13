package com.jabook.app.features.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateManager @Inject constructor() {

    fun createPlaybackState(
        player: ExoPlayer?,
        sleepTimerRemaining: Long,
        error: String? = null,
    ): PlaybackState {
        return if (player != null) {
            PlaybackState(
                isPlaying = player.isPlaying,
                isPaused = !player.isPlaying && player.playbackState == Player.STATE_READY,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                isCompleted = player.playbackState == Player.STATE_ENDED,
                currentPosition = player.currentPosition,
                duration = player.duration,
                currentChapterIndex = player.currentMediaItemIndex,
                playbackSpeed = player.playbackParameters.speed,
                sleepTimerRemaining = sleepTimerRemaining,
                error = error,
            )
        } else {
            PlaybackState(error = error)
        }
    }
}
