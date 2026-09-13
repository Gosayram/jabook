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

package com.jabook.app.jabook.compose.feature.settings

import androidx.work.WorkManager
import com.jabook.app.jabook.audio.domain.usecase.ListeningStatsUseCase
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.backup.BackupService
import com.jabook.app.jabook.compose.data.cache.CacheManager
import com.jabook.app.jabook.compose.data.cache.CacheStatistics
import com.jabook.app.jabook.compose.data.cache.CacheType
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferencesSerializer
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.UserEqPresetRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.library.GetLibraryUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/** "Clear cache" must end the RuTracker session on a full clear (credentials kept). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelClearCacheTest {
    private val context: android.content.Context = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val userPreferencesRepository: UserPreferencesRepository = mock()
    private val authRepository: AuthRepository = mock()
    private val booksRepository: BooksRepository = mock()
    private val mirrorManager: MirrorManager = mock()
    private val backupService: BackupService = mock()
    private val cacheManager: CacheManager = mock()
    private val updateBookSettingsUseCase: UpdateBookSettingsUseCase = mock()
    private val workManager: WorkManager = mock()
    private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao = mock()
    private val userEqPresetRepository: UserEqPresetRepository = mock()
    private val torrentManager: TorrentManager = mock()
    private val forumCatalog: com.jabook.app.jabook.compose.data.indexing.ForumCatalog = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val logger: Logger = mock()
    private val getLibraryUseCase: GetLibraryUseCase = mock()
    private val listeningStatsUseCase: ListeningStatsUseCase = mock()

    private val testDispatcher = StandardTestDispatcher()

    private val stats =
        CacheStatistics(
            totalSize = 0L,
            searchCacheSize = 0L,
            topicCacheSize = 0L,
            tempDownloadsSize = 0L,
            logFilesSize = 0L,
            imageCacheSize = 0L,
            lastCleanup = 0L,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)
        whenever(settingsRepository.userPreferences)
            .thenReturn(MutableStateFlow(UserPreferencesSerializer.defaultValue))
        whenever(settingsRepository.customEqBands).thenReturn(MutableStateFlow(List(10) { 0 }))
        whenever(userPreferencesRepository.userData)
            .thenReturn(
                flowOf(
                    com.jabook.app.jabook.compose.data.model
                        .UserData(),
                ),
            )
        whenever(authRepository.authStatus).thenReturn(MutableStateFlow(AuthStatus.Unauthenticated))
        whenever(booksRepository.getScanProgress()).thenReturn(flowOf(ScanProgress.Idle))
        whenever(mirrorManager.currentMirror).thenReturn(MutableStateFlow("mirror.example"))
        whenever(mirrorManager.availableMirrors).thenReturn(MutableStateFlow(listOf("mirror.example")))
        whenever(torrentManager.downloadsFlow).thenReturn(MutableStateFlow(emptyMap()))
        whenever(forumCatalog.observeAll()).thenReturn(flowOf(emptyList()))
        whenever(getLibraryUseCase(any())).thenReturn(flowOf(emptyList()))
        wheneverBlocking { cacheManager.getCacheStatistics() }.thenReturn(stats)
        wheneverBlocking { cacheManager.clearAllCache() }.thenReturn(true)
        wheneverBlocking { cacheManager.clearCacheType(any()) }.thenReturn(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            context = context,
            settingsRepository = settingsRepository,
            userPreferencesRepository = userPreferencesRepository,
            authRepository = authRepository,
            booksRepository = booksRepository,
            mirrorManager = mirrorManager,
            backupService = backupService,
            cacheManager = cacheManager,
            updateBookSettingsUseCase = updateBookSettingsUseCase,
            workManager = workManager,
            scanPathDao = scanPathDao,
            userEqPresetRepository = userEqPresetRepository,
            torrentManager = torrentManager,
            forumCatalog = forumCatalog,
            loggerFactory = loggerFactory,
            getLibraryUseCase = getLibraryUseCase,
            listeningStatsUseCase = listeningStatsUseCase,
        )

    @Test
    fun `full clear cache also clears the auth session`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearCache()
            advanceUntilIdle()

            verify(cacheManager).clearAllCache()
            verify(authRepository).clearSession()
            assertTrue(viewModel.cacheOperation.value is CacheOperationState.Success)
        }

    @Test
    fun `selective cache clear keeps the auth session`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearCache("search")
            advanceUntilIdle()

            verify(cacheManager).clearCacheType(CacheType.SEARCH)
            verify(authRepository, never()).clearSession()
            assertTrue(viewModel.cacheOperation.value is CacheOperationState.Success)
        }

    @Test
    fun `failed full clear does not clear the auth session`() =
        runTest {
            wheneverBlocking { cacheManager.clearAllCache() }.thenReturn(false)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearCache()
            advanceUntilIdle()

            verify(authRepository, never()).clearSession()
            assertTrue(viewModel.cacheOperation.value is CacheOperationState.Error)
        }
}
