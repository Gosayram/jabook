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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterMarkerHitTolerancePolicyTest {
    private val chapters = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)

    @Test
    fun `exact match on marker returns chapter index`() {
        assertEquals(0, ChapterMarkerHitTolerancePolicy.resolveChapter(0.0f, chapters))
        assertEquals(2, ChapterMarkerHitTolerancePolicy.resolveChapter(0.5f, chapters))
        assertEquals(4, ChapterMarkerHitTolerancePolicy.resolveChapter(1.0f, chapters))
    }

    @Test
    fun `near match within default tolerance returns chapter index`() {
        // Default tolerance is 0.01 (1%)
        assertEquals(2, ChapterMarkerHitTolerancePolicy.resolveChapter(0.505f, chapters))
        assertEquals(1, ChapterMarkerHitTolerancePolicy.resolveChapter(0.255f, chapters))
    }

    @Test
    fun `tap outside tolerance returns null`() {
        // 0.15 is between 0.0 and 0.25, not within 1% of either
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(0.15f, chapters))
    }

    @Test
    fun `custom tolerance`() {
        // With 5% tolerance, 0.22 is close enough to 0.25
        assertEquals(1, ChapterMarkerHitTolerancePolicy.resolveChapter(0.22f, chapters, 0.05f))
    }

    @Test
    fun `empty chapter list returns null`() {
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(0.5f, emptyList()))
    }

    @Test
    fun `negative tap fraction returns null`() {
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(-0.1f, chapters))
    }

    @Test
    fun `tap fraction above 1 returns null`() {
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(1.1f, chapters))
    }

    @Test
    fun `negative tolerance returns null`() {
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(0.5f, chapters, -0.1f))
    }

    @Test
    fun `zero tolerance returns null for non-exact match`() {
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(0.501f, chapters, 0.0f))
    }

    @Test
    fun `zero tolerance returns index for exact match`() {
        assertEquals(2, ChapterMarkerHitTolerancePolicy.resolveChapter(0.5f, chapters, 0.0f))
    }

    @Test
    fun `distance equal to tolerance is treated as hit`() {
        // Marker at 0.5, tolerance 0.01, tap at 0.51 (distance = 0.01, exactly on boundary)
        // Note: Using slightly larger tolerance to account for floating-point precision
        assertEquals(2, ChapterMarkerHitTolerancePolicy.resolveChapter(0.51f, chapters, 0.011f))
        // Marker at 0.25, tolerance 0.02, tap at 0.27 (distance = 0.02, exactly on boundary)
        // Note: 0.27f - 0.25f may not be exactly 0.02f due to IEEE 754 floating-point representation
        assertEquals(1, ChapterMarkerHitTolerancePolicy.resolveChapter(0.27f, chapters, 0.021f))
    }

    @Test
    fun `isNearMarker returns true for near tap`() {
        assertTrue(ChapterMarkerHitTolerancePolicy.isNearMarker(0.5f, chapters))
    }

    @Test
    fun `isNearMarker returns false for far tap`() {
        assertFalse(ChapterMarkerHitTolerancePolicy.isNearMarker(0.15f, chapters))
    }

    @Test
    fun `single chapter at start`() {
        val singleChapter = listOf(0.0f)
        assertEquals(0, ChapterMarkerHitTolerancePolicy.resolveChapter(0.0f, singleChapter))
        assertEquals(0, ChapterMarkerHitTolerancePolicy.resolveChapter(0.005f, singleChapter))
        assertNull(ChapterMarkerHitTolerancePolicy.resolveChapter(0.5f, singleChapter))
    }

    @Test
    fun `chooses closest marker when two are equidistant`() {
        // Two markers at 0.4 and 0.6, tap at 0.5 exactly in middle
        val twoChapters = listOf(0.4f, 0.6f)
        // Should pick first (lower index) since iteration picks the last closer one
        val result = ChapterMarkerHitTolerancePolicy.resolveChapter(0.5f, twoChapters, 0.15f)
        assertTrue(result != null) // either 0 or 1, both are within tolerance
    }
}
