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

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterDetectionEnqueueGuardPolicyTest {
    private val debounceMs = ChapterDetectionEnqueueGuardPolicy.SAME_SIGNATURE_DEBOUNCE_MS
    private val signature =
        ChapterDetectionEnqueueGuardPolicy.FileSignature(
            filePath = "/test/book.mp3",
            fileIndex = 0,
            durationMs = 60000L,
            lastModifiedMs = 1000L,
        )

    @Test
    fun `returns false when previous is null`() {
        assertFalse(
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = null,
                next = signature,
                nowMs = 0L,
            ),
        )
    }

    @Test
    fun `returns false when signatures differ`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature =
                    ChapterDetectionEnqueueGuardPolicy.FileSignature(
                        filePath = "/other/book.mp3",
                        fileIndex = 0,
                        durationMs = 60000L,
                        lastModifiedMs = 1000L,
                    ),
                enqueuedAtMs = 0L,
            )
        assertFalse(
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = 0L,
            ),
        )
    }

    @Test
    fun `returns true when same signature within debounce window`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature = signature,
                enqueuedAtMs = 0L,
            )
        assertTrue(
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = debounceMs - 1,
            ),
        )
    }

    @Test
    fun `returns false when elapsed equals debounce threshold`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature = signature,
                enqueuedAtMs = 0L,
            )
        assertFalse(
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = debounceMs,
            ),
        )
    }

    @Test
    fun `returns false when elapsed exceeds debounce threshold`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature = signature,
                enqueuedAtMs = 0L,
            )
        assertFalse(
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = debounceMs + 1,
            ),
        )
    }

    @Test
    fun `coerces negative elapsed to zero and skips`() {
        val previous =
            ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
                signature = signature,
                enqueuedAtMs = 10000L,
            )
        // When nowMs < previous.enqueuedAtMs, elapsed would be negative, coerced to 0
        assertTrue(
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = previous,
                next = signature,
                nowMs = 0L,
            ),
        )
    }
}
