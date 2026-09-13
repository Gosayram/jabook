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

package com.jabook.app.jabook.compose.feature.debug

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache
import com.jabook.app.jabook.compose.data.debug.DebugAudioFocusSimulator
import com.jabook.app.jabook.compose.data.debug.DebugLogService
import com.jabook.app.jabook.compose.data.debug.DebugRuntimeOverrides
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao
import com.jabook.app.jabook.compose.data.local.dao.FavoriteDao
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Regression tests for build 142 crash: the init block's coroutine runs eagerly on
 * Dispatchers.Main.immediate during <init>; loadCacheStats() writes _cacheStats
 * synchronously (getCacheStatistics is non-suspending), which NPEd when the
 * _cacheStats property was declared after the init block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {
    private val debugLogService: DebugLogService = mock()
    private val mirrorManager: MirrorManager = mock()
    private val authService: com.jabook.app.jabook.compose.data.auth.RutrackerAuthService = mock()
    private val rutrackerRepository: RutrackerRepository = mock()
    private val debugRuntimeOverrides = DebugRuntimeOverrides()
    private val debugAudioFocusSimulator: DebugAudioFocusSimulator = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val database: JabookDatabase = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val logger: Logger = mock()
    private val booksDao: BooksDao = mock()
    private val favoriteDao: FavoriteDao = mock()
    private val offlineSearchDao: OfflineSearchDao = mock()
    private val downloadHistoryDao: DownloadHistoryDao = mock()
    private val searchHistoryDao: SearchHistoryDao = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val cacheStats =
        RutrackerSearchCache.CacheStatistics(
            entriesCount = 3,
            totalResults = 42,
            estimatedSize = 1024L,
            oldestEntry = 100L,
            newestEntry = 200L,
        )

    private lateinit var viewModel: DebugViewModel

    @Before
    fun setUp() {
        // Reproduces Dispatchers.Main.immediate: the init-block coroutine body runs
        // synchronously inside the constructor, exactly like on device.
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)
        wheneverBlocking { debugLogService.collectLogs() }.thenReturn("")
        whenever(mirrorManager.availableMirrors).thenReturn(MutableStateFlow(emptyList()))
        wheneverBlocking { authService.validateAuth(any()) }.thenReturn(false)
        whenever(authService.lastAuthError).thenReturn(null)
        whenever(rutrackerRepository.getCacheStatistics()).thenReturn(cacheStats)
        whenever(networkMonitor.networkType).thenReturn(MutableStateFlow(NetworkType.UNKNOWN))
        whenever(database.booksDao()).thenReturn(booksDao)
        whenever(database.favoriteDao()).thenReturn(favoriteDao)
        whenever(database.offlineSearchDao()).thenReturn(offlineSearchDao)
        whenever(database.downloadHistoryDao()).thenReturn(downloadHistoryDao)
        whenever(database.searchHistoryDao()).thenReturn(searchHistoryDao)
        wheneverBlocking { booksDao.getBookCount() }.thenReturn(0)
        wheneverBlocking { favoriteDao.getFavoritesCount() }.thenReturn(0)
        wheneverBlocking { offlineSearchDao.getTopicCount() }.thenReturn(0)
        wheneverBlocking { downloadHistoryDao.getCount() }.thenReturn(0)
        whenever(searchHistoryDao.getRecentSearches(any())).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DebugViewModel =
        DebugViewModel(
            debugLogService,
            mirrorManager,
            authService,
            rutrackerRepository,
            debugRuntimeOverrides,
            debugAudioFocusSimulator,
            networkMonitor,
            database,
            loggerFactory,
        )

    @Test
    fun constructorDoesNotThrowAndRunsInitWork() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is DebugUiState.Loading || viewModel.uiState.value is DebugUiState.Success)
        }

    @Test
    fun loadCacheStatsWritesStatisticsDuringConstruction() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(cacheStats, viewModel.cacheStats.value)
        }
}
