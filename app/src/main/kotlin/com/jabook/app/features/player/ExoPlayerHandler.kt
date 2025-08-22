package com.jabook.app.features.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.core.domain.model.Chapter
import com.jabook.app.shared.debug.IDebugLogger

class ExoPlayerHandler(
    private val context: Context,
    private val debugLogger: IDebugLogger,
    private val mediaItemManager: MediaItemManager,
    private val listener: Player.Listener,
) {
    var exoPlayer: ExoPlayer? = null
        private set

    fun initializePlayer(chapters: List<Chapter>) {
        try {
            // Release existing player if any
            release()

            // Create new ExoPlayer instance
            exoPlayer =
                ExoPlayer
                    .Builder(context)
                    .setSeekBackIncrementMs(15000) // 15 seconds
                    .setSeekForwardIncrementMs(30000) // 30 seconds
                    .build()

            exoPlayer?.addListener(listener)

            if (chapters.isNotEmpty()) {
                // Create media items from chapters
                val mediaItems = mediaItemManager.createMediaItems(chapters)

                // Set media items directly
                exoPlayer?.setMediaItems(mediaItems)
                exoPlayer?.prepare()
            }
        } catch (e: Exception) {
            debugLogger.logError("ExoPlayerHandler.initializePlayer failed", e)
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun seekToChapter(
        chapterIndex: Int,
        chapters: List<Chapter>,
    ) {
        if (chapterIndex >= 0 && chapterIndex < chapters.size) {
            exoPlayer?.seekTo(chapterIndex, 0)
        }
    }

    fun nextChapter() {
        exoPlayer?.seekToNextMediaItem()
    }

    fun previousChapter() {
        exoPlayer?.seekToPreviousMediaItem()
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun release() {
        exoPlayer?.removeListener(listener)
        exoPlayer?.release()
        exoPlayer = null
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0

    fun getDuration(): Long = exoPlayer?.duration ?: 0

    fun getCurrentChapterIndex(): Int? = exoPlayer?.currentMediaItemIndex

    fun getPlaybackSpeed(): Float = exoPlayer?.playbackParameters?.speed ?: 1.0f
}
