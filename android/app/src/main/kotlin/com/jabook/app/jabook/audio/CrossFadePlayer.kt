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
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
@OptIn(UnstableApi::class)
public class CrossFadePlayer(
    private val context: Context,
    private val playerFactory: (Context, handleAudioFocus: Boolean) -> ExoPlayer,
    private val coroutineScope: CoroutineScope,
    private val volumeWriteCoordinator: VolumeWriteCoordinator,
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

    /**
     * Focus handling is Media3-internal (only the player created with
     * `handleAudioFocus = true` receives focus events), so during a crossfade a
     * transient or permanent focus loss pauses the fading-out player only, leaving
     * the fading-in player audible. This listener routes focus-driven pauses on the
     * focus-owning player to [pause], which pauses both players and cancels the
     * transition. Focus re-gain resumes the surviving player natively through the
     * same Media3 path.
     */
    private val focusLossListener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                if (isCrossFading && !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    LogUtils.d("CrossFadePlayer", "Focus loss during crossfade — pausing both players")
                    pause()
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (isCrossFading && playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                    LogUtils.d("CrossFadePlayer", "Playback suppressed during crossfade — pausing both players")
                    pause()
                }
            }
        }

    init {
        playerA.addListener(focusLossListener)
        playerB.addListener(focusLossListener)
    }

    public var crossFadeDurationMs: Long = 0L
    private var crossfadeJob: Job? = null
    private var transitionGeneration: Long = 0L
    private var isCrossFading = false
    private var crossFadeOutPlayer: ExoPlayer? = null
    private var pendingPreloadRequest: PendingPreloadRequest? = null
    private var transitionOnComplete: (() -> Unit)? = null

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
     * Prepares the next player with the complete queue at [startIndex].
     *
     * Keeping the full queue here makes the incoming player's Media3 index remain the absolute
     * chapter index after the player swap.
     */
    public fun setNextMediaSources(
        mediaSources: List<MediaSource>,
        startIndex: Int,
    ) {
        if (isCrossFading) return
        nextPlayer.setMediaSources(mediaSources, startIndex, 0L)
        nextPlayer.prepare()
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
     *
     * Cancels an in-flight transition without swapping: the player holding the audible
     * content stays current (paused at its position, volume restored), the fading-in
     * player is emptied. After this call exactly one player holds content.
     */
    public fun pause() {
        transitionGeneration += 1L
        val wasFading = isCrossFading
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossFading = false
        crossFadeOutPlayer = null
        transitionOnComplete = null
        pendingPreloadRequest = null
        currentPlayer.pause()
        nextPlayer.pause()
        currentPlayer.volume = 1f
        nextPlayer.volume = 1f
        if (wasFading) {
            nextPlayer.clearMediaItems()
        }
    }

    /**
     * Stops playback and releases resources.
     */
    public fun release() {
        transitionGeneration += 1L
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossFading = false
        crossFadeOutPlayer = null
        transitionOnComplete = null
        pendingPreloadRequest = null
        playerA.release()
        playerB.release()
    }

    /**
     * Rebuilds both players with a new renderer configuration while keeping the active playback.
     */
    public fun recreatePlayers(
        factory: (Context, handleAudioFocus: Boolean) -> ExoPlayer,
        sourcePlayer: Player = currentPlayer,
    ) {
        val activeState = PlayerStateTransfer.savePlayerState(sourcePlayer)
        val oldPlayerA = playerA
        val oldPlayerB = playerB

        sourcePlayer.pause()

        val savedPendingPreload = pendingPreloadRequest
        transitionGeneration += 1L
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossFading = false
        crossFadeOutPlayer = null
        transitionOnComplete = null
        pendingPreloadRequest = null

        playerA = factory(context, true)
        playerB = factory(context, false)
        playerA.addListener(focusLossListener)
        playerB.addListener(focusLossListener)
        currentPlayer = playerA
        nextPlayer = playerB

        restoreActiveState(activeState)
        if (savedPendingPreload != null) {
            applyPreloadRequest(nextPlayer, savedPendingPreload)
        }
        oldPlayerA.release()
        oldPlayerB.release()
        onPlayerChanged?.invoke(currentPlayer)
    }

/**
     * Starts the crossfade transition.
     *
     * @param onComplete Callback when crossfade is finished.
     */
    public fun startCrossFade(onComplete: () -> Unit = {}) {
        if (isCrossFading) return
        val generation = ++transitionGeneration
        isCrossFading = true
        transitionOnComplete = onComplete

        val fadingOutPlayer = currentPlayer
        val fadingInPlayer = nextPlayer
        crossFadeOutPlayer = fadingOutPlayer

        // Ensure starting volumes
        fadingOutPlayer.volume = 1f
        fadingInPlayer.volume = 0f
        fadingInPlayer.setPlaybackSpeed(fadingOutPlayer.playbackParameters.speed)
        fadingInPlayer.repeatMode = fadingOutPlayer.repeatMode
        fadingInPlayer.shuffleModeEnabled = fadingOutPlayer.shuffleModeEnabled

        // Start the next player
        fadingInPlayer.play()

        LogUtils.d("CrossFadePlayer", "Starting crossfade: Out=$fadingOutPlayer, In=$fadingInPlayer")

        val durationMs = crossFadeDurationMs
        if (durationMs <= 0L) {
            // Zero-duration transition: swap synchronously, no fade loop.
            finalizeTransitionNow()
            return
        }

        // Own the outgoing player's volume for the whole transition. Revoke =
        // finalize (NOT job.cancel): cancelling would freeze mid-fade; finalize
        // jumps to the end state cleanly before another owner takes over. The
        // generation guard drops stale revokes: a superseded transition's claim
        // (released late in its finally) must not finalize a newer transition.
        volumeWriteCoordinator.tryAcquire(fadingOutPlayer, VolumeOwner.CROSSFADE) {
            if (generation == transitionGeneration) finalizeTransitionNow()
        }
        crossfadeJob =
            coroutineScope.launch {
                try {
                    // Equal-power curve (cos/sin) avoids the loudness dip of a linear
                    // equal-gain fade; ~20ms per step keeps steps smooth yet cheap.
                    val steps = (durationMs / 20L).coerceIn(16L, 200L).toInt()
                    val stepDelay = durationMs / steps
                    for (i in 1..steps) {
                        if (!isActive) return@launch
                        val angle = i.toFloat() / steps * (PI / 2.0)
                        fadingOutPlayer.volume = cos(angle).toFloat().coerceIn(0f, 1f)
                        fadingInPlayer.volume = sin(angle).toFloat().coerceIn(0f, 1f)
                        delay(stepDelay)
                    }

                    // Ensure final state
                    if (isActive) {
                        completeTransition(fadingOutPlayer)
                        invokeTransitionOnComplete()
                        LogUtils.d("CrossFadePlayer", "Crossfade complete. Current is now $currentPlayer")
                    }
                } finally {
                    if (generation == transitionGeneration) {
                        isCrossFading = false
                        crossFadeOutPlayer = null
                        crossfadeJob = null
                        transitionOnComplete = null
                        // Only the current transition may drop the claim; a stale
                        // finally must not release a newer owner's claim.
                        volumeWriteCoordinator.release(fadingOutPlayer, VolumeOwner.CROSSFADE)
                    }
                }
            }
    }

    /**
     * Completes an in-flight transition synchronously: the fading-in player becomes
     * current immediately and the outgoing player is cleaned up. Idempotent no-op
     * when no transition is running.
     */
    public fun finalizeTransitionNow() {
        if (!isCrossFading) return
        transitionGeneration += 1L
        crossfadeJob?.cancel()
        crossfadeJob = null
        val outPlayer = crossFadeOutPlayer
        crossFadeOutPlayer = null
        isCrossFading = false
        if (outPlayer == null) {
            transitionOnComplete = null
            return
        }
        completeTransition(outPlayer)
        invokeTransitionOnComplete()
        LogUtils.d("CrossFadePlayer", "Transition finalized immediately. Current is now $currentPlayer")
    }

    private fun completeTransition(fadingOutPlayer: ExoPlayer) {
        val incoming = nextPlayer
        incoming.volume = 1f

        // Swap and rebind active-player listeners FIRST: clearing the outgoing
        // timeline emits transition events that must not reach the still-bound listener.
        swapPlayers()

        // Only the player that survives the transition may own audio focus.
        fadingOutPlayer.setAudioAttributes(fadingOutPlayer.audioAttributes, false)
        incoming.setAudioAttributes(incoming.audioAttributes, true)

        fadingOutPlayer.pause()
        fadingOutPlayer.volume = 1f
        fadingOutPlayer.seekTo(0)
        fadingOutPlayer.clearMediaItems()
        applyPendingPreloadIfNeeded()
    }

    private fun invokeTransitionOnComplete() {
        val onComplete = transitionOnComplete
        transitionOnComplete = null
        onComplete?.invoke()
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

    private fun restoreActiveState(state: PlayerStateTransfer.SavedPlayerState) {
        if (state.mediaItems.isNotEmpty()) {
            PlayerStateTransfer.restorePlayerState(currentPlayer, state)
            return
        }

        currentPlayer.shuffleModeEnabled = state.shuffleModeEnabled
        currentPlayer.repeatMode = state.repeatMode
        currentPlayer.setPlaybackSpeed(state.playbackSpeed)
        currentPlayer.playWhenReady = state.playWhenReady
    }

    /**
     * Returns the currently active player (for UI/State queries).
     */
    public fun getActivePlayer(): ExoPlayer = currentPlayer

    public fun getNextPlayer(): ExoPlayer = nextPlayer

    /** Returns whether a transition is currently using both players. */
    public fun isTransitionRunning(): Boolean = isCrossFading
}
