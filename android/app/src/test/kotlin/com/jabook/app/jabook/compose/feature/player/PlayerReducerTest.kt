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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerReducerTest {
    @Test
    fun `nextChapterRepeatMode cycles through all modes`() {
        assertEquals(ChapterRepeatMode.ONCE, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.OFF))
        assertEquals(ChapterRepeatMode.INFINITE, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.ONCE))
        assertEquals(ChapterRepeatMode.OFF, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.INFINITE))
    }

    @Test
    fun `reduceChapterEnded in OFF mode never repeats and resets flag`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.OFF,
                hasRepeatedOnce = true,
            )

        assertFalse(reduction.shouldRepeat)
        assertFalse(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in ONCE mode repeats once when not repeated yet`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.ONCE,
                hasRepeatedOnce = false,
            )

        assertTrue(reduction.shouldRepeat)
        assertTrue(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in ONCE mode stops repeating after first repeat`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.ONCE,
                hasRepeatedOnce = true,
            )

        assertFalse(reduction.shouldRepeat)
        assertFalse(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in INFINITE mode always repeats and keeps flag`() {
        val reductionWithFalseFlag =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.INFINITE,
                hasRepeatedOnce = false,
            )
        val reductionWithTrueFlag =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.INFINITE,
                hasRepeatedOnce = true,
            )

        assertTrue(reductionWithFalseFlag.shouldRepeat)
        assertFalse(reductionWithFalseFlag.hasRepeatedOnce)
        assertTrue(reductionWithTrueFlag.shouldRepeat)
        assertTrue(reductionWithTrueFlag.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterChanged resets repeat flag`() {
        assertFalse(PlayerReducer.reduceChapterChanged())
    }
}
