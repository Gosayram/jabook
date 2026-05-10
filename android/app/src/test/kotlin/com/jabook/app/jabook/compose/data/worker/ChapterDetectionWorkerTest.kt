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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jabook.app.jabook.audio.ChapterSignalExtractor
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import kotlinx.coroutines.runBlocking
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
class ChapterDetectionWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val chaptersDao: ChaptersDao = mock()
    private val loggerFactory: LoggerFactory =
        object : LoggerFactory {
            override fun get(tag: String): Logger = NoopChapterDetectionWorkerLogger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoopChapterDetectionWorkerLogger
        }

    @Test
    fun `doWork returns failure when input data is invalid`() =
        runBlocking {
            val worker =
                buildWorker(
                    inputData = Data.EMPTY,
                    extractor = ChapterSignalExtractor { _, _ -> emptyList() },
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
            verify(chaptersDao, never()).insertAll(any())
        }

    @Test
    fun `doWork returns success and skips persistence when signal is empty`() =
        runBlocking {
            val inputData = validInputData()
            val worker =
                buildWorker(
                    inputData = inputData,
                    extractor = ChapterSignalExtractor { _, _ -> emptyList() },
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            verify(chaptersDao, never()).deleteByBookId(any())
            verify(chaptersDao, never()).insertAll(any())
        }

    @Test
    fun `doWork returns success and skips persistence for multi-file books`() =
        runBlocking {
            whenever(chaptersDao.getTotalCount("book-1")).thenReturn(3)
            val worker =
                buildWorker(
                    inputData = validInputData(),
                    extractor =
                        ChapterSignalExtractor { _, _ ->
                            buildList {
                                repeat(10) { add(-20f) }
                                repeat(24) { add(-48f) }
                                repeat(10) { add(-19f) }
                            }
                        },
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            verify(chaptersDao, never()).deleteByBookId(any())
            verify(chaptersDao, never()).insertAll(any())
        }

    @Test
    fun `doWork persists synthetic chapters for single-file books`() =
        runBlocking {
            whenever(chaptersDao.getTotalCount("book-1")).thenReturn(1)
            val worker =
                buildWorker(
                    inputData = validInputData(),
                    extractor =
                        ChapterSignalExtractor { _, _ ->
                            buildList {
                                repeat(10) { add(-20f) }
                                repeat(24) { add(-48f) }
                                repeat(10) { add(-19f) }
                                repeat(24) { add(-49f) }
                                repeat(10) { add(-18f) }
                            }
                        },
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            verify(chaptersDao).deleteByBookId("book-1")
            verify(chaptersDao).insertAll(any())
        }

    private fun validInputData(): Data =
        Data
            .Builder()
            .putString(ChapterDetectionWorker.KEY_BOOK_ID, "book-1")
            .putString(ChapterDetectionWorker.KEY_FILE_PATH, "/tmp/book.mp3")
            .putInt(ChapterDetectionWorker.KEY_FILE_INDEX, 0)
            .putLong(ChapterDetectionWorker.KEY_DURATION_MS, 3_600_000L)
            .build()

    private fun buildWorker(
        inputData: Data,
        extractor: ChapterSignalExtractor,
    ): ChapterDetectionWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != ChapterDetectionWorker::class.java.name) return null
                    return ChapterDetectionWorker(
                        appContext = appContext,
                        params = workerParameters,
                        chapterSignalExtractor = extractor,
                        chaptersDao = chaptersDao,
                        loggerFactory = loggerFactory,
                    )
                }
            }

        return TestListenableWorkerBuilder<ChapterDetectionWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(workerFactory)
            .build()
    }
}

private object NoopChapterDetectionWorkerLogger : Logger {
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
