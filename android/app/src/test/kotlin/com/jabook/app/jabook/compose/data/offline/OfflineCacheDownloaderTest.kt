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

package com.jabook.app.jabook.compose.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the offline slice's deterministic logic (no media3/Android objects). */
class OfflineCacheDownloaderTest {
    @Test
    fun downloadIdIsDeterministicAndUniquePerChapter() {
        assertEquals("book1#0", OfflineCacheDownloader.downloadId("book1", 0))
        assertEquals("book1#2", OfflineCacheDownloader.downloadId("book1", 2))
        assertNotEquals(
            OfflineCacheDownloader.downloadId("book1", 1),
            OfflineCacheDownloader.downloadId("book1", 2),
        )
        assertNotEquals(
            OfflineCacheDownloader.downloadId("book1", 1),
            OfflineCacheDownloader.downloadId("book2", 1),
        )
    }

    @Test
    fun urlValidationAcceptsHttpOnly() {
        assertTrue(OfflineCacheDownloader.validUrl("https://cdn.example/a.mp3"))
        assertTrue(OfflineCacheDownloader.validUrl("http://cdn.example/a.mp3"))
        assertFalse(OfflineCacheDownloader.validUrl("file:///sdcard/a.mp3"))
        assertFalse(OfflineCacheDownloader.validUrl("magnet:?xt=1"))
        assertFalse(OfflineCacheDownloader.validUrl(""))
        assertFalse(OfflineCacheDownloader.validBookId(" "))
        assertTrue(OfflineCacheDownloader.validBookId("42"))
    }

    @Test
    fun aggregateCountsTerminalStatesAndWaitsForAll() {
        // Mirrors Download.STATE_*: 0 queued, 2 downloading, 3 completed, 4 failed.
        val mixed = OfflineCacheDownloader.aggregate(listOf(3, 4, 2, 0))
        assertEquals(1, mixed.downloaded)
        assertEquals(1, mixed.failed)
        assertEquals(4, mixed.total)
        assertFalse("still downloading -> not finished", mixed.finished)

        val done = OfflineCacheDownloader.aggregate(listOf(3, 4, 3))
        assertTrue(done.finished)
        assertEquals(2, done.downloaded)

        val missing = OfflineCacheDownloader.aggregate(listOf(3, null))
        assertFalse("chapter never enqueued -> not finished", missing.finished)
    }
}
