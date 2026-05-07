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

package com.jabook.app.jabook.compose.data.indexing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexingProgressTest {
    @Test
    fun `in-progress percentage is finite and within bounds`() {
        val progress =
            IndexingProgress
                .InProgress(
                    currentForum = "forum",
                    currentForumIndex = 1,
                    totalForums = 5,
                    currentPage = 10,
                    topicsIndexed = 100,
                ).progress

        assertFalse(progress.isNaN())
        assertFalse(progress.isInfinite())
        assertTrue(progress in 0f..1f)
    }

    @Test
    fun `progress equals zero when totalForums is zero`() {
        val progress =
            IndexingProgress
                .InProgress(
                    currentForum = "forum",
                    currentForumIndex = 0,
                    totalForums = 0,
                    currentPage = 1,
                    topicsIndexed = 0,
                ).progress

        assertTrue(progress == 0f)
    }
}
