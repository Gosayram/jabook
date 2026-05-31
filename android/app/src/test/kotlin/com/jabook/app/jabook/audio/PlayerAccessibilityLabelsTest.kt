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

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAccessibilityLabelsTest {
    @Test
    fun `playPause playing`() {
        assertEquals("Пауза", PlayerAccessibilityLabels.playPause(true))
    }

    @Test
    fun `playPause paused`() {
        assertEquals("Воспроизведение", PlayerAccessibilityLabels.playPause(false))
    }

    @Test
    fun `skipNext without title`() {
        assertEquals("Следующая глава", PlayerAccessibilityLabels.skipNext())
    }

    @Test
    fun `skipNext with title`() {
        assertEquals("Следующая глава: Глава 5", PlayerAccessibilityLabels.skipNext("Глава 5"))
    }

    @Test
    fun `skipPrevious without title`() {
        assertEquals("Предыдущая глава", PlayerAccessibilityLabels.skipPrevious())
    }

    @Test
    fun `skipPrevious with title`() {
        assertEquals("Предыдущая глава: Глава 3", PlayerAccessibilityLabels.skipPrevious("Глава 3"))
    }

    @Test
    fun `seekBar label`() {
        val label = PlayerAccessibilityLabels.seekBar(65_000, 3_600_000)
        assertEquals("Позиция: 1:05 из 1:00:00", label)
    }

    @Test
    fun `playbackSpeed label`() {
        val label = PlayerAccessibilityLabels.playbackSpeed(1.5f)
        assert(label.contains("1") && label.contains("5") && label.contains("x"))
    }

    @Test
    fun `sleepTimer active`() {
        assertEquals("Таймер сна: 30 мин", PlayerAccessibilityLabels.sleepTimer(1800))
    }

    @Test
    fun `sleepTimer inactive`() {
        assertEquals("Таймер сна: выключен", PlayerAccessibilityLabels.sleepTimer(null))
    }

    @Test
    fun `bookmark with existing bookmark`() {
        assertEquals("Закладка установлена", PlayerAccessibilityLabels.bookmark(true))
    }

    @Test
    fun `bookmark without bookmark`() {
        assertEquals("Добавить закладку", PlayerAccessibilityLabels.bookmark(false))
    }

    @Test
    fun `chapterList label`() {
        assertEquals("Глава 3 из 10", PlayerAccessibilityLabels.chapterList(2, 10))
    }
}
