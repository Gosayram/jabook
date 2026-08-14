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

package com.jabook.app.jabook.compose.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferencesSerializer
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val offlineSearchDao: OfflineSearchDao = mock()
    private val torrentDownloadRepository: TorrentDownloadRepository = mock()
    private val booksDao: BooksDao = mock()
    private val rutrackerRepository: RutrackerRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val okHttpClient: OkHttpClient = mock()
    private val loggerFactory: LoggerFactory =
        object : LoggerFactory {
            override fun get(tag: String): Logger = NoopLogger

            override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoopLogger
        }

    @Test
    fun `doWork returns retry when failure happens and attempts remain`() =
        runTest {
            whenever(settingsRepository.userPreferences).thenReturn(flowOf(UserPreferencesSerializer.defaultValue))
            whenever(networkMonitor.networkType).thenReturn(flowOf(NetworkType.WIFI))
            whenever(torrentDownloadRepository.getAll()).thenReturn(emptyList())
            whenever(booksDao.getAllBooks()).thenReturn(emptyList())
            whenever(offlineSearchDao.clearOldCache(org.mockito.kotlin.any())).thenThrow(
                IllegalStateException("boom"),
            )

            val worker = buildWorker(runAttemptCount = 0)
            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Retry)
        }

    @Test
    fun `doWork returns failure when retry budget exhausted`() =
        runTest {
            whenever(settingsRepository.userPreferences).thenReturn(flowOf(UserPreferencesSerializer.defaultValue))
            whenever(networkMonitor.networkType).thenReturn(flowOf(NetworkType.WIFI))
            whenever(torrentDownloadRepository.getAll()).thenReturn(emptyList())
            whenever(booksDao.getAllBooks()).thenReturn(emptyList())
            whenever(offlineSearchDao.clearOldCache(org.mockito.kotlin.any())).thenThrow(
                IllegalStateException("boom"),
            )

            val worker = buildWorker(runAttemptCount = 3)
            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
        }

    @Test
    fun `doWork propagates cancellation instead of scheduling a retry`() =
        runTest {
            whenever(settingsRepository.userPreferences).thenReturn(flowOf(UserPreferencesSerializer.defaultValue))
            whenever(networkMonitor.networkType).thenReturn(flowOf(NetworkType.WIFI))
            whenever(torrentDownloadRepository.getAll()).thenReturn(emptyList())
            whenever(booksDao.getAllBooks()).thenReturn(emptyList())
            whenever(offlineSearchDao.clearOldCache(org.mockito.kotlin.any())).thenThrow(CancellationException("cancelled"))

            assertFailsWith<CancellationException> {
                buildWorker(runAttemptCount = 0).doWork()
            }
        }

    @Test
    fun `doWork propagates cancellation from a per-download metadata request`() =
        runTest {
            whenever(torrentDownloadRepository.getAll()).thenReturn(
                listOf(
                    TorrentDownload(
                        hash = "hash-1",
                        name = "Book",
                        state = TorrentState.DOWNLOADING,
                        topicId = "topic-1",
                    ),
                ),
            )
            whenever(rutrackerRepository.getTopicDetails("topic-1")).thenThrow(
                CancellationException("cancelled"),
            )

            assertFailsWith<CancellationException> {
                buildWorker(runAttemptCount = 0).doWork()
            }
        }

    @Test
    fun `sync metadata reads books once regardless of download count`() =
        runTest {
            whenever(torrentDownloadRepository.getAll()).thenReturn(
                listOf(
                    TorrentDownload(hash = "hash-1", name = "Book 1", state = TorrentState.DOWNLOADING, topicId = "topic-1"),
                    TorrentDownload(hash = "hash-2", name = "Book 2", state = TorrentState.DOWNLOADING, topicId = "topic-2"),
                ),
            )
            whenever(booksDao.getAllBooks()).thenReturn(emptyList())
            whenever(rutrackerRepository.getTopicDetails(org.mockito.kotlin.any())).thenReturn(Result.failure(Exception("offline")))
            whenever(settingsRepository.userPreferences).thenReturn(flowOf(UserPreferencesSerializer.defaultValue))
            whenever(networkMonitor.networkType).thenReturn(flowOf(NetworkType.WIFI))

            buildWorker(runAttemptCount = 0).doWork()

            verify(booksDao, times(2)).getAllBooks()
        }

    @Test
    fun `syncCoverImages rejects ws scheme`() =
        runTest {
            whenever(torrentDownloadRepository.getAll()).thenReturn(emptyList())
            whenever(settingsRepository.userPreferences).thenReturn(flowOf(UserPreferencesSerializer.defaultValue))
            whenever(networkMonitor.networkType).thenReturn(flowOf(NetworkType.WIFI))
            val bookWithWs =
                mock<com.jabook.app.jabook.compose.data.local.entity.BookEntity> {
                    whenever(it.id).thenReturn("b1")
                    whenever(it.coverUrl).thenReturn("ws://example.com/cover.jpg")
                    whenever(it.coverPath).thenReturn(null)
                }
            whenever(booksDao.getAllBooks()).thenReturn(listOf(bookWithWs))

            buildWorker(runAttemptCount = 0).doWork()

            verify(okHttpClient, org.mockito.kotlin.never()).newCall(org.mockito.kotlin.any())
        }

    @Test
    fun `syncCoverImages rejects ftp scheme`() =
        runTest {
            whenever(torrentDownloadRepository.getAll()).thenReturn(emptyList())
            whenever(settingsRepository.userPreferences).thenReturn(flowOf(UserPreferencesSerializer.defaultValue))
            whenever(networkMonitor.networkType).thenReturn(flowOf(NetworkType.WIFI))
            val bookWithFtp =
                mock<com.jabook.app.jabook.compose.data.local.entity.BookEntity> {
                    whenever(it.id).thenReturn("b2")
                    whenever(it.coverUrl).thenReturn("ftp://example.com/cover.jpg")
                    whenever(it.coverPath).thenReturn(null)
                }
            whenever(booksDao.getAllBooks()).thenReturn(listOf(bookWithFtp))

            buildWorker(runAttemptCount = 0).doWork()

            verify(okHttpClient, org.mockito.kotlin.never()).newCall(org.mockito.kotlin.any())
        }

    private fun buildWorker(runAttemptCount: Int): SyncWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != SyncWorker::class.java.name) return null
                    return SyncWorker(
                        appContext = appContext,
                        params = workerParameters,
                        offlineSearchDao = offlineSearchDao,
                        torrentDownloadRepository = torrentDownloadRepository,
                        booksDao = booksDao,
                        rutrackerRepository = rutrackerRepository,
                        settingsRepository = settingsRepository,
                        networkMonitor = networkMonitor,
                        coverDownloadClient = okHttpClient,
                        loggerFactory = loggerFactory,
                    )
                }
            }

        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }
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
