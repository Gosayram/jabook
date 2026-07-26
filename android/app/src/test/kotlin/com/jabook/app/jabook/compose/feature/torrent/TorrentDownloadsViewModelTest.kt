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

import androidx.lifecycle.SavedStateHandle
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.repository.DownloadHistoryRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentDownloadsViewModelTest {
    private val torrentManager: TorrentManager = mock()
    private val repository: TorrentDownloadRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val downloadHistoryRepository: DownloadHistoryRepository = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val savedStateHandle = SavedStateHandle()

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesFlow =
        MutableStateFlow(
            UserPreferences
                .newBuilder()
                .setDownloadPath("/tmp")
                .build(),
        )
    private val networkTypeFlow = MutableStateFlow(NetworkType.WIFI)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(NoOpLogger)
        whenever(torrentManager.downloadsFlow).thenReturn(MutableStateFlow(emptyMap()))
        whenever(repository.getAllFlow()).thenReturn(flowOf(emptyList()))
        whenever(downloadHistoryRepository.getHistoryWithFilter(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.userPreferences).thenReturn(preferencesFlow)
        whenever(networkMonitor.networkType).thenReturn(networkTypeFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resumeDownload emits wifi warning when wifi-only enabled on cellular`() =
        runTest(testDispatcher) {
            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )
            preferencesFlow.value =
                UserPreferences
                    .newBuilder()
                    .setWifiOnlyDownload(true)
                    .setDownloadPath("/tmp")
                    .build()
            networkTypeFlow.value = NetworkType.CELLULAR

            val snackbarDeferred =
                backgroundScope.async {
                    viewModel.snackbarEvent.first()
                }

            viewModel.resumeDownload("hash-1")
            advanceUntilIdle()

            assertEquals("Download queued: Waiting for WiFi connection", snackbarDeferred.await())
            verify(torrentManager).resumeTorrent("hash-1")
        }

    @Test
    fun `resumeDownload does not emit wifi warning on ethernet when wifi-only enabled`() =
        runTest(testDispatcher) {
            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )
            preferencesFlow.value =
                UserPreferences
                    .newBuilder()
                    .setWifiOnlyDownload(true)
                    .setDownloadPath("/tmp")
                    .build()
            networkTypeFlow.value = NetworkType.ETHERNET

            viewModel.resumeDownload("hash-2")
            advanceUntilIdle()

            assertNull(withTimeoutOrNull(100) { viewModel.snackbarEvent.first() })
            verify(torrentManager).resumeTorrent("hash-2")
        }

    @Test
    fun `resumeDownload network policy matrix emits warning only when expected`() =
        runTest(testDispatcher) {
            val scenarios =
                listOf(
                    Triple(false, NetworkType.WIFI, false),
                    Triple(false, NetworkType.ETHERNET, false),
                    Triple(false, NetworkType.CELLULAR, false),
                    Triple(false, NetworkType.NONE, false),
                    Triple(false, NetworkType.UNKNOWN, false),
                    Triple(true, NetworkType.WIFI, false),
                    Triple(true, NetworkType.ETHERNET, false),
                    Triple(true, NetworkType.CELLULAR, true),
                    Triple(true, NetworkType.NONE, true),
                    Triple(true, NetworkType.UNKNOWN, true),
                )

            scenarios.forEachIndexed { index, (wifiOnly, networkType, shouldWarn) ->
                val viewModel =
                    TorrentDownloadsViewModel(
                        torrentManager = torrentManager,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        networkMonitor = networkMonitor,
                        downloadHistoryRepository = downloadHistoryRepository,
                        loggerFactory = loggerFactory,
                        savedStateHandle = savedStateHandle,
                    )
                preferencesFlow.value =
                    UserPreferences
                        .newBuilder()
                        .setWifiOnlyDownload(wifiOnly)
                        .setDownloadPath("/tmp")
                        .build()
                networkTypeFlow.value = networkType

                val snackbarDeferred =
                    backgroundScope.async {
                        withTimeoutOrNull(120) { viewModel.snackbarEvent.first() }
                    }

                viewModel.resumeDownload("hash-$index")
                advanceUntilIdle()

                val snackbar = snackbarDeferred.await()
                if (shouldWarn) {
                    assertEquals("Download queued: Waiting for WiFi connection", snackbar)
                } else {
                    assertNull(snackbar)
                }
            }
        }

    @Test
    fun `confirmAddTorrent emits wifi warning on restricted network and still adds torrent`() =
        runTest(testDispatcher) {
            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )
            preferencesFlow.value =
                UserPreferences
                    .newBuilder()
                    .setWifiOnlyDownload(true)
                    .setDownloadPath("/tmp")
                    .build()
            networkTypeFlow.value = NetworkType.CELLULAR

            viewModel.prepareAddTorrent("magnet:?xt=urn:btih:test")
            advanceUntilIdle()

            val snackbarDeferred =
                backgroundScope.async {
                    viewModel.snackbarEvent.first()
                }

            viewModel.confirmAddTorrent()
            advanceUntilIdle()

            assertEquals("Download queued: Waiting for WiFi connection", snackbarDeferred.await())
            verify(torrentManager).addTorrent("magnet:?xt=urn:btih:test", "/tmp")
        }

    @Test
    fun `status mapping groups downloads by state correctly`() =
        runTest(testDispatcher) {
            val downloading =
                TorrentDownload(
                    hash = "h1",
                    name = "Book 1",
                    state = TorrentState.DOWNLOADING,
                    progress = 0.5f,
                    downloadSpeed = 100,
                )
            val paused =
                TorrentDownload(
                    hash = "h2",
                    name = "Book 2",
                    state = TorrentState.PAUSED,
                    progress = 0.3f,
                )
            val completed =
                TorrentDownload(
                    hash = "h3",
                    name = "Book 3",
                    state = TorrentState.COMPLETED,
                    progress = 1f,
                    totalSize = 1000L,
                )
            val errored =
                TorrentDownload(
                    hash = "h4",
                    name = "Book 4",
                    state = TorrentState.ERROR,
                    errorMessage = "Timeout",
                )
            val queued =
                TorrentDownload(
                    hash = "h5",
                    name = "Book 5",
                    state = TorrentState.QUEUED,
                )

            whenever(torrentManager.downloadsFlow).thenReturn(
                MutableStateFlow(
                    mapOf(
                        "h1" to downloading,
                        "h2" to paused,
                        "h3" to completed,
                        "h4" to errored,
                        "h5" to queued,
                    ),
                ),
            )

            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )

            // Subscribe to trigger WhileSubscribed collection
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            val success = viewModel.uiState.value as TorrentDownloadsUiState.Success

            assertEquals(1, success.activeDownloads.size) // downloading
            assertEquals(1, success.pausedDownloads.size) // paused
            assertEquals(1, success.completedDownloads.size) // completed
            assertEquals(1, success.errorDownloads.size) // error
            assertEquals(1, success.queuedCount) // queued
            assertEquals(1, success.downloadingCount)
            assertEquals(100L, success.totalDownloadSpeed)
            collectJob.cancel()
        }

    @Test
    fun `history items are loaded into Success state`() =
        runTest(testDispatcher) {
            val historyItems =
                listOf(
                    DownloadHistoryItem(
                        id = 1,
                        bookId = "b1",
                        bookTitle = "Completed Book",
                        status = "completed",
                        startedAt = 1000L,
                        completedAt = 2000L,
                        totalBytes = 5000L,
                        errorMessage = null,
                    ),
                    DownloadHistoryItem(
                        id = 2,
                        bookId = "b2",
                        bookTitle = "Failed Book",
                        status = "failed",
                        startedAt = 1000L,
                        completedAt = 2000L,
                        totalBytes = null,
                        errorMessage = "Network error",
                    ),
                    DownloadHistoryItem(
                        id = 3,
                        bookId = "b3",
                        bookTitle = "Cancelled Book",
                        status = "cancelled",
                        startedAt = 1000L,
                        completedAt = 2000L,
                        totalBytes = null,
                        errorMessage = null,
                    ),
                )

            whenever(downloadHistoryRepository.getHistoryWithFilter(any(), any())).thenReturn(
                flowOf(historyItems),
            )

            whenever(torrentManager.downloadsFlow).thenReturn(
                MutableStateFlow(
                    mapOf(
                        "h1" to TorrentDownload(hash = "h1", name = "Active", state = TorrentState.DOWNLOADING),
                    ),
                ),
            )

            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )

            // Subscribe to trigger WhileSubscribed collection
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            val success = viewModel.uiState.value as TorrentDownloadsUiState.Success

            assertEquals(3, success.historyItems.size)
            assertEquals("completed", success.historyItems[0].status)
            assertEquals("failed", success.historyItems[1].status)
            assertEquals("cancelled", success.historyItems[2].status)
            collectJob.cancel()
        }

    @Test
    fun `storage summary maps completed download sizes to audiobook storage`() =
        runTest(testDispatcher) {
            val completedDownload =
                TorrentDownload(
                    hash = "h1",
                    name = "Audiobook",
                    state = TorrentState.COMPLETED,
                    progress = 1f,
                    totalSize = 50_000_000L,
                )
            val downloadingDownload =
                TorrentDownload(
                    hash = "h2",
                    name = "Downloading",
                    state = TorrentState.DOWNLOADING,
                    progress = 0.5f,
                    totalSize = 30_000_000L,
                )

            whenever(torrentManager.downloadsFlow).thenReturn(
                MutableStateFlow(
                    mapOf(
                        "h1" to completedDownload,
                        "h2" to downloadingDownload,
                    ),
                ),
            )

            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )

            // Subscribe to trigger WhileSubscribed collection
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            val success = viewModel.uiState.value as TorrentDownloadsUiState.Success

            // Audiobook storage = only completed downloads
            assertEquals(50_000_000L, success.audiobookStorageUsed)
            // Total storage = all downloads
            assertEquals(80_000_000L, success.totalStorageUsed)
            // Available storage should be non-negative (StatFs may return 0 in test env)
            assertTrue(success.availableStorage >= 0L)
            collectJob.cancel()
        }

    @Test
    fun `pause all sends pause to all active downloads`() =
        runTest(testDispatcher) {
            val activeMap =
                mapOf(
                    "h1" to TorrentDownload(hash = "h1", name = "Book 1", state = TorrentState.DOWNLOADING),
                    "h2" to TorrentDownload(hash = "h2", name = "Book 2", state = TorrentState.STREAMING),
                )
            whenever(torrentManager.downloadsFlow).thenReturn(MutableStateFlow(activeMap))

            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )

            // Subscribe to trigger WhileSubscribed collection before checking state
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.pauseAll()
            advanceUntilIdle()

            verify(torrentManager).pauseTorrent("h1")
            verify(torrentManager).pauseTorrent("h2")
            collectJob.cancel()
        }

    @Test
    fun `cancel delete removes from manager and repository`() =
        runTest(testDispatcher) {
            whenever(torrentManager.downloadsFlow).thenReturn(
                MutableStateFlow(
                    mapOf(
                        "h1" to TorrentDownload(hash = "h1", name = "Book 1", state = TorrentState.DOWNLOADING),
                    ),
                ),
            )

            val viewModel =
                TorrentDownloadsViewModel(
                    torrentManager = torrentManager,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    networkMonitor = networkMonitor,
                    downloadHistoryRepository = downloadHistoryRepository,
                    loggerFactory = loggerFactory,
                    savedStateHandle = savedStateHandle,
                )

            viewModel.deleteDownload("h1", deleteFiles = false)
            advanceUntilIdle()

            verify(torrentManager).removeTorrent("h1", false)
            verify(repository).delete("h1")
        }
}
