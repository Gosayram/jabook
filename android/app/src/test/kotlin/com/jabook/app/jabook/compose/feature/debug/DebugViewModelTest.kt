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
import com.jabook.app.jabook.compose.data.cache.CacheManager
import com.jabook.app.jabook.compose.data.cache.CacheStatistics
import com.jabook.app.jabook.compose.data.debug.DebugAudioFocusSimulator
import com.jabook.app.jabook.compose.data.debug.DebugLogService
import com.jabook.app.jabook.compose.data.debug.DebugRuntimeOverrides
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao
import com.jabook.app.jabook.compose.data.local.dao.FavoriteDao
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
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
import java.nio.file.Files

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
    private val debugRuntimeOverrides = DebugRuntimeOverrides()
    private val debugAudioFocusSimulator: DebugAudioFocusSimulator = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val database: JabookDatabase = mock()
    private val cacheManager: CacheManager = mock()
    private val cookieJar: PersistentCookieJar = mock()
    private val context: android.content.Context = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val logger: Logger = mock()
    private val booksDao: BooksDao = mock()
    private val favoriteDao: FavoriteDao = mock()
    private val chaptersDao: ChaptersDao = mock()
    private val offlineSearchDao: OfflineSearchDao = mock()
    private val downloadHistoryDao: DownloadHistoryDao = mock()
    private val searchHistoryDao: SearchHistoryDao = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val tempDir: java.io.File = Files.createTempDirectory("jabook_debug_test").toFile()
    private val coversDir = java.io.File(tempDir, "covers").apply { mkdirs() }
    private val coverFile = java.io.File(coversDir, "cover-1.jpg").apply { writeBytes(ByteArray(64)) }

    private val cacheStats =
        CacheStatistics(
            totalSize = 4096L,
            searchCacheSize = 1024L,
            topicCacheSize = 512L,
            tempDownloadsSize = 256L,
            logFilesSize = 128L,
            imageCacheSize = 2048L,
            lastCleanup = 100L,
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
        wheneverBlocking { cacheManager.getCacheStatistics() }.thenReturn(cacheStats)
        whenever(cookieJar.size()).thenReturn(7)
        whenever(context.filesDir).thenReturn(tempDir)
        whenever(networkMonitor.networkType).thenReturn(MutableStateFlow(NetworkType.UNKNOWN))
        whenever(database.booksDao()).thenReturn(booksDao)
        whenever(database.favoriteDao()).thenReturn(favoriteDao)
        whenever(database.chaptersDao()).thenReturn(chaptersDao)
        whenever(database.offlineSearchDao()).thenReturn(offlineSearchDao)
        whenever(database.downloadHistoryDao()).thenReturn(downloadHistoryDao)
        whenever(database.searchHistoryDao()).thenReturn(searchHistoryDao)
        wheneverBlocking { booksDao.getBookCount() }.thenReturn(0)
        wheneverBlocking { favoriteDao.getFavoritesCount() }.thenReturn(0)
        wheneverBlocking { offlineSearchDao.getTopicCount() }.thenReturn(0)
        wheneverBlocking { downloadHistoryDao.getCount() }.thenReturn(0)
        wheneverBlocking { chaptersDao.getTotalChapterCount() }.thenReturn(0)
        whenever(searchHistoryDao.getRecentSearches(any())).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun createViewModel(): DebugViewModel =
        DebugViewModel(
            debugLogService,
            mirrorManager,
            authService,
            debugRuntimeOverrides,
            debugAudioFocusSimulator,
            networkMonitor,
            database,
            cacheManager,
            cookieJar,
            context,
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
            assertEquals(cacheStats, viewModel.cacheSnapshot.value.stats)
        }

    @Test
    fun loadCacheStatsCollectsCoversDirectoryAndCookieCount() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(1, viewModel.cacheSnapshot.value.coversCount)
            assertEquals(64L, viewModel.cacheSnapshot.value.coversBytes)
            assertEquals(7, viewModel.cacheSnapshot.value.cookieCount)
        }

    @Test
    fun refreshDbInspectorIncludesChaptersCount() =
        runTest {
            viewModel = createViewModel()
            wheneverBlocking { chaptersDao.getTotalChapterCount() }.thenReturn(11)
            viewModel.refreshDbInspector()
            advanceUntilIdle()
            assertEquals(11, viewModel.dbInspectorSnapshot.value.chaptersCount)
        }
}
