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

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private val playerFactory: (Context, handleAudioFocus: Boolean) -> ExoPlayer,
    private val coroutineScope: CoroutineScope,
) {
    private sealed interface PendingPreloadRequest {
        data class MediaItemRequest(
            val mediaItem: androidx.media3.common.MediaItem,
        ) : PendingPreloadRequest

        data class MediaSourceRequest(
            val mediaSource: MediaSource,
        ) : PendingPreloadRequest
    }

    private var playerA: ExoPlayer = playerFactory(context, true)
    private var playerB: ExoPlayer = playerFactory(context, false)

    private var currentPlayer: ExoPlayer = playerA
    private var nextPlayer: ExoPlayer = playerB

    public var crossFadeDurationMs: Long = 0L
    private var crossfadeJob: Job? = null
    private var isCrossFading = false
    private var crossFadeOutPlayer: ExoPlayer? = null
    private var pendingPreloadRequest: PendingPreloadRequest? = null

    public var onPlayerChanged: ((ExoPlayer) -> Unit)? = null

    /**
     * Prepares the next player with the given media item.
     */
    public fun setNextTrack(mediaItem: androidx.media3.common.MediaItem) {
        enqueueOrApplyPreloadRequest(PendingPreloadRequest.MediaItemRequest(mediaItem))
    }

    /**
     * Prepares the next player with the given media source.
     */
    public fun setNextMediaSource(mediaSource: MediaSource) {
        enqueueOrApplyPreloadRequest(PendingPreloadRequest.MediaSourceRequest(mediaSource))
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
        crossfadeJob?.cancel()
    }

    /**
     * Stops playback and releases resources.
     */
    public fun release() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossFading = false
        crossFadeOutPlayer = null
        pendingPreloadRequest = null
        playerA.release()
        playerB.release()
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
        crossFadeOutPlayer = fadingOutPlayer

        // Ensure starting volumes
        fadingOutPlayer.volume = 1f
        fadingInPlayer.volume = 0f

        // Start the next player
        fadingInPlayer.play()

        LogUtils.d("CrossFadePlayer", "Starting crossfade: Out=$fadingOutPlayer, In=$fadingInPlayer")

        val durationMs = crossFadeDurationMs
        crossfadeJob =
            coroutineScope.launch {
                val steps = 50
                val stepDelay = durationMs / steps
                for (i in 1..steps) {
                    if (!isActive) return@launch
                    val progress = i.toFloat() / steps
                    fadingOutPlayer.volume = 1f - progress
                    fadingInPlayer.volume = progress
                    delay(stepDelay)
                }

                // Ensure final state
                if (isActive) {
                    isCrossFading = false
                    fadingOutPlayer.pause()
                    fadingOutPlayer.volume = 1f
                    fadingOutPlayer.seekTo(0) // Reset position
                    fadingOutPlayer.clearMediaItems() // Clear for reuse

                    // Swap players
                    swapPlayers()
                    applyPendingPreloadIfNeeded()
                    crossFadeOutPlayer = null
                    crossfadeJob = null
                    onComplete()
                    LogUtils.d("CrossFadePlayer", "Crossfade complete. Current is now $currentPlayer")
                }
            }
    }

    private fun resolvePreloadTargetPlayer(): ExoPlayer {
        // During crossfade, `nextPlayer` is currently fading in and about to become active.
        // Queue updates should preload into the outgoing player, which becomes standby after swap.
        return if (isCrossFading) {
            crossFadeOutPlayer ?: nextPlayer
        } else {
            nextPlayer
        }
    }

    private fun enqueueOrApplyPreloadRequest(request: PendingPreloadRequest) {
        if (isCrossFading) {
            // Keep only the latest preload request while transition is active.
            pendingPreloadRequest = request
            LogUtils.d("CrossFadePlayer", "Queued preload during active crossfade")
            return
        }
        val targetPlayer = resolvePreloadTargetPlayer()
        applyPreloadRequest(targetPlayer, request)
    }

    private fun applyPendingPreloadIfNeeded() {
        val pendingRequest = pendingPreloadRequest ?: return
        pendingPreloadRequest = null
        applyPreloadRequest(nextPlayer, pendingRequest)
    }

    private fun applyPreloadRequest(
        targetPlayer: ExoPlayer,
        request: PendingPreloadRequest,
    ) {
        targetPlayer.clearMediaItems()
        when (request) {
            is PendingPreloadRequest.MediaItemRequest -> targetPlayer.setMediaItem(request.mediaItem)
            is PendingPreloadRequest.MediaSourceRequest -> targetPlayer.setMediaSource(request.mediaSource)
        }
        targetPlayer.prepare()
        LogUtils.d("CrossFadePlayer", "Preload request applied on $targetPlayer")
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
