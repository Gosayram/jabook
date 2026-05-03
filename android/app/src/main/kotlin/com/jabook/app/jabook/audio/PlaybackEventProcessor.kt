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
import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.widget.PlayerWidgetProvider

/**
 * Processes consolidated ExoPlayer events from `onEvents()` callback.
 *
 * Extracted from PlayerListener as part of TASK-VERM-03 (PlayerListener decomposition).
 * Uses `onEvents()` for efficient consolidated event handling (Media3 1.10+ contract).
 *
 * Responsible for:
 * - Playback state changes (IDLE, BUFFERING, READY, ENDED)
 * - Play-when-ready changes (audio focus, user request)
 * - Is-playing changes (position saving, crossfade control)
 * - Media item transitions (preload, memory optimization)
 * - Playback parameter / repeat / shuffle changes
 */
internal class PlaybackEventProcessor(
    private val context: Context,
    private val getIsBookCompleted: () -> Boolean,
    private val setIsBookCompleted: (Boolean) -> Unit,
    private val setLastCompletedTrackIndex: ((Int) -> Unit)?,
    private val getSleepTimerEndOfChapter: () -> Boolean,
    private val getSleepTimerEndOfTrack: () -> Boolean,
    private val cancelSleepTimer: () -> Unit,
    private val sendTimerExpiredEvent: () -> Unit,
    private val markSleepTimerPause: () -> Unit,
    private val saveCurrentPosition: () -> Unit,
    private val getActualPlaylistSize: (() -> Int)?,
    private val updateLastPlayedTimestamp: ((String) -> Unit)?,
    private val getCurrentBookId: (() -> String?)?,
    private val preloadNextTrack: ((Int) -> Unit)?,
    private val optimizeMemoryUsage: ((Int) -> Unit)?,
    private val getCrossfadeHandler: (() -> CrossfadeHandler?)?,
    private val playerErrorHandler: PlayerErrorHandler,
    private val bookCompletionTracker: BookCompletionTracker,
) {
    /**
     * Processes all ExoPlayer events in a consolidated callback.
     * This is more efficient than handling individual callbacks separately.
     */
    fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        LogUtils.v("AudioPlayerService", "onEvents: $events")

        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            handlePlaybackStateChanged(player)
        }

        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            handlePlayWhenReadyChanged(player)
        }

        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
            handleIsPlayingChanged(player)
        }

        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            handleMediaItemTransition(player)
        }

        if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
            val params = player.playbackParameters
            LogUtils.d("AudioPlayerService", "Playback parameters changed: speed=${params.speed}, pitch=${params.pitch}")
        }

        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
            LogUtils.d("AudioPlayerService", "Repeat mode changed: ${player.repeatMode}")
        }

        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            LogUtils.d("AudioPlayerService", "Shuffle mode changed: ${player.shuffleModeEnabled}")
        }
    }

    private fun handlePlaybackStateChanged(player: Player) {
        val playbackState = player.playbackState
        val stateName = playbackStateName(playbackState)
        LogUtils.i(
            "AudioPlayerService",
            "EVENT_PLAYBACK_STATE_CHANGED: $stateName, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}",
        )

        PlayerWidgetProvider.requestUpdate(context)

        // Reset retry and skip counts on successful playback
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
            playerErrorHandler.resetCounts()
        }

        if (playbackState == Player.STATE_ENDED) {
            bookCompletionTracker.stopPositionCheck()
        }

        if (PositionTrackingResetPolicy.shouldResetOnPlaybackStateChanged(playbackState)) {
            // Reset handled by BookCompletionTracker
        }

        // Handle book completion + sleep timer when last track ends
        if (playbackState == Player.STATE_ENDED) {
            val currentIndex = player.currentMediaItemIndex
            val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

            if (getSleepTimerEndOfChapter() || getSleepTimerEndOfTrack()) {
                LogUtils.d("AudioPlayerService", "Sleep timer expired (end of chapter), pausing playback")
                markSleepTimerPause()
                saveCurrentPosition()
                player.playWhenReady = false
                cancelSleepTimer()
                sendTimerExpiredEvent()
            }

            if (!bookCompletionTracker.handleBookCompletion(player, currentIndex, "STATE_ENDED")) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Track ended, will auto-advance (track $currentIndex of ${totalTracks - 1})",
                )
            }
        }

        // Handle errors in IDLE state
        if (playbackState == Player.STATE_IDLE) {
            val error = player.playerError
            if (error != null) {
                LogUtils.e("AudioPlayerService", "Playback error: ${error.message}", error)
                playerErrorHandler.logErrorContext(error)
                playerErrorHandler.handlePlayerError(error)
            }
        }
    }

    private fun handlePlayWhenReadyChanged(player: Player) {
        // Reset book completion if user manually starts playback after completion
        if (getIsBookCompleted() && player.playWhenReady) {
            LogUtils.i("AudioPlayerService", "User started playback after completion, resetting flag")
            setIsBookCompleted(false)
            setLastCompletedTrackIndex?.invoke(-1)
        }

        LogUtils.i(
            "AudioPlayerService",
            "EVENT_PLAY_WHEN_READY_CHANGED: playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, playbackState=${player.playbackState}",
        )
    }

    private fun handleIsPlayingChanged(player: Player) {
        val isPlaying = player.isPlaying
        val playbackState = player.playbackState
        LogUtils.i(
            "AudioPlayerService",
            "EVENT_IS_PLAYING_CHANGED: isPlaying=$isPlaying, playWhenReady=${player.playWhenReady}, " +
                "playbackState=${playbackStateName(playbackState)}",
        )

        PlayerWidgetProvider.requestUpdate(context)

        // Save position when playback stops (player still ready, not ended)
        if (!isPlaying && !player.playWhenReady && playbackState == Player.STATE_READY) {
            LogUtils.d("AudioPlayerService", "Playback stopped, saving position")
            saveCurrentPosition()
        }

        // Update last played timestamp when playback starts
        if (isPlaying && playbackState == Player.STATE_READY) {
            LogUtils.v("AudioPlayerService", "Playback started, position will be saved periodically")
            getCurrentBookId?.invoke()?.let { bookId ->
                updateLastPlayedTimestamp?.invoke(bookId)
            }
        }

        // Position check for end-of-file detection
        val currentIndex = player.currentMediaItemIndex
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount
        val isLastTrack = currentIndex >= totalTracks - 1

        if (isLastTrack && isPlaying && !getIsBookCompleted()) {
            LogUtils.d(
                "AudioPlayerService",
                "Starting position check on last track (index=$currentIndex/$totalTracks)",
            )
            bookCompletionTracker.startPositionCheck()
        } else if (isPlaying && !getIsBookCompleted() && playbackState == Player.STATE_READY) {
            LogUtils.v(
                "AudioPlayerService",
                "Starting position check (index=$currentIndex/$totalTracks)",
            )
            bookCompletionTracker.startPositionCheck()
        } else {
            if (!isLastTrack) {
                bookCompletionTracker.stopPositionCheck()
            }
        }

        // Crossfade monitoring
        if (isPlaying && playbackState == Player.STATE_READY) {
            getCrossfadeHandler?.invoke()?.startMonitoring()
        } else {
            getCrossfadeHandler?.invoke()?.stopMonitoring()
        }
    }

    private fun handleMediaItemTransition(player: Player) {
        val currentIndex = player.currentMediaItemIndex
        LogUtils.v(
            "AudioPlayerService",
            "EVENT_MEDIA_ITEM_TRANSITION index=$currentIndex; sync handled by onMediaItemTransition()",
        )

        bookCompletionTracker.stopPositionCheck()
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

        // Preload next track for smooth transition
        if (currentIndex >= 0 && currentIndex < totalTracks - 1) {
            val nextIndex = currentIndex + 1
            val nextTrackLoaded =
                try {
                    player.getMediaItemAt(nextIndex)
                    true
                } catch (_: IndexOutOfBoundsException) {
                    false
                } catch (_: Exception) {
                    false
                }

            if (!nextTrackLoaded) {
                LogUtils.d("AudioPlayerService", "Preloading next track $nextIndex")
                preloadNextTrack?.invoke(nextIndex)
            }
        }

        // Optimize memory for large playlists
        if (totalTracks > 50) {
            optimizeMemoryUsage?.invoke(currentIndex)
        }

        // Restart position check on last track while playing
        if (player.isPlaying &&
            !getIsBookCompleted() &&
            player.playbackState == Player.STATE_READY &&
            currentIndex >= totalTracks - 1
        ) {
            LogUtils.d("AudioPlayerService", "Transition to last track: starting position check")
            bookCompletionTracker.startPositionCheck()
        }

        LogUtils.d("AudioPlayerService", "Media item transition: index=$currentIndex, total=${player.mediaItemCount}")

        playerErrorHandler.resetCounts()

        // Restart position check if playing
        if (player.isPlaying && !getIsBookCompleted() && player.playbackState == Player.STATE_READY) {
            bookCompletionTracker.startPositionCheck()
        }
    }

    private fun playbackStateName(state: Int): String =
        when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
}
