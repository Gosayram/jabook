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

class ChapterDetectionSamplingPolicyTest {
    @Test
    fun `buildPlan enforces minimum windows for short tracks`() {
        val plan =
            ChapterDetectionSamplingPolicy.buildPlan(
                durationMs = 10_000L,
                requestedWindowStepMs = 1_000L,
            )

        assertEquals(ChapterDetectionSamplingPolicy.MIN_WINDOWS, plan.windowsToProcess)
        assertTrue(plan.effectiveWindowStepMs >= ChapterDetectionSamplingPolicy.MIN_WINDOW_STEP_MS)
    }

    @Test
    fun `buildPlan caps windows for very long tracks`() {
        val plan =
            ChapterDetectionSamplingPolicy.buildPlan(
                durationMs = 24L * 60L * 60L * 1000L, // 24h
                requestedWindowStepMs = 100L,
            )

        assertEquals(ChapterDetectionSamplingPolicy.MAX_WINDOWS, plan.windowsToProcess)
        assertTrue(plan.effectiveWindowStepMs >= ChapterDetectionSamplingPolicy.MIN_WINDOW_STEP_MS)
    }

    @Test
    fun `buildPlan keeps requested density for normal duration`() {
        val plan =
            ChapterDetectionSamplingPolicy.buildPlan(
                durationMs = 60L * 60L * 1000L, // 1h
                requestedWindowStepMs = 2_000L,
            )

        assertEquals(1_800, plan.windowsToProcess)
        assertTrue(plan.effectiveWindowStepMs in 1_900L..2_100L)
    }
}
