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

package com.jabook.app.jabook.audio.processors

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class LufsAnalysisWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val booksDao: BooksDao = mock()
    private val chaptersDao: ChaptersDao = mock()
    private val loggerFactory: LoggerFactory =
        object : LoggerFactory {
            override fun get(tag: String): Logger = NoopWorkerLogger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoopWorkerLogger
        }

    @Test
    fun `unsupported format writes failure sentinel then fails`() =
        runBlocking {
            whenever(booksDao.getBookLocalPath(any())).thenReturn(null)
            whenever(chaptersDao.getChaptersByBookId(any())).thenReturn(emptyList())

            val result = buildWorker().doWork()

            verify(booksDao).updateLufsValue("book1", LufsAnalysisWorker.LUFS_ANALYSIS_FAILED)
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    @Test
    fun `retry exhaustion writes failure sentinel then fails`() =
        runBlocking {
            whenever(booksDao.getBookLocalPath(any())).thenThrow(RuntimeException("corrupt file"))
            whenever(chaptersDao.getChaptersByBookId(any())).thenReturn(emptyList())

            val result = buildWorker(runAttemptCount = 3).doWork()

            verify(booksDao).updateLufsValue("book1", LufsAnalysisWorker.LUFS_ANALYSIS_FAILED)
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    @Test
    fun `missing book id fails without touching dao`() =
        runBlocking {
            val result = buildWorker(inputData = workDataOf()).doWork()

            verify(booksDao, never()).updateLufsValue(any(), any())
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    @Test
    fun `isValidLufs rejects null and sentinel accepts real measurements`() {
        assertFalse(LufsAnalysisWorker.isValidLufs(null))
        assertFalse(LufsAnalysisWorker.isValidLufs(LufsAnalysisWorker.LUFS_ANALYSIS_FAILED))
        assertTrue(LufsAnalysisWorker.isValidLufs(-23.0))
        assertTrue(LufsAnalysisWorker.isValidLufs(-70.0))
        // Sentinel must be a normal double: SQLite turns NaN into NULL.
        assertEquals(-999.0, LufsAnalysisWorker.LUFS_ANALYSIS_FAILED, 0.0)
        assertFalse(LufsAnalysisWorker.LUFS_ANALYSIS_FAILED.isNaN())
    }

    private fun buildWorker(
        inputData: androidx.work.Data = workDataOf(LufsAnalysisWorker.KEY_BOOK_ID to "book1"),
        runAttemptCount: Int = 0,
    ): LufsAnalysisWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != LufsAnalysisWorker::class.java.name) return null
                    return LufsAnalysisWorker(
                        context = appContext,
                        params = workerParameters,
                        booksDao = booksDao,
                        chaptersDao = chaptersDao,
                        loggerFactory = loggerFactory,
                    )
                }
            }

        return TestListenableWorkerBuilder<LufsAnalysisWorker>(context)
            .setWorkerFactory(workerFactory)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .build() as LufsAnalysisWorker
    }
}

private object NoopWorkerLogger : Logger {
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
