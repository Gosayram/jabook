// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// you may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.CoverLookupCandidate
import com.jabook.app.jabook.compose.data.remote.cover.RemoteCoverProvider
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverEnrichmentWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val booksDao: BooksDao = mock()
    private val userPreferencesRepository: UserPreferencesRepository = mock()
    private val coverProvider: RemoteCoverProvider = mock()
    private val loggerFactory: LoggerFactory =
        object : LoggerFactory {
            override fun get(tag: String): Logger = NoopLogger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoopLogger
        }

    @Test
    fun `found cover is persisted and book marked attempted`() =
        runTest {
            whenever(booksDao.getBooksWithoutCovers()).thenReturn(listOf(CoverLookupCandidate("b1", "Война и мир", "Лев Толстой")))
            whenever(userPreferencesRepository.getAttemptedCoverLookupIds()).thenReturn(emptySet())
            whenever(coverProvider.lookup(any(), any())).thenReturn("https://covers.openlibrary.org/b/id/1-L.jpg")

            val result = buildWorker().doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            verify(booksDao).updateCoverUrl("b1", "https://covers.openlibrary.org/b/id/1-L.jpg")
            verify(userPreferencesRepository).markCoverLookupAttempted(listOf("b1"))
        }

    @Test
    fun `missed cover marks book attempted without updating cover`() =
        runTest {
            whenever(booksDao.getBooksWithoutCovers()).thenReturn(listOf(CoverLookupCandidate("b2", "Нет обложки", "Никто")))
            whenever(userPreferencesRepository.getAttemptedCoverLookupIds()).thenReturn(emptySet())
            whenever(coverProvider.lookup(any(), any())).thenReturn(null)

            val result = buildWorker().doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            verify(booksDao, never()).updateCoverUrl(any(), any())
            verify(userPreferencesRepository).markCoverLookupAttempted(listOf("b2"))
        }

    @Test
    fun `empty candidate list is a cheap no-op`() =
        runTest {
            whenever(booksDao.getBooksWithoutCovers()).thenReturn(emptyList())
            whenever(userPreferencesRepository.getAttemptedCoverLookupIds()).thenReturn(emptySet())

            val result = buildWorker().doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            verify(coverProvider, never()).lookup(any(), any())
            verify(userPreferencesRepository, never()).markCoverLookupAttempted(any())
        }

    @Test
    fun `already attempted books are excluded from the batch`() =
        runTest {
            whenever(booksDao.getBooksWithoutCovers())
                .thenReturn(
                    listOf(
                        CoverLookupCandidate("done", "Уже искали", "Автор"),
                        CoverLookupCandidate("fresh", "Новая книга", "Автор"),
                    ),
                )
            whenever(userPreferencesRepository.getAttemptedCoverLookupIds()).thenReturn(setOf("done"))
            whenever(coverProvider.lookup(any(), any())).thenReturn("https://example.com/cover.jpg")

            buildWorker().doWork()

            verify(coverProvider).lookup("Новая книга", "Автор")
            verify(userPreferencesRepository).markCoverLookupAttempted(listOf("fresh"))
        }

    private fun buildWorker(): CoverEnrichmentWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != CoverEnrichmentWorker::class.java.name) return null
                    return CoverEnrichmentWorker(
                        appContext = appContext,
                        params = workerParameters,
                        booksDao = booksDao,
                        userPreferencesRepository = userPreferencesRepository,
                        coverProvider = coverProvider,
                        loggerFactory = loggerFactory,
                    )
                }
            }

        return TestListenableWorkerBuilder<CoverEnrichmentWorker>(context)
            .setWorkerFactory(workerFactory)
            .setRunAttemptCount(0)
            .build()
    }

    private object NoopLogger : Logger {
        override fun d(message: () -> String) = Unit

        override fun d(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun e(message: () -> String) = Unit

        override fun e(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun i(message: () -> String) = Unit

        override fun i(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun w(message: () -> String) = Unit

        override fun w(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit

        override fun v(message: () -> String) = Unit

        override fun v(
            message: () -> String,
            throwable: Throwable?,
        ) = Unit

        override fun v(
            throwable: Throwable?,
            message: () -> String,
        ) = Unit
    }
}
