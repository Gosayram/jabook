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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class PlayerViewModelAutoplayTest {
    @Test
    fun `evaluateSeriesAutoplayDecision returns shouldTrigger true when at end with 95 percent played`() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = true,
                positionMs = 95_000L,
                durationMs = 100_000L,
                hasTriggeredSeriesAutoplay = false,
            )
        assertTrue(decision.shouldTriggerAutoplay)
        assertFalse(decision.shouldResetAutoplay)
    }

    @Test
    fun `evaluateSeriesAutoplayDecision returns false when not at last chapter`() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = false,
                isPlaying = true,
                positionMs = 95_000L,
                durationMs = 100_000L,
                hasTriggeredSeriesAutoplay = false,
            )
        assertFalse(decision.shouldTriggerAutoplay)
        assertFalse(decision.shouldResetAutoplay)
    }

    @Test
    fun `evaluateSeriesAutoplayDecision returns shouldReset true when not playing near end`() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = false,
                positionMs = 50_000L,
                durationMs = 100_000L,
                hasTriggeredSeriesAutoplay = false,
            )
        assertFalse(decision.shouldTriggerAutoplay)
        assertTrue(decision.shouldResetAutoplay)
    }

    @Test
    fun `evaluateSeriesAutoplayDecision returns shouldReset when already triggered`() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = true,
                positionMs = 95_000L,
                durationMs = 100_000L,
                hasTriggeredSeriesAutoplay = true,
            )
        assertFalse(decision.shouldTriggerAutoplay)
        assertTrue(decision.shouldResetAutoplay)
    }

    @Test
    fun `evaluateSeriesAutoplayDecision returns shouldReset when not at 95 percent threshold`() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = true,
                positionMs = 80_000L,
                durationMs = 100_000L,
                hasTriggeredSeriesAutoplay = false,
            )
        assertFalse(decision.shouldTriggerAutoplay)
        assertTrue(decision.shouldResetAutoplay)
    }

    @Test
    fun `evaluateSeriesAutoplayDecision handles zero duration`() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = true,
                positionMs = 0L,
                durationMs = 0L,
                hasTriggeredSeriesAutoplay = false,
            )
        assertFalse(decision.shouldTriggerAutoplay)
        assertTrue(decision.shouldResetAutoplay)
    }
}

private data class AutoplayDecision(
    val shouldTriggerAutoplay: Boolean,
    val shouldResetAutoplay: Boolean,
)

private fun evaluateSeriesAutoplayDecision(
    isLastChapter: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasTriggeredSeriesAutoplay: Boolean,
): AutoplayDecision {
    val isNearEnd =
        if (durationMs > 0) {
            positionMs.toDouble() / durationMs >= 0.95
        } else {
            positionMs > 0
        }

    return when {
        !isLastChapter -> AutoplayDecision(false, false)
        !isPlaying -> AutoplayDecision(false, true)
        hasTriggeredSeriesAutoplay -> AutoplayDecision(false, true)
        isNearEnd -> AutoplayDecision(true, false)
        else -> AutoplayDecision(false, true)
    }
}
