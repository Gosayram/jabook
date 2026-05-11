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

class ChapterDetectionEligibilityPolicyTest {
    @Test
    fun `shouldEnqueueSingleFileDetection returns true for long single-file books`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "/books/one-file-book.mp3",
                durationMs = 45L * 60L * 1000L,
            )

        assertTrue(result)
    }

    @Test
    fun `shouldEnqueueSingleFileDetection returns false for multi-file books`() {
        val result =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 8,
                filePath = "/books/chapter-01.mp3",
                durationMs = 45L * 60L * 1000L,
            )

        assertFalse(result)
    }

    @Test
    fun `shouldEnqueueSingleFileDetection returns false for blank path or short duration`() {
        val blankPath =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "  ",
                durationMs = 45L * 60L * 1000L,
            )
        val shortDuration =
            ChapterDetectionEligibilityPolicy.shouldEnqueueSingleFileDetection(
                chapterCount = 1,
                filePath = "/books/short.mp3",
                durationMs = 5L * 60L * 1000L,
            )

        assertFalse(blankPath)
        assertFalse(shortDuration)
    }
}
