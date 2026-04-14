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
