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

public class ChapterDetectionEligibilityPolicyTest {
    private val minDuration = ChapterDetectionEligibilityPolicy.MIN_ELIGIBLE_DURATION_MS

    @Test
    public fun `shouldEnqueueSingleFileDetection returns true for valid input`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "/path/to/book.mp3",
                durationMs = minDuration,
            )
        assertTrue(result)
    }

    @Test
    public fun `shouldEnqueueSingleFileDetection returns false when duration below threshold`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "/path/to/book.mp3",
                durationMs = minDuration - 1,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldEnqueueSingleFileDetection returns false for blank filePath`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "",
                durationMs = minDuration,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldEnqueueSingleFileDetection returns false for whitespace filePath`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "   ",
                durationMs = minDuration,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldEnqueueSingleFileDetection returns false when chapterCount not 1`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 0,
                filePath = "/path/to/book.mp3",
                durationMs = minDuration,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldEnqueueSingleFileDetection returns false when chapterCount greater than 1`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 5,
                filePath = "/path/to/book.mp3",
                durationMs = minDuration,
            )
        assertFalse(result)
    }

    @Test
    public fun `shouldEnqueueSingleFileDetection returns false for all invalid conditions`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 0,
                filePath = "",
                durationMs = 0L,
            )
        assertFalse(result)
    }
}
