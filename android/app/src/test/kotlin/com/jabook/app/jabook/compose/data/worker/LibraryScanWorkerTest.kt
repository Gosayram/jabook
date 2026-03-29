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
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import com.jabook.app.jabook.compose.data.local.scanner.ScannedBook
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.AppError
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryScanWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val booksDao: BooksDao = mock()
    private val chaptersDao: ChaptersDao = mock()
    private val loggerFactory: LoggerFactory =
        object : LoggerFactory {
            override fun get(tag: String): Logger = NoopWorkerLogger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoopWorkerLogger
        }

    @Test
    fun `doWork returns failure when scanner is cancelled`() =
        runTest {
            val worker =
                buildWorker(
                    scanner =
                        object : LocalBookScanner {
                            private val progress = MutableStateFlow<ScanProgress>(ScanProgress.Discovery(0))
                            override val scanProgress: StateFlow<ScanProgress> = progress

                            override suspend fun scanAudiobooks(): Result<List<ScannedBook>, AppError> =
                                throw CancellationException("cancelled by test")
                        },
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
        }

    private fun buildWorker(scanner: LocalBookScanner): LibraryScanWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != LibraryScanWorker::class.java.name) return null
                    return LibraryScanWorker(
                        appContext = appContext,
                        params = workerParameters,
                        bookScanner = scanner,
                        booksDao = booksDao,
                        chaptersDao = chaptersDao,
                        loggerFactory = loggerFactory,
                    )
                }
            }

        return TestListenableWorkerBuilder<LibraryScanWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
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
