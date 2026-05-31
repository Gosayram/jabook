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
 * P-74: Accessibility labels for player controls.
 *
 * Provides semantic descriptions for screen readers (TalkBack)
 * and remote-first navigation (Android TV, webOS).
 *
 * Usage:
 * ```
 * Modifier.semantics {
 *     contentDescription = PlayerAccessibilityLabels.playPause(isPlaying = true)
 * }
 * ```
 */
public object PlayerAccessibilityLabels {
    /**
     * Play/pause button label.
     */
    public fun playPause(isPlaying: Boolean): String = if (isPlaying) "Пауза" else "Воспроизведение"

    /**
     * Skip next button label.
     */
    public fun skipNext(chapterTitle: String? = null): String =
        if (chapterTitle != null) "Следующая глава: $chapterTitle" else "Следующая глава"

    /**
     * Skip previous button label.
     */
    public fun skipPrevious(chapterTitle: String? = null): String =
        if (chapterTitle != null) "Предыдущая глава: $chapterTitle" else "Предыдущая глава"

    /**
     * Seek bar label with current position and duration.
     */
    public fun seekBar(
        currentPositionMs: Long,
        durationMs: Long,
    ): String {
        val current = formatTime(currentPositionMs)
        val total = formatTime(durationMs)
        return "Позиция: $current из $total"
    }

    /**
     * Playback speed label.
     */
    public fun playbackSpeed(speed: Float): String = "Скорость воспроизведения: ${"%.1f".format(speed)}x"

    /**
     * Sleep timer label.
     */
    public fun sleepTimer(remainingSeconds: Int?): String =
        if (remainingSeconds != null && remainingSeconds > 0) {
            val minutes = remainingSeconds / 60
            "Таймер сна: $minutes мин"
        } else {
            "Таймер сна: выключен"
        }

    /**
     * Bookmark button label.
     */
    public fun bookmark(hasBookmark: Boolean): String = if (hasBookmark) "Закладка установлена" else "Добавить закладку"

    /**
     * Chapter list label.
     */
    public fun chapterList(
        currentIndex: Int,
        totalCount: Int,
    ): String = "Глава ${currentIndex + 1} из $totalCount"

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
