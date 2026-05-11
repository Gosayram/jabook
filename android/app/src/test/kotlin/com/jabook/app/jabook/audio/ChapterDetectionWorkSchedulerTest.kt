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

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.data.worker.ChapterDetectionWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ChapterDetectionWorkSchedulerTest {
    @Test
    fun `enqueue schedules unique work with keep policy and expected payload`() {
        val workManager = mock<WorkManager>()
        val scheduler = ChapterDetectionWorkScheduler(workManager = workManager, nowMsProvider = { 1000L })
        val requestCaptor = argumentCaptor<OneTimeWorkRequest>()

        scheduler.enqueue(
            bookId = "book-1",
            filePath = "/books/book1/chapter.mp3",
            fileIndex = 2,
            durationMs = 123_000L,
            fileLastModifiedMs = 777L,
        )

        verify(workManager).enqueueUniqueWork(
            eq("${ChapterDetectionWorker.WORK_NAME_PREFIX}_book-1_2"),
            eq(ExistingWorkPolicy.KEEP),
            requestCaptor.capture(),
        )
        val input = requestCaptor.firstValue.inputData
        assertEquals("book-1", input.getString(ChapterDetectionWorker.KEY_BOOK_ID))
        assertEquals("/books/book1/chapter.mp3", input.getString(ChapterDetectionWorker.KEY_FILE_PATH))
        assertEquals(2, input.getInt(ChapterDetectionWorker.KEY_FILE_INDEX, -1))
        assertEquals(123_000L, input.getLong(ChapterDetectionWorker.KEY_DURATION_MS, -1L))
        assertEquals(777L, input.getLong(ChapterDetectionWorker.KEY_FILE_LAST_MODIFIED_MS, -1L))
    }

    @Test
    fun `enqueue ignores invalid request`() {
        val workManager = mock<WorkManager>()
        val scheduler = ChapterDetectionWorkScheduler(workManager = workManager)

        scheduler.enqueue(
            bookId = "",
            filePath = "/books/book1/chapter.mp3",
            fileIndex = 0,
            durationMs = 50_000L,
            fileLastModifiedMs = 1L,
        )
        scheduler.enqueue(
            bookId = "book-1",
            filePath = "",
            fileIndex = 0,
            durationMs = 50_000L,
            fileLastModifiedMs = 1L,
        )
        scheduler.enqueue(
            bookId = "book-1",
            filePath = "/books/book1/chapter.mp3",
            fileIndex = 0,
            durationMs = 0L,
            fileLastModifiedMs = 1L,
        )

        verify(workManager, never()).enqueueUniqueWork(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `enqueue skips duplicate signature within debounce window`() {
        val workManager = mock<WorkManager>()
        var nowMs = 1_000L
        val scheduler = ChapterDetectionWorkScheduler(workManager = workManager, nowMsProvider = { nowMs })

        scheduler.enqueue(
            bookId = "book-1",
            filePath = "/books/book1/chapter.mp3",
            fileIndex = 1,
            durationMs = 10_000L,
            fileLastModifiedMs = 5L,
        )
        nowMs += ChapterDetectionEnqueueGuardPolicy.SAME_SIGNATURE_DEBOUNCE_MS - 1L
        scheduler.enqueue(
            bookId = "book-1",
            filePath = "/books/book1/chapter.mp3",
            fileIndex = 1,
            durationMs = 10_000L,
            fileLastModifiedMs = 5L,
        )

        val requestCaptor = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(
            eq("${ChapterDetectionWorker.WORK_NAME_PREFIX}_book-1_1"),
            eq(ExistingWorkPolicy.KEEP),
            requestCaptor.capture(),
        )
        assertEquals(1, requestCaptor.allValues.size)
    }
}
