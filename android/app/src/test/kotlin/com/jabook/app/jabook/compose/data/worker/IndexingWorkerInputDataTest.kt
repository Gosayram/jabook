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

package com.jabook.app.jabook.compose.data.worker

import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexingWorkerInputDataTest {
    @Test
    fun `parseForumIds returns sanitized comma separated forum ids`() {
        val parsed = IndexingWorker.parseForumIds(" 574, 1036 ,,400 ")

        assertEquals("574,1036,400", parsed)
    }

    @Test
    fun `parseForumIds falls back when value is absent`() {
        val parsed = IndexingWorker.parseForumIds(null)

        assertEquals(RutrackerApi.AUDIOBOOKS_FORUM_IDS, parsed)
    }

    @Test
    fun `parseForumIds falls back when value is invalid`() {
        val parsed = IndexingWorker.parseForumIds("574,not-a-forum,400")

        assertEquals(RutrackerApi.AUDIOBOOKS_FORUM_IDS, parsed)
    }

    @Test
    fun `parseForumIds falls back when value is blank`() {
        val parsed = IndexingWorker.parseForumIds(" , , ")

        assertEquals(RutrackerApi.AUDIOBOOKS_FORUM_IDS, parsed)
    }
}
