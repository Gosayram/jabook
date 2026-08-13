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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class ChapterDetectionWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `doWork rejects invalid input`() =
        runBlocking {
            assertTrue(buildWorker(Data.EMPTY).doWork() is ListenableWorker.Result.Failure)
        }

    @Test
    fun `doWork leaves a single source file as one playable chapter`() =
        runBlocking {
            assertTrue(buildWorker(validInputData()).doWork() is ListenableWorker.Result.Success)
        }

    private fun validInputData(): Data =
        Data
            .Builder()
            .putString(ChapterDetectionWorker.KEY_BOOK_ID, "book-1")
            .putString(ChapterDetectionWorker.KEY_FILE_PATH, "/tmp/book.mp3")
            .putInt(ChapterDetectionWorker.KEY_FILE_INDEX, 0)
            .putLong(ChapterDetectionWorker.KEY_DURATION_MS, 3_600_000L)
            .build()

    private fun buildWorker(inputData: Data): ChapterDetectionWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? = ChapterDetectionWorker(appContext, workerParameters)
            }
        return TestListenableWorkerBuilder<ChapterDetectionWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(workerFactory)
            .build()
    }
}
