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

class ChapterSelectorReorderPolicyTest {
    @Test
    fun `drag down past threshold triggers single move down and resets accumulator`() {
        val first =
            accumulateChapterReorderDrag(
                accumulated = 0f,
                dragAmount = 30f,
                threshold = 48f,
            )
        val second =
            accumulateChapterReorderDrag(
                accumulated = first.remainingAccumulated,
                dragAmount = 20f,
                threshold = 48f,
            )

        assertEquals(ChapterReorderDragAction.None, first.action)
        assertEquals(ChapterReorderDragAction.MoveDown, second.action)
        assertEquals(0f, second.remainingAccumulated, 0.0001f)
    }

    @Test
    fun `accumulator reset after cancel prevents carry-over into next gesture`() {
        val partialGesture =
            accumulateChapterReorderDrag(
                accumulated = 0f,
                dragAmount = 30f,
                threshold = 48f,
            )

        // Simulate onDragCancel/onDragEnd branch in composable.
        val nextGesture =
            accumulateChapterReorderDrag(
                accumulated = 0f,
                dragAmount = 30f,
                threshold = 48f,
            )

        assertEquals(ChapterReorderDragAction.None, partialGesture.action)
        assertEquals(ChapterReorderDragAction.None, nextGesture.action)
    }

    @Test
    fun `drag up past threshold triggers single move up and resets accumulator`() {
        val first =
            accumulateChapterReorderDrag(
                accumulated = 0f,
                dragAmount = -35f,
                threshold = 48f,
            )
        val second =
            accumulateChapterReorderDrag(
                accumulated = first.remainingAccumulated,
                dragAmount = -15f,
                threshold = 48f,
            )

        assertEquals(ChapterReorderDragAction.None, first.action)
        assertEquals(ChapterReorderDragAction.MoveUp, second.action)
        assertEquals(0f, second.remainingAccumulated, 0.0001f)
    }

    @Test
    fun `boundary checks prevent move beyond list edges`() {
        assertFalse(canMoveChapterUp(index = 0))
        assertTrue(canMoveChapterUp(index = 1))

        assertFalse(canMoveChapterDown(index = 0, totalCount = 1))
        assertTrue(canMoveChapterDown(index = 0, totalCount = 2))
        assertFalse(canMoveChapterDown(index = 1, totalCount = 2))
    }
}

