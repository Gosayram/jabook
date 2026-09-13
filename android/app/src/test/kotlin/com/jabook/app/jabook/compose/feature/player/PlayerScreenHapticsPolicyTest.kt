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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerScreenHapticsPolicyTest {
    @Test
    fun `resolveChapterBoundaryHapticDecision returns null when chapter unchanged`() {
        val result =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 5,
                newChapterIndex = 5,
                skipTriggeredHaptic = false,
            )
        assertNull(result)
    }

    @Test
    fun `resolveChapterBoundaryHapticDecision performs haptic on normal chapter change`() {
        val result =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 1,
                newChapterIndex = 2,
                skipTriggeredHaptic = false,
            )
        assertNotNull(result)
        assertTrue(result?.shouldPerformHaptic ?: false)
        assertFalse(result?.nextSkipTriggeredHaptic ?: true)
        assertEquals(2, result?.nextLastChapterBoundaryIndex)
    }

    @Test
    fun `resolveChapterBoundaryHapticDecision skips haptic after skip`() {
        val result =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 1,
                newChapterIndex = 2,
                skipTriggeredHaptic = true,
            )
        assertNotNull(result)
        assertFalse(result?.shouldPerformHaptic ?: true)
        assertFalse(result?.nextSkipTriggeredHaptic ?: true)
        assertEquals(2, result?.nextLastChapterBoundaryIndex)
    }

    @Test
    fun `resolveChapterBoundaryHapticDecision handles multiple chapter skips`() {
        // First skip - no haptic
        val result1 =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 1,
                newChapterIndex = 5,
                skipTriggeredHaptic = true,
            )
        assertFalse(result1?.shouldPerformHaptic ?: true)
        assertEquals(5, result1?.nextLastChapterBoundaryIndex)

        // Next normal - should have haptic
        val result2 =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 5,
                newChapterIndex = 6,
                skipTriggeredHaptic = false,
            )
        assertTrue(result2?.shouldPerformHaptic ?: false)
        assertEquals(6, result2?.nextLastChapterBoundaryIndex)
    }

    @Test
    fun `resolveChapterBoundaryHapticDecision handles chapter regression`() {
        val result =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 5,
                newChapterIndex = 2,
                skipTriggeredHaptic = false,
            )
        assertNotNull(result)
        assertTrue(result?.shouldPerformHaptic ?: false)
        assertEquals(2, result?.nextLastChapterBoundaryIndex)
    }
}
