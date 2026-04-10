package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SquigglySliderSanitizerTest {
    @Test
    fun `sanitizeChapterMarkersFractions filters NaN infinity and out-of-bounds values`() {
        val result =
            sanitizeChapterMarkersFractions(
                listOf(
                    Float.NaN,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    -0.2f,
                    0f,
                    0.1f,
                    1f,
                    1.5f,
                    0.9f,
                ),
            )

        assertEquals(listOf(0.1f, 0.9f), result)
    }

    @Test
    fun `sanitizeChapterMarkersFractions deduplicates and sorts ascending`() {
        val result = sanitizeChapterMarkersFractions(listOf(0.8f, 0.2f, 0.2f, 0.6f, 0.8f))

        assertEquals(listOf(0.2f, 0.6f, 0.8f), result)
    }
}
