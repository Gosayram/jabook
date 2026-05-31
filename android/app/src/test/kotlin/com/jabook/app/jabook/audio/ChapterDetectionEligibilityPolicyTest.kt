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

class ChapterDetectionEligibilityPolicyTest {
    private val minDuration = ChapterDetectionEligibilityPolicy.MIN_ELIGIBLE_DURATION_MS

    @Test
    fun `returns true when all conditions met - exactly 10 minutes`() {
        assertTrue(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "/valid/path.mp3",
                durationMs = minDuration,
            ),
        )
    }

    @Test
    fun `returns false when duration below threshold`() {
        assertFalse(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "/valid/path.mp3",
                durationMs = minDuration - 1,
            ),
        )
    }

    @Test
    fun `returns false when chapterCount is zero`() {
        assertFalse(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 0,
                filePath = "/valid/path.mp3",
                durationMs = minDuration,
            ),
        )
    }

    @Test
    fun `returns false when chapterCount greater than one`() {
        assertFalse(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 2,
                filePath = "/valid/path.mp3",
                durationMs = minDuration,
            ),
        )
    }

    @Test
    fun `returns false when filePath is blank`() {
        assertFalse(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "",
                durationMs = minDuration,
            ),
        )
    }

    @Test
    fun `returns false when filePath is blank whitespace`() {
        assertFalse(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "   ",
                durationMs = minDuration,
            ),
        )
    }

    @Test
    fun `returns false when filePath is null-like representation`() {
        assertFalse(
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "\n\t",
                durationMs = minDuration,
            ),
        )
    }
}
