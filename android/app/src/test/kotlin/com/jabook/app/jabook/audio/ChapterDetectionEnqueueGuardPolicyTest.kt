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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectionEnqueueGuardPolicyTest {
    private val signature =
        ChapterDetectionEnqueueGuardPolicy.FileSignature(
            filePath = "/books/a.mp3",
            fileIndex = 0,
            durationMs = 3_600_000L,
            lastModifiedMs = 1_000L,
        )

    @Test
    fun `shouldSkipEnqueue is false when there is no previous record`() {
        val shouldSkip =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = null,
                next = signature,
                nowMs = 10_000L,
            )

        assertFalse(shouldSkip)
    }

    @Test
    fun `shouldSkipEnqueue is true for same signature within debounce window`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature = signature,
                enqueuedAtMs = 100_000L,
            )

        val shouldSkip =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = 100_000L + ChapterDetectionEnqueueGuardPolicy.SAME_SIGNATURE_DEBOUNCE_MS - 1L,
            )

        assertTrue(shouldSkip)
    }

    @Test
    fun `shouldSkipEnqueue is false when signature changed or debounce elapsed`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature = signature,
                enqueuedAtMs = 100_000L,
            )
        val changedSignature =
            signature.copy(lastModifiedMs = signature.lastModifiedMs + 500L)

        val bySignature =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = changedSignature,
                nowMs = 100_001L,
            )
        val byElapsed =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = 100_000L + ChapterDetectionEnqueueGuardPolicy.SAME_SIGNATURE_DEBOUNCE_MS,
            )

        assertFalse(bySignature)
        assertFalse(byElapsed)
    }
}
