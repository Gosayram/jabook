// Copyright 2026 Jabook Contributors
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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.LinearInterpolator
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource

/**
 * Manages crossfade transitions between two ExoPlayer instances.
 *
 * Inspired by RetroMusicPlayer's CrossFadePlayer.
 *
 * How it works:
 * - Maintains two ExoPlayer instances (Player A and Player B).
 * - Switches between them when transitioning to a new track.
 * - Overlaps playback by 'crossFadeDurationMs'.
 * - Fades out the current player and fades in the next player.
 *
 * Note: This requires manual playlist management (queueing one track at a time)
 * rather than ExoPlayer's ConcatenatingMediaSource.
 */
public class CrossFadePlayer(
    private val context: Context,
    private val playerFactory: (Context) -> ExoPlayer,
) {
    private var playerA: ExoPlayer = playerFactory(context)
    private var playerB: ExoPlayer = playerFactory(context)

    private var currentPlayer: ExoPlayer = playerA
    private var nextPlayer: ExoPlayer = playerB

    public var crossFadeDurationMs: Long = 0L
    private var currentAnimator: ValueAnimator? = null
    private var isCrossFading = false

    public var onPlayerChanged: ((ExoPlayer) -> Unit)? = null

    /**
     * Prepares the next player with the given media item.
     */
    public fun setNextTrack(mediaItem: androidx.media3.common.MediaItem) {
        nextPlayer.setMediaItem(mediaItem)
        nextPlayer.prepare()
        android.util.Log.d("CrossFadePlayer", "Next track prepared on $nextPlayer")
    }

    /**
     * Prepares the next player with the given media source.
     */
    public fun setNextMediaSource(mediaSource: MediaSource) {
        nextPlayer.setMediaSource(mediaSource)
        nextPlayer.prepare()
        android.util.Log.d("CrossFadePlayer", "Next media source prepared on $nextPlayer")
    }

    /**
     * Starts playback on the current player.
     */
    public fun play() {
        if (!isCrossFading) {
            currentPlayer.play()
        }
    }

    /**
     * Pauses playback on all players.
     */
    public fun pause() {
        currentPlayer.pause()
        nextPlayer.pause()
        currentAnimator?.pause()
    }

    /**
     * Stops playback and releases resources.
     */
    public fun release() {
        playerA.release()
        playerB.release()
        currentAnimator?.cancel()
    }

    /**
     * Starts the crossfade transition.
     *
     * @param onComplete Callback when crossfade is finished.
     */
    public fun startCrossFade(onComplete: () -> Unit = {}) {
        if (isCrossFading) return
        isCrossFading = true

        val fadingOutPlayer = currentPlayer
        val fadingInPlayer = nextPlayer

        // Ensure starting volumes
        fadingOutPlayer.volume = 1f
        fadingInPlayer.volume = 0f

        // Start the next player
        fadingInPlayer.play()

        // Notify listener that active player (logically) might need considering,
        // but typically we switch the "Active" pointer after fade.
        // However, for UI, we might want to show next track metadata immediately?
        // Usually, we switch metadata halfway or at start.
        // For now, we switch "Active Player" reference at END of fade.

        android.util.Log.d("CrossFadePlayer", "Starting crossfade: Out=$fadingOutPlayer, In=$fadingInPlayer")

        currentAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = crossFadeDurationMs.toLong()
                interpolator = LinearInterpolator()

                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    try {
                        fadingOutPlayer.volume = 1f - progress
                        fadingInPlayer.volume = progress
                    } catch (e: Exception) {
                        // Handle potential player release during animation
                    }
                }

                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            isCrossFading = false
                            fadingOutPlayer.pause()
                            fadingOutPlayer.volume = 1f
                            fadingOutPlayer.seekTo(0) // Reset position
                            fadingOutPlayer.clearMediaItems() // Clear for reuse

                            // Swap players
                            swapPlayers()
                            onComplete()
                            android.util.Log.d("CrossFadePlayer", "Crossfade complete. Current is now $currentPlayer")
                        }
                    },
                )
                start()
            }
    }

    private fun swapPlayers() {
        val temp = currentPlayer
        currentPlayer = nextPlayer
        nextPlayer = temp
        onPlayerChanged?.invoke(currentPlayer)
    }

    /**
     * Returns the currently active player (for UI/State queries).
     */
    public fun getActivePlayer(): ExoPlayer = currentPlayer

    public fun getNextPlayer(): ExoPlayer = nextPlayer
}
