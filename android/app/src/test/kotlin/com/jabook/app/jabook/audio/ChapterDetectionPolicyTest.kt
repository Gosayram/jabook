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
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectionPolicyTest {
    @Test
    fun `detectCandidates returns empty when there is no long silence`() {
        val values = List(100) { -20f }

        val result = ChapterDetectionPolicy.detectCandidates(values)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectCandidates emits boundary when long silence ends`() {
        val values =
            buildList {
                repeat(10) { add(-18f) } // speech
                repeat(25) { add(-45f) } // silence 2500ms
                repeat(10) { add(-16f) } // speech resumes
            }

        val result = ChapterDetectionPolicy.detectCandidates(values)

        assertEquals(1, result.size)
        assertEquals(3_500L, result.first().startMs)
        assertTrue(result.first().confidence >= 1f)
    }

    @Test
    fun `detectCandidates keeps confidence below one for short-just-over-threshold silence`() {
        val values =
            buildList {
                repeat(10) { add(-20f) }
                repeat(20) { add(-41f) } // exactly threshold duration
                repeat(5) { add(-15f) }
            }

        val result =
            ChapterDetectionPolicy.detectCandidates(
                rmsDbValues = values,
                minSilenceMs = 2_500L,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectCandidates emits multiple boundaries for separate silence blocks`() {
        val values =
            buildList {
                repeat(8) { add(-17f) }
                repeat(21) { add(-42f) }
                repeat(8) { add(-17f) }
                repeat(24) { add(-43f) }
                repeat(8) { add(-17f) }
            }

        val result = ChapterDetectionPolicy.detectCandidates(values)

        assertEquals(2, result.size)
        assertEquals(2_900L, result[0].startMs)
        assertEquals(6_100L, result[1].startMs)
    }
}
