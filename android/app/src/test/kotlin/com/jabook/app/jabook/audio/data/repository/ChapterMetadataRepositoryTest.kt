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

package com.jabook.app.jabook.audio.data.repository

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.local.dao.ChapterMetadataDao
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ChapterMetadataRepositoryTest {
    @Test
    fun `saveChapters uses atomic replace for a single book`() =
        runTest {
            val dao = mock<ChapterMetadataDao>()
            val repository = ChapterMetadataRepository(dao)
            val chapters =
                listOf(
                    chapter(id = "c1", bookId = "book-1", fileIndex = 0),
                    chapter(id = "c2", bookId = "book-1", fileIndex = 1),
                )

            val result = repository.saveChapters(chapters)

            assertTrue(result is Result.Success)
            verify(dao).replaceChaptersForBook(eq("book-1"), eq(chapters))
            verify(dao, never()).upsertChapters(any())
        }

    @Test
    fun `saveChapters with empty input is no-op success`() =
        runTest {
            val dao = mock<ChapterMetadataDao>()
            val repository = ChapterMetadataRepository(dao)

            val result = repository.saveChapters(emptyList())

            assertTrue(result is Result.Success)
            verify(dao, never()).replaceChaptersForBook(any(), any())
        }

    @Test
    fun `saveChapters returns error when chapters contain multiple book ids`() =
        runTest {
            val dao = mock<ChapterMetadataDao>()
            val repository = ChapterMetadataRepository(dao)
            val chapters =
                listOf(
                    chapter(id = "c1", bookId = "book-1", fileIndex = 0),
                    chapter(id = "c2", bookId = "book-2", fileIndex = 1),
                )

            val result = repository.saveChapters(chapters)

            assertTrue(result is Result.Error)
            verify(dao, never()).replaceChaptersForBook(any(), any())
        }

    private fun chapter(
        id: String,
        bookId: String,
        fileIndex: Int,
    ): ChapterMetadataEntity =
        ChapterMetadataEntity(
            id = id,
            bookId = bookId,
            fileIndex = fileIndex,
            title = "Chapter $fileIndex",
            filePath = "/tmp/$id.mp3",
            startTime = 0,
            endTime = null,
            duration = 1000L,
            lastUpdated = 1L,
        )
}
