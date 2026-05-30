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
 * P-64: Chapter marker extracted from M4B/MP4 container.
 *
 * M4B audiobook files store chapter markers in the `udta/chpl` atom.
 * This data class represents a single chapter with its timing and metadata.
 *
 * @property index Chapter index (0-based)
 * @property startMs Start position in milliseconds
 * @property title Chapter title (from metadata or auto-generated)
 * @property durationMs Chapter duration in milliseconds (null if unknown)
 */
public data class M4bChapter(
    val index: Int,
    val startMs: Long,
    val title: String,
    val durationMs: Long? = null,
) {
    /**
     * End position in milliseconds, or null if duration unknown.
     */
    val endMs: Long?
        get() = durationMs?.let { startMs + it }

    /**
     * Whether this is the final chapter.
     */
    val isLast: Boolean
        get() = durationMs == null

    /**
     * Human-readable duration string.
     */
    public fun formatDuration(): String {
        val dur = durationMs ?: return "ongoing"
        val totalSeconds = dur / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Human-readable start position string.
     */
    public fun formatStart(): String {
        val totalSeconds = startMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%d:%02d".format(minutes, seconds)
        }
    }

    public companion object {
        /**
         * Generates default chapter titles.
         */
        public fun generateTitle(index: Int): String = "Глава ${index + 1}"

        /**
         * Calculates chapter durations from a list of chapters with known start times.
         * The last chapter gets null duration (unknown end).
         */
        public fun calculateDurations(chapters: List<M4bChapter>): List<M4bChapter> {
            if (chapters.isEmpty()) return emptyList()
            return chapters.mapIndexed { i, chapter ->
                if (i < chapters.size - 1) {
                    chapter.copy(durationMs = chapters[i + 1].startMs - chapter.startMs)
                } else {
                    chapter
                }
            }
        }
    }
}
