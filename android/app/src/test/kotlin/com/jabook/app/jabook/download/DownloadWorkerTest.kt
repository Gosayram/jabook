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

package com.jabook.app.jabook.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.test.withTestTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class DownloadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val torrentManager: TorrentManager = mock()

    @Test
    fun `doWork finishes when a non-completing progress flow reports completion`() =
        runTest {
            whenever(torrentManager.addMagnetLink(any(), any(), any())).thenReturn("hash-1")
            whenever(torrentManager.getDownloadProgress("hash-1")).thenReturn(
                MutableStateFlow(
                    TorrentDownload(
                        hash = "hash-1",
                        name = "Book",
                        state = TorrentState.COMPLETED,
                        progress = 1f,
                    ),
                ),
            )

            val result = withTestTimeout { buildWorker().doWork() }

            assertTrue(result is ListenableWorker.Result.Success)
        }

    private fun buildWorker(): DownloadWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != DownloadWorker::class.java.name) return null
                    return DownloadWorker(
                        context = appContext,
                        params = workerParameters,
                        torrentManager = torrentManager,
                    )
                }
            }

        val inputData =
            Data
                .Builder()
                .putString(DownloadWorker.KEY_MAGNET_URI, "magnet:?xt=urn:btih:hash-1")
                .putString(DownloadWorker.KEY_SAVE_PATH, "/books")
                .putString(DownloadWorker.KEY_BOOK_TITLE, "Book")
                .build()

        return TestListenableWorkerBuilder<DownloadWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(workerFactory)
            .build()
    }
}
