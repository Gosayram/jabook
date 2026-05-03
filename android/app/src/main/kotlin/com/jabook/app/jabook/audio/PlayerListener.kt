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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CompletableDeferred

/**
 * Slim coordinator for ExoPlayer event listening.
 *
 * Delegates to specialized sub-handlers:
 * - [PlaybackEventProcessor] — consolidated `onEvents()` processing
 * - [PlayerMetadataHandler] — ReplayGain + embedded artwork
 * - [PositionDiscontinuityHandler] — track changes, seeks, wraparound detection
 * - [TrackTransitionCoordinator] — deferred track-switch management
 *
 * Inspired by lissen-android for better error recovery.
 * Uses `onEvents()` for efficient consolidated event handling (Media3 1.10+ contract).
 *
 * TASK-VERM-03: Reduced from 934 → ~200 lines by extracting sub-handlers.
 */
internal class PlayerListener(
    context: Context,
    getActivePlayer: () -> ExoPlayer,
    getIsBookCompleted: () -> Boolean,
    setIsBookCompleted: (Boolean) -> Unit,
    getSleepTimerEndOfChapter: () -> Boolean,
    getSleepTimerEndOfTrack: () -> Boolean,
    cancelSleepTimer: () -> Unit,
    sendTimerExpiredEvent: () -> Unit,
    markSleepTimerPause: () -> Unit = {},
    saveCurrentPosition: () -> Unit,
    getEmbeddedArtworkPath: () -> String?,
    setEmbeddedArtworkPath: (String?) -> Unit,
    getCurrentMetadata: () -> Map<String, String>?,
    setLastCompletedTrackIndex: ((Int) -> Unit)? = null,
    getLastCompletedTrackIndex: (() -> Int)? = null,
    getActualPlaylistSize: (() -> Int)? = null,
    updateActualTrackIndex: ((Int) -> Unit)? = null,
    isPlaylistLoading: (() -> Boolean)? = null,
    updateLastPlayedTimestamp: ((String) -> Unit)? = null,
    markBookCompleted: ((String) -> Unit)? = null,
    getCurrentBookId: (() -> String?)? = null,
    preloadNextTrack: ((Int) -> Unit)? = null,
    optimizeMemoryUsage: ((Int) -> Unit)? = null,
    updateAudioVisualizer: ((Int) -> Unit)? = null,
    getCrossfadeHandler: (() -> CrossfadeHandler?)? = null,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
) : Player.Listener {
    // --- Captured callbacks for direct use in listener overrides ---
    private val capturedActivePlayer: () -> ExoPlayer = getActivePlayer
    private val capturedSavePosition: () -> Unit = saveCurrentPosition
    private val capturedSleepTimerEndOfChapter: () -> Boolean = getSleepTimerEndOfChapter
    private val capturedSleepTimerEndOfTrack: () -> Boolean = getSleepTimerEndOfTrack
    private val capturedCancelSleepTimer: () -> Unit = cancelSleepTimer
    private val capturedSendTimerExpired: () -> Unit = sendTimerExpiredEvent
    private val capturedMarkSleepTimerPause: () -> Unit = markSleepTimerPause
    private val capturedUpdateAudioVisualizer: ((Int) -> Unit)? = updateAudioVisualizer

    // --- Sub-handlers (extracted from PlayerListener, TASK-VERM-03) ---

    private val bookCompletionTracker: BookCompletionTracker =
        BookCompletionTracker(
            context = context,
            scope = coroutineScope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            getActivePlayer = { getActivePlayer() },
            getIsBookCompleted = { getIsBookCompleted() },
            setIsBookCompleted = { setIsBookCompleted(it) },
            getActualPlaylistSize = { getActualPlaylistSize?.invoke() ?: getActivePlayer().mediaItemCount },
            getLastCompletedTrackIndex = { getLastCompletedTrackIndex?.invoke() ?: -1 },
            setLastCompletedTrackIndex = { setLastCompletedTrackIndex?.invoke(it) },
            saveCurrentPosition = { saveCurrentPosition() },
            getCurrentBookId = { getCurrentBookId?.invoke() },
            markBookCompleted = markBookCompleted,
        )

    private val playerErrorHandler: PlayerErrorHandler =
        PlayerErrorHandler(
            getActivePlayer = { getActivePlayer() },
            getActualPlaylistSize = { getActualPlaylistSize?.invoke() ?: getActivePlayer().mediaItemCount },
            getCurrentMetadata = { getCurrentMetadata() },
            getCurrentBookId = { getCurrentBookId?.invoke() },
        )

    private val metadataHandler: PlayerMetadataHandler =
        PlayerMetadataHandler(
            context = context,
            setEmbeddedArtworkPath = setEmbeddedArtworkPath,
        )

    private val trackTransitionCoordinator: TrackTransitionCoordinator =
        TrackTransitionCoordinator(
            isPlaylistLoading = isPlaylistLoading,
            updateActualTrackIndex = updateActualTrackIndex,
        )

    private val positionDiscontinuityHandler: PositionDiscontinuityHandler =
        PositionDiscontinuityHandler(
            getActivePlayer = getActivePlayer,
            getIsBookCompleted = getIsBookCompleted,
            setIsBookCompleted = setIsBookCompleted,
            setLastCompletedTrackIndex = setLastCompletedTrackIndex,
            getActualPlaylistSize = getActualPlaylistSize,
            saveCurrentPosition = saveCurrentPosition,
            bookCompletionTracker = bookCompletionTracker,
            playerErrorHandler = playerErrorHandler,
        )

    private val playbackEventProcessor: PlaybackEventProcessor =
        PlaybackEventProcessor(
            context = context,
            getIsBookCompleted = getIsBookCompleted,
            setIsBookCompleted = setIsBookCompleted,
            setLastCompletedTrackIndex = setLastCompletedTrackIndex,
            getSleepTimerEndOfChapter = getSleepTimerEndOfChapter,
            getSleepTimerEndOfTrack = getSleepTimerEndOfTrack,
            cancelSleepTimer = cancelSleepTimer,
            sendTimerExpiredEvent = sendTimerExpiredEvent,
            markSleepTimerPause = markSleepTimerPause,
            saveCurrentPosition = saveCurrentPosition,
            getActualPlaylistSize = getActualPlaylistSize,
            updateLastPlayedTimestamp = updateLastPlayedTimestamp,
            getCurrentBookId = getCurrentBookId,
            preloadNextTrack = preloadNextTrack,
            optimizeMemoryUsage = optimizeMemoryUsage,
            getCrossfadeHandler = getCrossfadeHandler,
            playerErrorHandler = playerErrorHandler,
            bookCompletionTracker = bookCompletionTracker,
        )

    private val audioFocusDuckingController =
        AudioFocusDuckingController(
            getActivePlayer = getActivePlayer,
            scope = coroutineScope,
            onDuckApplied = {
                saveCurrentPosition()
                LogUtils.d("AudioPlayerService", "Saved position on transient audio focus duck event")
            },
        )

    /** Backward-compatible accessor for LoudnessNormalizer injection from PlayerConfigurator. */
    var loudnessNormalizer: com.jabook.app.jabook.audio.processors.LoudnessNormalizer?
        get() = metadataHandler.loudnessNormalizer
        set(value) {
            metadataHandler.loudnessNormalizer = value
        }

    // --- Public API for PlaylistManager integration ---

    /** Sets a deferred to be completed when track switch occurs. */
    public fun setPendingTrackSwitchDeferred(deferred: CompletableDeferred<Int>) {
        trackTransitionCoordinator.setPendingTrackSwitchDeferred(deferred)
    }

    /** Clears the pending deferred (cancels waiting for track switch). */
    public fun clearPendingTrackSwitchDeferred() {
        trackTransitionCoordinator.clearPendingTrackSwitchDeferred()
    }

    // --- Player.Listener overrides (delegating to sub-handlers) ---

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        playbackEventProcessor.onEvents(player, events)
    }

    override fun onMetadata(metadata: androidx.media3.common.Metadata) {
        metadataHandler.onMetadata(metadata)
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        val reasonText =
            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                else -> "UNKNOWN($reason)"
            }
        LogUtils.i("AudioPlayerService", "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reasonText")

        if (!playWhenReady) {
            LogUtils.d("AudioPlayerService", "Playback paused (reason=$reasonText), saving position")
            capturedSavePosition()
        }
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        audioFocusDuckingController.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        playerErrorHandler.logErrorContext(error)
        playerErrorHandler.handlePlayerError(error)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val currentIndex = capturedActivePlayer().currentMediaItemIndex
        val reasonName =
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                else -> "UNKNOWN($reason)"
            }
        LogUtils.i(
            "AudioPlayerService",
            "🎵 Track switch: index=$currentIndex, reason=$reasonName, mediaId=${mediaItem?.mediaId ?: "unknown"}",
        )

        trackTransitionCoordinator.handleTrackTransitionEvent(currentIndex, "onMediaItemTransition")

        // Sleep timer "end of chapter" on auto transition
        if ((capturedSleepTimerEndOfChapter() || capturedSleepTimerEndOfTrack()) &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        ) {
            LogUtils.d("AudioPlayerService", "Sleep timer expired (end of chapter on auto transition)")
            capturedMarkSleepTimerPause()
            capturedSavePosition()
            val player = capturedActivePlayer()
            player.playWhenReady = false
            capturedCancelSleepTimer()
            capturedSendTimerExpired()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        positionDiscontinuityHandler.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        metadataHandler.onMediaMetadataChanged(mediaMetadata)
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        LogUtils.d("AudioPlayerService", "Audio session ID changed: $audioSessionId")
        if (audioSessionId != 0) {
            capturedUpdateAudioVisualizer?.invoke(audioSessionId)
        }
    }

    fun release() {
        bookCompletionTracker.stopPositionCheck()
        audioFocusDuckingController.release()
    }
}
