// Copyright 2025 Jabook Contributors
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
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Constants for position saving broadcasts.
 */
object PositionConstants {
    const val ACTION_SAVE_POSITION_BEFORE_UNLOAD = "com.jabook.app.jabook.audio.SAVE_POSITION_BEFORE_UNLOAD"
    const val EXTRA_TRACK_INDEX = "trackIndex"
    const val EXTRA_POSITION_MS = "positionMs"
}

/**
 * Manages playback position operations (save, restore, seek).
 */
internal class PositionManager(
    private val context: Context,
    private val getActivePlayer: () -> ExoPlayer,
    private val packageName: String,
    private val sendBroadcast: (Intent) -> Unit,
) {
    companion object {
        // Use PositionConstants for public access
        const val ACTION_SAVE_POSITION_BEFORE_UNLOAD = PositionConstants.ACTION_SAVE_POSITION_BEFORE_UNLOAD
        const val EXTRA_TRACK_INDEX = PositionConstants.EXTRA_TRACK_INDEX
        const val EXTRA_POSITION_MS = PositionConstants.EXTRA_POSITION_MS
    }

    /**
     * Saves current playback position via broadcast.
     *
     * This method broadcasts the current position to trigger saving through MethodChannel.
     * Position is also saved periodically, so this is an additional safety measure.
     */
    fun saveCurrentPosition() {
        try {
            val activePlayer = getActivePlayer()
            if (activePlayer.mediaItemCount > 0) {
                val currentIndex = activePlayer.currentMediaItemIndex
                val currentPosition = activePlayer.currentPosition

                // Broadcast intent to trigger position saving through MethodChannel
                val saveIntent =
                    Intent(PositionConstants.ACTION_SAVE_POSITION_BEFORE_UNLOAD).apply {
                        putExtra(PositionConstants.EXTRA_TRACK_INDEX, currentIndex)
                        putExtra(PositionConstants.EXTRA_POSITION_MS, currentPosition)
                        setPackage(packageName) // Set package for explicit broadcast
                    }
                sendBroadcast(saveIntent)
                android.util.Log.d(
                    "AudioPlayerService",
                    "Position save broadcast sent: track=$currentIndex, position=${currentPosition}ms",
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Failed to send save position broadcast", e)
            // Not critical - position is already saved periodically
        }
    }

    /**
     * Sets playback progress from saved position.
     *
     * Inspired by lissen-android: restores playback position across multiple tracks/chapters
     * using cumulative durations for accurate seeking.
     *
     * @param filePaths List of file paths (for reference, actual durations come from MediaItems)
     * @param progressSeconds Progress in seconds (overall position across all tracks)
     */
    fun setPlaybackProgress(
        filePaths: List<String>,
        progressSeconds: Double?,
    ) {
        val player = getActivePlayer()

        if (filePaths.isEmpty() || player.mediaItemCount == 0) {
            android.util.Log.w("AudioPlayerService", "Cannot set playback progress: empty file list or no media items")
            return
        }

        when (progressSeconds) {
            null, 0.0 -> {
                // No saved progress, start from beginning
                player.seekTo(0, 0L)
                android.util.Log.d("AudioPlayerService", "No saved progress, starting from beginning")
            }
            else -> {
                // Calculate which track and position to seek to
                val positionMs = (progressSeconds * 1000).toLong()

                // According to best practices: use only real durations from player
                // Do NOT use approximate estimates (unreliable for VBR, partial downloads, etc.)
                //
                // Strategy: Use current track duration if available
                // If position is within current track, seek to it
                // Otherwise, we can't accurately determine position across tracks without knowing all durations
                val currentTrackDuration = player.duration
                val currentIndex = player.currentMediaItemIndex

                if (currentTrackDuration != C.TIME_UNSET && currentTrackDuration > 0) {
                    // We have duration for current track
                    if (positionMs <= currentTrackDuration) {
                        // Position is within current track - seek to it
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Restoring playback position within current track: position=${positionMs}ms (from ${progressSeconds}s)",
                        )
                        player.seekTo(currentIndex, positionMs.coerceAtLeast(0L))
                    } else {
                        // Position is beyond current track duration
                        // Without knowing durations of other tracks, we can't accurately determine target track
                        // According to best practices: don't use unreliable estimates
                        // Seek to beginning of current track as safe fallback
                        android.util.Log.w(
                            "AudioPlayerService",
                            "Cannot accurately restore position across tracks without knowing all durations. Seeking to beginning of current track.",
                        )
                        player.seekTo(currentIndex, 0L)
                    }
                } else {
                    // No duration available - can't determine position accurately
                    // According to best practices: don't use unreliable estimates
                    // Just seek to beginning as fallback
                    android.util.Log.w(
                        "AudioPlayerService",
                        "Cannot restore playback position: duration not available. Seeking to beginning.",
                    )
                    player.seekTo(0, 0L)
                }
            }
        }
    }
}
