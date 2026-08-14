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

package com.jabook.app.jabook.compose.feature.player.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSeekAvailabilityPolicyTest {
    @Test
    fun `large playlist chapter is unavailable until its media item is added`() {
        assertFalse(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 40, mediaItemCount = 1))
        assertTrue(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 40, mediaItemCount = 41))
    }

    @Test
    fun `negative index returns false`() {
        assertFalse(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = -1, mediaItemCount = 5))
    }

    @Test
    fun `zero media items returns false for any index`() {
        assertFalse(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 0, mediaItemCount = 0))
        assertFalse(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 1, mediaItemCount = 0))
    }

    @Test
    fun `first valid index 0 returns true`() {
        assertTrue(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 0, mediaItemCount = 3))
    }

    @Test
    fun `last valid index returns true`() {
        assertTrue(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 2, mediaItemCount = 3))
    }

    @Test
    fun `index equal to mediaItemCount returns false`() {
        assertFalse(ChapterSeekAvailabilityPolicy.isAvailable(chapterIndex = 3, mediaItemCount = 3))
    }
}
