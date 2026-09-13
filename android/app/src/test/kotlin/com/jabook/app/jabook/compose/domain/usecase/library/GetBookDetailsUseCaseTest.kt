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

package com.jabook.app.jabook.compose.domain.usecase.library

import com.jabook.app.jabook.compose.data.model.DownloadStatus
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds

class GetBookDetailsUseCaseTest {
    private val booksRepository: BooksRepository = mock()
    private val useCase = GetBookDetailsUseCase(booksRepository)

    private val testBook =
        Book(
            id = "book-1",
            title = "Test Audiobook",
            author = "Author",
            coverUrl = null,
            description = "Description",
            totalDuration = 3600000.milliseconds,
            currentPosition = 1800000.milliseconds,
            progress = 0.5f,
            currentChapterIndex = 3,
            downloadStatus = DownloadStatus.DOWNLOADED,
            downloadProgress = 1.0f,
            localPath = "/data/test.mp3",
            addedDate = 1000L,
            lastPlayedDate = 2000L,
            isFavorite = true,
            sourceUrl = "magnet:?xt=urn:test",
        )

    @Test
    fun `invoke returns book from repository`() =
        runTest {
            whenever(booksRepository.getBook("book-1")).thenReturn(flowOf(testBook))

            val result = useCase("book-1").first()

            assertEquals("book-1", result?.id)
            assertEquals("Test Audiobook", result?.title)
            assertEquals("Author", result?.author)
        }

    @Test
    fun `invoke returns null when book not found`() =
        runTest {
            whenever(booksRepository.getBook("nonexistent")).thenReturn(flowOf(null))

            val result = useCase("nonexistent").first()

            assertNull(result)
        }

    @Test
    fun `invoke passes bookId to repository`() =
        runTest {
            whenever(booksRepository.getBook("specific-id")).thenReturn(flowOf(testBook))

            useCase("specific-id").first()

            org.mockito.kotlin
                .verify(booksRepository)
                .getBook("specific-id")
        }

    @Test
    fun `invoke returns empty list when no books exist`() =
        runTest {
            whenever(booksRepository.getBook("empty")).thenReturn(flowOf(null))

            val results = mutableListOf<Book?>()
            useCase("empty").collect { results.add(it) }

            assertEquals(1, results.size)
            assertNull(results[0])
        }
}
