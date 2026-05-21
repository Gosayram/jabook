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

public class ChapterDetectionEnqueueGuardPolicyTest {
    private val debounceMs = ChapterDetectionEnqueueGuardPolicy.SAME_SIGNATURE_DEBOUNCE_MS

    private fun makeSignature(
        path: String = "/path/to/file.mp3",
        index: Int = 0,
        duration: Long = 60_000L,
        modified: Long = 1_000L,
    ): ChapterDetectionEnqueueGuardPolicy.FileSignature =
        ChapterDetectionEnqueueGuardPolicy.FileSignature(
            filePath = path,
            fileIndex = index,
            durationMs = duration,
            lastModifiedMs = modified,
        )

    private fun makeRecord(
        signature: ChapterDetectionEnqueueGuardPolicy.FileSignature = makeSignature(),
        enqueuedAt: Long = 0L,
    ): ChapterDetectionEnqueueGuardPolicy.EnqueueRecord =
        ChapterDetectionEnqueueGuardPolicy.EnqueueRecord(
            signature = signature,
            enqueuedAtMs = enqueuedAt,
        )

    @Test
    public fun `shouldSkipEnqueue returns false when previous is null`() {
        val result =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = null,
                next = makeSignature(),
                nowMs = 1000L,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldSkipEnqueue returns false when signatures differ`() {
        val prevRecord = makeRecord(makeSignature("/path/file1.mp3"))
        val result =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = prevRecord,
                next = makeSignature("/path/file2.mp3"),
                nowMs = 1000L,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldSkipEnqueue returns true when elapsed is just under debounce`() {
        val prevRecord = makeRecord(enqueuedAt = 0L)
        val justUnder = debounceMs - 1
        val result =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = prevRecord,
                next = prevRecord.signature,
                nowMs = justUnder,
            )
        assertTrue(result)
    }

    @Test
    public fun `shouldSkipEnqueue returns false when elapsed equals debounce`() {
        val prevRecord = makeRecord(enqueuedAt = 0L)
        val result =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = prevRecord,
                next = prevRecord.signature,
                nowMs = debounceMs,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldSkipEnqueue returns false when elapsed is over debounce`() {
        val prevRecord = makeRecord(enqueuedAt = 0L)
        val overDebounce = debounceMs + 1
        val result =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = prevRecord,
                next = prevRecord.signature,
                nowMs = overDebounce,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldSkipEnqueue handles negative elapsed by coercing to zero`() {
        val prevRecord = makeRecord(enqueuedAt = 1000L)
        val result =
            ChapterDetectionEnqueueGuardPolicy.shouldSkipEnqueue(
                previous = prevRecord,
                next = prevRecord.signature,
                nowMs = 0L,
            )
        assertTrue(result)
    }
}
