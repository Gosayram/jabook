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

package com.jabook.app.jabook.compose.feature.torrent

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentFile
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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
@OptIn(ExperimentalCoroutinesApi::class)
class TorrentDetailsViewModelTest {
    private val context: Context = mock()
    private val torrentManager: TorrentManager = mock()
    private val torrentDownloadRepository: TorrentDownloadRepository = mock()
    private val booksRepository: BooksRepository = mock()
    private val streamingMonitor: TorrentStreamingMonitor = mock()
    private val loggerFactory: LoggerFactory = mock()

    private val testDispatcher = StandardTestDispatcher()

    private val downloadsFlow =
        MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())

    private val downloadHash1 =
        TorrentDownload(
            hash = "hash1",
            name = "Alpha Audiobook",
            state = TorrentState.DOWNLOADING,
            savePath = "/downloads/alpha",
            files =
                listOf(
                    TorrentFile(index = 0, path = "alpha/ch1.mp3", size = 1000L),
                    TorrentFile(index = 1, path = "alpha/ch2.mp3", size = 2000L),
                    TorrentFile(index = 2, path = "alpha/cover.jpg", size = 500L),
                ),
        )

    private lateinit var viewModel: TorrentDetailsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(NoOpLogger)
        whenever(context.getString(any<Int>())).thenReturn("message")
        whenever(torrentManager.downloadsFlow).thenReturn(downloadsFlow)
        whenever(streamingMonitor.isBuffering).thenReturn(MutableStateFlow(false))
        whenever(streamingMonitor.monitoredHash).thenReturn(MutableStateFlow<String?>(null))
        downloadsFlow.value = mapOf("hash1" to downloadHash1)
        viewModel =
            TorrentDetailsViewModel(
                context,
                torrentManager,
                torrentDownloadRepository,
                booksRepository,
                SavedStateHandle(mapOf("hash" to "hash1")),
                streamingMonitor,
                loggerFactory,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `hash is taken from route`() {
        assertEquals("hash1", viewModel.hash)
    }

    @Test
    fun `download flow exposes entry matching route hash`() =
        runTest(testDispatcher.scheduler) {
            viewModel.download.test {
                advanceUntilIdle()
                assertEquals(downloadHash1, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `download flow hides entries of other hashes`() =
        runTest(testDispatcher.scheduler) {
            val other =
                downloadHash1.copy(hash = "hash2", name = "Beta Audiobook")
            downloadsFlow.value = mapOf("hash2" to other)

            viewModel.download.test {
                advanceUntilIdle()
                assertEquals(null, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `update file selection maps selected files to normal priority and rest to skip`() =
        runTest(testDispatcher.scheduler) {
            whenever(torrentDownloadRepository.getByHash("hash1")).thenReturn(downloadHash1)

            viewModel.updateFileSelection(setOf(1))
            advanceUntilIdle()

            verify(torrentManager).prioritizeFiles("hash1", listOf(0, 4, 0))
        }

    @Test
    fun `update file selection with no download does not touch manager`() =
        runTest(testDispatcher.scheduler) {
            whenever(torrentDownloadRepository.getByHash("hash1")).thenReturn(null)

            viewModel.updateFileSelection(setOf(0))
            advanceUntilIdle()

            verify(torrentManager, org.mockito.kotlin.never()).prioritizeFiles(any(), any())
        }
}
