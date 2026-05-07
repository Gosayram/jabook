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
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TorrentDownloadsViewModelTest {
    private val torrentManager: TorrentManager = mock()
    private val repository: TorrentDownloadRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val networkMonitor: NetworkMonitor = mock()
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
        whenever(repository.getAllFlow()).thenReturn(emptyFlow())
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
}
