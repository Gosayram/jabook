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

/**
 * P-83: Typed command for setPlaylist operations with built-in validation.
 *
 * Replaces fragile Bundle/Map parsing with a validated data class.
 * All parameters are validated at construction time — invalid states
 * are impossible.
 *
 * Usage:
 * ```
 * val command = SetPlaylistCommand(
 *     bookId = "book-123",
 *     filePaths = listOf("/path/to/chapter1.m4b", "/path/to/chapter2.m4b"),
 *     trackIndex = 0,
 *     positionMs = 45_000L,
 *     speed = 1.5f
 * )
 * playerController.executePlaylist(command)
 * ```
 */
public data class SetPlaylistCommand(
    val bookId: String,
    val filePaths: List<String>,
    val trackIndex: Int = 0,
    val positionMs: Long = 0L,
    val speed: Float = DEFAULT_SPEED,
    val autoPlay: Boolean = true,
    val enableCrossfade: Boolean = false,
    val crossfadeDurationMs: Long = DEFAULT_CROSSFADE_MS,
) {
    init {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(filePaths.isNotEmpty()) { "filePaths must not be empty" }
        require(trackIndex in filePaths.indices) {
            "trackIndex ($trackIndex) out of bounds [0, ${filePaths.size - 1}]"
        }
        require(positionMs >= 0) { "positionMs must be non-negative, got $positionMs" }
        require(speed in MIN_SPEED..MAX_SPEED) {
            "speed ($speed) must be in [$MIN_SPEED, $MAX_SPEED]"
        }
        require(crossfadeDurationMs >= 0) {
            "crossfadeDurationMs must be non-negative, got $crossfadeDurationMs"
        }
    }

    /** Total number of tracks in the playlist. */
    val trackCount: Int get() = filePaths.size

    /** Whether this is a single-track playlist. */
    val isSingleTrack: Boolean get() = filePaths.size == 1

    /** Returns the file path of the current track. */
    val currentFilePath: String get() = filePaths[trackIndex]

    /**
     * Creates a copy with a new track index, clamped to valid range.
     */
    public fun withTrackIndex(index: Int): SetPlaylistCommand = copy(trackIndex = index.coerceIn(0, filePaths.size - 1))

    /**
     * Creates a copy with a new position, clamped to non-negative.
     */
    public fun withPosition(positionMs: Long): SetPlaylistCommand = copy(positionMs = positionMs.coerceAtLeast(0L))

    /**
     * Creates a copy with a new speed, clamped to valid range.
     */
    public fun withSpeed(speed: Float): SetPlaylistCommand = copy(speed = speed.coerceIn(MIN_SPEED, MAX_SPEED))

    public companion object {
        internal const val DEFAULT_SPEED = 1.0f
        internal const val MIN_SPEED = 0.5f
        internal const val MAX_SPEED = 4.0f
        internal const val DEFAULT_CROSSFADE_MS = 3_000L
    }
}
