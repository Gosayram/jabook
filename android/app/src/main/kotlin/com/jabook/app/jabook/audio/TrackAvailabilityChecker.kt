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

import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Checks if a track is available for playback.
 *
 * Inspired by lissen-android PlaybackNotificationService implementation.
 * This checks if the file exists locally or if it's a valid network URL.
 */
public object TrackAvailabilityChecker {
    /**
     * Checks if a track at the given index is available.
     *
     * @param player ExoPlayer instance
     * @param index Track index to check
     * @return true if track is available, false otherwise
     */
    public fun isTrackAvailable(
        player: ExoPlayer,
        index: Int,
    ): Boolean {
        if (index < 0 || index >= player.mediaItemCount) {
            return false
        }

        val mediaItem = player.getMediaItemAt(index)
        val uri = mediaItem.localConfiguration?.uri ?: return false

        return when {
            uri.scheme == "file" -> {
                // Local file - check if it exists
                val file = File(uri.path ?: return false)
                val exists = file.exists() && file.isFile && file.canRead()
                if (!exists) {
                    android.util.Log.w(
                        "TrackAvailabilityChecker",
                        "Track $index not available: file does not exist: ${uri.path}",
                    )
                }
                exists
            }
            uri.scheme == "http" || uri.scheme == "https" -> {
                // Network URL - assume available (will fail during playback if not)
                true
            }
            else -> {
                android.util.Log.w(
                    "TrackAvailabilityChecker",
                    "Track $index not available: unsupported URI scheme: ${uri.scheme}",
                )
                false
            }
        }
    }

    /**
     * Finds the next available track index in the given direction.
     *
     * Inspired by lissen-android findAvailableTrackIndex implementation.
     *
     * @param player ExoPlayer instance
     * @param currentIndex Current track index
     * @param direction Direction to search (FORWARD or BACKWARD)
     * @param maxIterations Maximum number of iterations to prevent infinite loops
     * @return Index of next available track, or null if none found
     */
    public fun findAvailableTrackIndex(
        player: ExoPlayer,
        currentIndex: Int,
        direction: Direction,
        maxIterations: Int = 4096,
    ): Int? {
        if (isTrackAvailable(player, currentIndex)) {
            return currentIndex
        }

        if (maxIterations <= 0) {
            android.util.Log.w("TrackAvailabilityChecker", "Max iterations reached while searching for available track")
            return null
        }

        val nextIndex =
            when (direction) {
                Direction.FORWARD -> (currentIndex + 1) % player.mediaItemCount
                Direction.BACKWARD -> if (currentIndex - 1 < 0) player.mediaItemCount - 1 else currentIndex - 1
            }

        return findAvailableTrackIndex(player, nextIndex, direction, maxIterations - 1)
    }

    /**
     * Direction for searching available tracks.
     */
    public enum class Direction {
        FORWARD,
        BACKWARD,
    }
}
