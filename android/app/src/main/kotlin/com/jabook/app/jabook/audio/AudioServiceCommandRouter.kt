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

import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils

/**
 * Routes all playback command methods for [AudioPlayerService].
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates all play/pause/stop/seek/speed/repeat/shuffle/next/previous/rewind/forward
 * commands into a single router, reducing service boilerplate.
 *
 * @param getPlaybackController provider for PlaybackController
 * @param getPositionManager provider for PositionManager
 * @param getMetadataManager provider for MetadataManager
 * @param getPlayerStateHelper provider for PlayerStateHelper
 * @param getUnloadManager provider for UnloadManager
 * @param getActivePlayer provider for active ExoPlayer instance
 * @param getPlaybackLifecycleActions provider for PlaybackLifecycleActions side effects
 * @param resetBookCompletionIfNeeded resets book completion flag if needed
 * @param updateCrashPlaybackContext updates crash diagnostics context
 */
internal class AudioServiceCommandRouter(
    private val getPlaybackController: () -> PlaybackController?,
    private val getPositionManager: () -> PositionManager?,
    private val getMetadataManager: () -> MetadataManager?,
    private val getPlayerStateHelper: () -> PlayerStateHelper?,
    private val getUnloadManager: () -> UnloadManager?,
    private val getActivePlayer: () -> androidx.media3.exoplayer.ExoPlayer,
    private val getPlaybackLifecycleActions: () -> PlaybackLifecycleActions,
    private val resetBookCompletionIfNeeded: (String) -> Unit,
    private val updateCrashPlaybackContext: () -> Unit,
) {
    // --- Core playback commands ---

    fun play() {
        resetBookCompletionIfNeeded("play()")
        getPlaybackController()?.play() ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
            return
        }
        getPlaybackLifecycleActions().onPlay()
    }

    fun pause() {
        getPlaybackController()?.pause() ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
            return
        }
        getPlaybackLifecycleActions().onPause()
    }

    fun stop() {
        getPlaybackController()?.stop() ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
            return
        }
        getPlaybackLifecycleActions().onStop()
    }

    // --- Seek commands ---

    fun seekTo(positionMs: Long) {
        getPlaybackController()?.seekTo(positionMs)
    }

    fun seekToTrack(index: Int) {
        resetBookCompletionIfNeeded("Manual seekToTrack($index)")
        getPlaybackController()?.seekToTrack(index) ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
        }
    }

    fun seekToTrackAndPosition(
        trackIndex: Int,
        positionMs: Long,
    ) {
        resetBookCompletionIfNeeded("Manual seekToTrackAndPosition($trackIndex, $positionMs)")
        getPlaybackController()?.seekToTrackAndPosition(trackIndex, positionMs) ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
        }
    }

    // --- Speed / Repeat / Shuffle ---

    fun setSpeed(speed: Float) {
        getPlaybackController()?.setSpeed(speed)
        updateCrashPlaybackContext()
    }

    fun getSpeed(): Float = getPlaybackController()?.getSpeed() ?: 1.0f

    fun setRepeatMode(repeatMode: Int) {
        getPlaybackController()?.setRepeatMode(repeatMode)
    }

    fun getRepeatMode(): Int = getPlaybackController()?.getRepeatMode() ?: Player.REPEAT_MODE_OFF

    fun setShuffleModeEnabled(enabled: Boolean) {
        getPlaybackController()?.setShuffleModeEnabled(enabled)
    }

    fun getShuffleModeEnabled(): Boolean = getPlaybackController()?.getShuffleModeEnabled() ?: false

    // --- Navigation ---

    fun next() {
        resetBookCompletionIfNeeded("Manual next()")
        getPlaybackController()?.next() ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
        }
    }

    fun previous() {
        resetBookCompletionIfNeeded("Manual previous()")
        getPlaybackController()?.previous() ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
        }
    }

    fun rewind(seconds: Int = 15) {
        getPlaybackController()?.rewind(seconds) ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
        }
    }

    fun forward(seconds: Int = 30) {
        getPlaybackController()?.forward(seconds) ?: run {
            LogUtils.e(TAG, "PlaybackController not initialized")
        }
    }

    // --- Metadata / Position ---

    fun updateMetadata(metadata: Map<String, String>) {
        getMetadataManager()?.updateMetadata(metadata) ?: run {
            LogUtils.e(TAG, "MetadataManager not initialized")
        }
        updateCrashPlaybackContext()
    }

    fun setPlaybackProgress(
        filePaths: List<String>,
        progressSeconds: Double?,
    ) {
        getPositionManager()?.setPlaybackProgress(filePaths, progressSeconds) ?: run {
            LogUtils.e(TAG, "PositionManager not initialized")
        }
    }

    fun saveCurrentPosition() {
        getPositionManager()?.saveCurrentPosition() ?: run {
            LogUtils.e(TAG, "PositionManager not initialized")
        }
    }

    // --- State queries ---

    fun getCurrentPosition(): Long = getPlayerStateHelper()?.getCurrentPosition() ?: 0L

    fun getDuration(): Long = getPlayerStateHelper()?.getDuration() ?: 0L

    fun getPlayerState(): Map<String, Any> = getPlayerStateHelper()?.getPlayerState() ?: emptyMap()

    fun getCurrentMediaItemInfo(): Map<String, Any?> = getMetadataManager()?.getCurrentMediaItemInfo() ?: emptyMap()

    fun extractArtworkFromFile(filePath: String): String? = getMetadataManager()?.extractArtworkFromFile(filePath)

    fun getPlaylistInfo(): Map<String, Any> = getPlayerStateHelper()?.getPlaylistInfo() ?: emptyMap()

    val isPlaying: Boolean
        get() = getActivePlayer().isPlaying

    // --- Unload ---

    fun unloadPlayerDueToInactivity() {
        getUnloadManager()?.unloadPlayerDueToInactivity() ?: run {
            LogUtils.e(TAG, "UnloadManager not initialized")
        }
    }

    private companion object {
        private const val TAG = "AudioPlayerService"
    }
}
