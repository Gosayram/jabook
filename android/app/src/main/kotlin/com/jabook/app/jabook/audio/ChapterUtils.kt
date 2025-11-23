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

/**
 * Utility functions for calculating chapter index and position.
 * 
 * Inspired by lissen-android implementation for chapter navigation and progress tracking.
 * These functions are used for:
 * - Seeking to specific positions across multiple chapters
 * - Calculating current chapter index from total playback position
 * - Calculating position within current chapter
 * - Timer functionality (e.g., "until end of current chapter")
 */

/**
 * Calculates the chapter index for a given total playback position.
 * 
 * @param chapterDurations List of chapter durations in seconds
 * @param totalPosition Total playback position in seconds
 * @return Index of the chapter containing the position, or last chapter if position is beyond all chapters
 * 
 * Example:
 * ```
 * val durations = listOf(300.0, 600.0, 450.0) // 5min, 10min, 7.5min
 * val index = calculateChapterIndex(durations, 650.0) // Returns 1 (second chapter)
 * ```
 */
fun calculateChapterIndex(
    chapterDurations: List<Double>,
    totalPosition: Double
): Int {
    if (chapterDurations.isEmpty()) {
        return 0
    }
    
    var accumulatedDuration = 0.0
    
    for ((index, chapterDuration) in chapterDurations.withIndex()) {
        accumulatedDuration += chapterDuration
        if (totalPosition < accumulatedDuration - 0.1) {
            return index
        }
    }
    
    // Position is beyond all chapters, return last chapter index
    return chapterDurations.size - 1
}

/**
 * Calculates the position within the current chapter for a given total playback position.
 * 
 * @param chapterDurations List of chapter durations in seconds
 * @param totalPosition Total playback position in seconds
 * @return Position within the current chapter in seconds, or 0.0 if position is beyond all chapters
 * 
 * Example:
 * ```
 * val durations = listOf(300.0, 600.0, 450.0) // 5min, 10min, 7.5min
 * val position = calculateChapterPosition(durations, 650.0) // Returns 50.0 (50 seconds into second chapter)
 * ```
 */
fun calculateChapterPosition(
    chapterDurations: List<Double>,
    totalPosition: Double
): Double {
    if (chapterDurations.isEmpty()) {
        return 0.0
    }
    
    var accumulatedDuration = 0.0
    
    for (chapterDuration in chapterDurations) {
        val chapterEnd = accumulatedDuration + chapterDuration
        if (totalPosition < chapterEnd - 0.1) {
            return (totalPosition - accumulatedDuration)
        }
        accumulatedDuration = chapterEnd
    }
    
    // Position is beyond all chapters
    return 0.0
}

/**
 * Calculates the chapter index for a given total playback position (milliseconds version).
 * 
 * @param chapterDurationsMs List of chapter durations in milliseconds
 * @param totalPositionMs Total playback position in milliseconds
 * @return Index of the chapter containing the position, or last chapter if position is beyond all chapters
 */
fun calculateChapterIndexMs(
    chapterDurationsMs: List<Long>,
    totalPositionMs: Long
): Int {
    if (chapterDurationsMs.isEmpty()) {
        return 0
    }
    
    var accumulatedDuration = 0L
    
    for ((index, chapterDuration) in chapterDurationsMs.withIndex()) {
        accumulatedDuration += chapterDuration
        if (totalPositionMs < accumulatedDuration - 100) { // 0.1s = 100ms tolerance
            return index
        }
    }
    
    // Position is beyond all chapters, return last chapter index
    return chapterDurationsMs.size - 1
}

/**
 * Calculates the position within the current chapter for a given total playback position (milliseconds version).
 * 
 * @param chapterDurationsMs List of chapter durations in milliseconds
 * @param totalPositionMs Total playback position in milliseconds
 * @return Position within the current chapter in milliseconds, or 0L if position is beyond all chapters
 */
fun calculateChapterPositionMs(
    chapterDurationsMs: List<Long>,
    totalPositionMs: Long
): Long {
    if (chapterDurationsMs.isEmpty()) {
        return 0L
    }
    
    var accumulatedDuration = 0L
    
    for (chapterDuration in chapterDurationsMs) {
        val chapterEnd = accumulatedDuration + chapterDuration
        if (totalPositionMs < chapterEnd - 100) { // 0.1s = 100ms tolerance
            return (totalPositionMs - accumulatedDuration)
        }
        accumulatedDuration = chapterEnd
    }
    
    // Position is beyond all chapters
    return 0L
}

