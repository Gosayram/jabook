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

import android.os.Bundle
import androidx.media3.session.MediaConstants

/**
 * Helper for creating Media3-compatible completion status extras.
 *
 * Used to show visual indicators in:
 * - Android Auto (progress rings on audiobook covers)
 * - Library UI (Not Started / In Progress / Completed badges)
 * - Third-party media browsers
 *
 * Based on MediaConstants from Media3 library.
 */
public object CompletionStatusHelper {
    /**
     * Calculate completion percentage from position and duration.
     *
     * @param positionMs Current playback position in milliseconds
     * @param durationMs Total audiobook duration in milliseconds
     * @return Completion percentage from 0.0 (not started) to 1.0 (fully played)
     */
    public fun calculateCompletionPercentage(
        positionMs: Long,
        durationMs: Long,
    ): Double {
        if (durationMs <= 0) return 0.0
        return (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
    }

    /**
     * Calculate completion percentage considering all tracks in playlist (inspired by Easybook).
     *
     * This provides a more accurate progress calculation by accounting for:
     * - Completed tracks (fully played)
     * - Current track position
     * - Remaining tracks
     *
     * @param currentTrackIndex Index of currently playing track (0-based)
     * @param currentPositionMs Position within current track in milliseconds
     * @param trackDurations List of track durations in milliseconds (must match playlist order)
     * @return Completion percentage from 0.0 (not started) to 1.0 (fully played)
     */
    public fun calculateCompletionPercentageWithTracks(
        currentTrackIndex: Int,
        currentPositionMs: Long,
        trackDurations: List<Long>,
    ): Double {
        if (trackDurations.isEmpty()) return 0.0

        // Validate current track index
        val safeTrackIndex = currentTrackIndex.coerceIn(0, trackDurations.size - 1)

        // Calculate total duration of all tracks
        val totalDuration = trackDurations.sum()

        if (totalDuration <= 0) return 0.0

        // Calculate completed duration:
        // 1. Sum of all completed tracks (tracks before current)
        val completedTracksDuration =
            trackDurations.take(safeTrackIndex).sum()

        // 2. Add current track position
        val currentTrackDuration = trackDurations[safeTrackIndex]
        val currentTrackProgress = currentPositionMs.coerceIn(0, currentTrackDuration)

        // Total completed duration
        val totalCompletedDuration = completedTracksDuration + currentTrackProgress

        // Calculate percentage
        return (totalCompletedDuration.toDouble() / totalDuration).coerceIn(0.0, 1.0)
    }

    /**
     * Get Media3 completion status code based on percentage.
     *
     * Thresholds:
     * - 0% - 1%: Not played
     * - 1% - 95%: Partially played
     * - 95% - 100%: Fully played (allows 5% margin for credits/silence)
     */
    public fun getCompletionStatus(completionPercentage: Double): Int =
        when {
            completionPercentage < 0.01 -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
            completionPercentage >= 0.95 -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
            else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
        }

    /**
     * Create Bundle extras with completion status for MediaMetadata.
     *
     * Usage:
     * ```kotlin
     * val extras = createCompletionExtras(positionMs = 45000, durationMs = 60000)
     * MediaMetadata.Builder()
     *     .setExtras(extras)
     *     .build()
     * ```
     *
     * @param positionMs Current playback position in milliseconds
     * @param durationMs Total audiobook duration in milliseconds
     * @return Bundle with EXTRAS_KEY_COMPLETION_STATUS and optionally EXTRAS_KEY_COMPLETION_PERCENTAGE
     */
    public fun createCompletionExtras(
        positionMs: Long,
        durationMs: Long,
    ): Bundle {
        val percentage = calculateCompletionPercentage(positionMs, durationMs)
        val status = getCompletionStatus(percentage)

        return Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, status)

            // Only include percentage for partially played items
            if (status == MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED) {
                putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, percentage)
            }
        }
    }

    /**
     * Get human-readable completion status text (for debugging/UI).
     *
     * @param context Android context for string resources
     * @param status Completion status code
     * @return Localized status string
     */
    public fun getCompletionStatusText(
        context: android.content.Context,
        status: Int,
    ): String =
        when (status) {
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED ->
                context.getString(com.jabook.app.jabook.R.string.media3_completion_not_started)
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED ->
                context.getString(com.jabook.app.jabook.R.string.media3_completion_in_progress)
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED ->
                context.getString(com.jabook.app.jabook.R.string.media3_completion_completed)
            else -> context.getString(com.jabook.app.jabook.R.string.unknown)
        }
}
