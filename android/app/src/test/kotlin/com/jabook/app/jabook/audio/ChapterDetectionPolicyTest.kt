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
        assertTrue(result.first().confidence > 0.7f)
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

    @Test
    fun `resolveAdaptiveSilenceThresholdDb returns bounded adaptive threshold`() {
        val values =
            buildList {
                repeat(40) { add(-55f) } // floor
                repeat(60) { add(-18f) } // speech
            }

        val threshold = ChapterDetectionPolicy.resolveAdaptiveSilenceThresholdDb(values)

        assertTrue(threshold in ChapterDetectionPolicy.MIN_ADAPTIVE_THRESHOLD_DB..ChapterDetectionPolicy.MAX_ADAPTIVE_THRESHOLD_DB)
        assertTrue(threshold > -55f)
    }

    @Test
    fun `detectCandidates uses adaptive threshold when explicit threshold is null`() {
        val values =
            buildList {
                repeat(10) { add(-20f) }
                repeat(25) { add(-49f) }
                repeat(10) { add(-19f) }
            }

        val result =
            ChapterDetectionPolicy.detectCandidates(
                rmsDbValues = values,
                silenceThresholdDb = null,
            )

        assertEquals(1, result.size)
    }

    @Test
    fun `detectCandidates treats values equal to threshold as silence`() {
        val values =
            buildList {
                repeat(8) { add(-18f) }
                repeat(20) { add(ChapterDetectionPolicy.DEFAULT_SILENCE_THRESHOLD_DB) }
                add(-10f)
            }

        val result =
            ChapterDetectionPolicy.detectCandidates(
                rmsDbValues = values,
                windowStepMs = ChapterDetectionPolicy.DEFAULT_WINDOW_STEP_MS,
                silenceThresholdDb = ChapterDetectionPolicy.DEFAULT_SILENCE_THRESHOLD_DB,
                minSilenceMs = 2_000L,
            )

        assertEquals(1, result.size)
        assertEquals(2_800L, result.first().startMs)
    }

    @Test
    fun `detectCandidates requires silence duration to be at least minimum`() {
        val tooShort =
            buildList {
                repeat(5) { add(-15f) }
                repeat(19) { add(-45f) } // 1900ms
                repeat(5) { add(-15f) }
            }
        val exactMin =
            buildList {
                repeat(5) { add(-15f) }
                repeat(20) { add(-45f) } // 2000ms
                repeat(5) { add(-15f) }
            }

        val resultTooShort = ChapterDetectionPolicy.detectCandidates(tooShort, minSilenceMs = 2_000L)
        val resultExactMin = ChapterDetectionPolicy.detectCandidates(exactMin, minSilenceMs = 2_000L)

        assertTrue(resultTooShort.isEmpty())
        assertEquals(1, resultExactMin.size)
    }

    @Test
    fun `detectCandidates handles empty input and invalid timing params`() {
        assertTrue(ChapterDetectionPolicy.detectCandidates(emptyList()).isEmpty())
        val zeroWindowStepResult =
            ChapterDetectionPolicy.detectCandidates(
                rmsDbValues = listOf(-40f, -20f),
                windowStepMs = 0L,
            )
        val zeroMinSilenceResult =
            ChapterDetectionPolicy.detectCandidates(
                rmsDbValues = listOf(-40f, -20f),
                minSilenceMs = 0L,
            )
        assertTrue(
            zeroWindowStepResult.isEmpty(),
        )
        assertTrue(
            zeroMinSilenceResult.isEmpty(),
        )
    }

    @Test
    fun `detectCandidates caps confidence at max confidence`() {
        val values =
            buildList {
                repeat(10) { add(-15f) }
                repeat(60) { add(-90f) } // very long and very deep silence
                repeat(10) { add(-12f) }
            }

        val result =
            ChapterDetectionPolicy.detectCandidates(
                rmsDbValues = values,
                minSilenceMs = 2_000L,
                silenceThresholdDb = -40f,
            )

        assertEquals(1, result.size)
        assertEquals(1f, result.first().confidence)
    }
}
