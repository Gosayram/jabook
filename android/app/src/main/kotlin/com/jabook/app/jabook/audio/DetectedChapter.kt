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
 * P-54: Data model for automatically detected chapter boundaries.
 *
 * When audiobooks don't have chapter markers in metadata,
 * chapters can be detected by analyzing silence gaps in the audio.
 *
 * @property index Chapter index (0-based)
 * @property startMs Start position in milliseconds
 * @property endMs End position in milliseconds (null for last chapter)
 * @property title Auto-generated chapter title
 * @property confidence Detection confidence (0.0–1.0)
 */
public data class DetectedChapter(
    val index: Int,
    val startMs: Long,
    val endMs: Long?,
    val title: String,
    val confidence: Float = 1.0f,
) {
    /** Duration in milliseconds, or null if end is unknown. */
    val durationMs: Long?
        get() = endMs?.let { it - startMs }

    /** Whether this is the final chapter. */
    val isLast: Boolean
        get() = endMs == null

    /**
     * Returns a human-readable duration string.
     */
    public fun formatDuration(): String {
        val dur = durationMs ?: return "ongoing"
        val seconds = dur / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    public companion object {
        /**
         * Minimum silence duration to consider as chapter boundary.
         */
        internal const val MIN_SILENCE_MS = 2000L

        /**
         * Scan interval for silence detection.
         */
        internal const val SCAN_INTERVAL_MS = 500L

        /**
         * Creates chapter titles for a list of detected chapters.
         */
        public fun generateTitles(count: Int): List<String> = (1..count).map { "Глава $it" }
    }
}
