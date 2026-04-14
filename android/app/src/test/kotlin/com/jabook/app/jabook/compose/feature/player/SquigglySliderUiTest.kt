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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SquigglySlider] utility functions covering markers and value range normalization.
 */
public class SquigglySliderUiTest {
    // ==================== Chapter Markers Tests ====================

    @Test
    public fun `chapter markers are sanitized - invalid values filtered out`() {
        val invalidMarkers =
            listOf(
                -0.5f, // negative
                1.5f, // > 1
                Float.NaN, // NaN
                Float.POSITIVE_INFINITY, // Infinity
            )
        val validMarkers = listOf(0.25f, 0.5f, 0.75f)

        val result = sanitizeChapterMarkersFractions(invalidMarkers + validMarkers)

        assertEquals(3, result.size)
        assertEquals(listOf(0.25f, 0.5f, 0.75f), result)
    }

    @Test
    public fun `chapter markers are sorted and deduplicated`() {
        val unsortedMarkers = listOf(0.5f, 0.25f, 0.75f, 0.5f, 0.25f)

        val result = sanitizeChapterMarkersFractions(unsortedMarkers)

        assertEquals(3, result.size)
        assertEquals(listOf(0.25f, 0.5f, 0.75f), result)
    }

    @Test
    public fun `chapter markers at boundaries are excluded`() {
        val markers = listOf(0f, 0.5f, 1f)

        val result = sanitizeChapterMarkersFractions(markers)

        // 0f and 1f should be excluded (marker at exact start or end is not useful)
        assertEquals(1, result.size)
        assertEquals(0.5f, result[0], 0.0001f)
    }

    // ==================== Value Range Normalization Tests ====================

    @Test
    public fun `normalizeValueRange handles NaN start`() {
        val range = normalizeValueRange(Float.NaN..1f)

        assertEquals(0f, range.start, 0.0001f)
        assertEquals(1f, range.endInclusive, 0.0001f)
    }

    @Test
    public fun `normalizeValueRange handles NaN end`() {
        val range = normalizeValueRange(0f..Float.NaN)

        assertEquals(0f, range.start, 0.0001f)
        assertEquals(1f, range.endInclusive, 0.0001f)
    }

    @Test
    public fun `normalizeValueRange handles inverted range`() {
        val range = normalizeValueRange(1f..0f)

        // Inverted range should fall back to 0f..1f
        assertEquals(0f, range.start, 0.0001f)
        assertEquals(1f, range.endInclusive, 0.0001f)
    }

    @Test
    public fun `normalizeValueRange handles infinity`() {
        val range = normalizeValueRange(Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY)

        assertEquals(0f, range.start, 0.0001f)
        assertEquals(1f, range.endInclusive, 0.0001f)
    }

    @Test
    public fun `normalizeValueRange preserves valid range`() {
        val range = normalizeValueRange(100f..500f)

        assertEquals(100f, range.start, 0.0001f)
        assertEquals(500f, range.endInclusive, 0.0001f)
    }

    // ==================== Edge Cases ====================

    @Test
    public fun `sanitizeChapterMarkers handles empty list`() {
        val result = sanitizeChapterMarkersFractions(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    public fun `sanitizeChapterMarkers handles all invalid markers`() {
        val invalidMarkers = listOf(-1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)

        val result = sanitizeChapterMarkersFractions(invalidMarkers)

        assertTrue(result.isEmpty())
    }

    @Test
    public fun `normalizeValueRange handles equal start and end`() {
        val range = normalizeValueRange(50f..50f)

        // Equal values should fall back to 0f..1f
        assertEquals(0f, range.start, 0.0001f)
        assertEquals(1f, range.endInclusive, 0.0001f)
    }
}
