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

class PlayerViewModelAutoplayPolicyTest {
    @Test
    fun evaluateSeriesAutoplayDecision_triggersOnlyForEndedLastChapterAndNotPlaying() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = false,
                positionMs = 9_300L,
                durationMs = 10_000L,
                hasTriggeredSeriesAutoplay = false,
            )

        assertTrue(decision.shouldTriggerAutoplay)
        assertFalse(decision.shouldResetAutoplay)
    }

    @Test
    fun evaluateSeriesAutoplayDecision_doesNotTriggerWhenAlreadyTriggered() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = false,
                positionMs = 9_900L,
                durationMs = 10_000L,
                hasTriggeredSeriesAutoplay = true,
            )

        assertFalse(decision.shouldTriggerAutoplay)
        assertFalse(decision.shouldResetAutoplay)
    }

    @Test
    fun evaluateSeriesAutoplayDecision_resetsWhenPlaybackResumesOrNotLastChapter() {
        val resumed =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = true,
                positionMs = 9_900L,
                durationMs = 10_000L,
                hasTriggeredSeriesAutoplay = false,
            )
        val notLastChapter =
            evaluateSeriesAutoplayDecision(
                isLastChapter = false,
                isPlaying = false,
                positionMs = 9_900L,
                durationMs = 10_000L,
                hasTriggeredSeriesAutoplay = false,
            )

        assertTrue(resumed.shouldResetAutoplay)
        assertTrue(notLastChapter.shouldResetAutoplay)
        assertFalse(resumed.shouldTriggerAutoplay)
        assertFalse(notLastChapter.shouldTriggerAutoplay)
    }

    @Test
    fun evaluateSeriesAutoplayDecision_resetsWhenUserSeeksBackAfterAutoplayTriggered() {
        val decision =
            evaluateSeriesAutoplayDecision(
                isLastChapter = true,
                isPlaying = false,
                positionMs = 4_000L,
                durationMs = 10_000L,
                hasTriggeredSeriesAutoplay = true,
            )

        assertTrue(decision.shouldResetAutoplay)
        assertFalse(decision.shouldTriggerAutoplay)
    }

    @Test
    fun resolveDeleteBookmarkFailureReason_mapsFailureToUserFacingMessage() {
        val failed = resolveDeleteBookmarkFailureReason(Result.failure(IllegalStateException("db")))
        val success = resolveDeleteBookmarkFailureReason(Result.success(Unit))

        assertEquals("Failed to delete bookmark", failed)
        assertEquals(null, success)
    }
}
