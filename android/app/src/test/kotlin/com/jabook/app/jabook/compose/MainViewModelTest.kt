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

package com.jabook.app.jabook.compose

import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.data.model.UserData
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class MainViewModelTest {
    private val userPreferencesRepository: UserPreferencesRepository = mock()
    private val settingsRepository: SettingsRepository = mock()

    private val testDispatcher = StandardTestDispatcher()
    private val userDataFlow = MutableStateFlow(defaultUserData)
    private val userPreferencesFlow = MutableStateFlow(defaultUserPreferences)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(userPreferencesRepository.userData).thenReturn(userDataFlow)
        whenever(settingsRepository.userPreferences).thenReturn(userPreferencesFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(MainActivityUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `state transitions to Success with correct userData and dynamic colors`() =
        runTest(testDispatcher.scheduler) {
            val viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value as MainActivityUiState.Success
            assertEquals(defaultUserData, state.userData)
            assertTrue(state.useDynamicColors)
            collector.cancel()
        }

    @Test
    fun `userData change updates state`() =
        runTest(testDispatcher.scheduler) {
            val viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val updatedUserData = defaultUserData.copy(theme = AppTheme.AMOLED)
            userDataFlow.value = updatedUserData
            advanceUntilIdle()

            val state = viewModel.uiState.value as MainActivityUiState.Success
            assertEquals(AppTheme.AMOLED, state.userData.theme)
            collector.cancel()
        }

    @Test
    fun `useDynamicColors false propagates correctly`() =
        runTest(testDispatcher.scheduler) {
            userPreferencesFlow.value =
                UserPreferences
                    .newBuilder()
                    .setUseDynamicColors(false)
                    .build()

            val viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value as MainActivityUiState.Success
            assertFalse(state.useDynamicColors)
            collector.cancel()
        }

    @Test
    fun `default userData has expected values`() =
        runTest(testDispatcher.scheduler) {
            val viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value as MainActivityUiState.Success
            assertEquals(AppTheme.SYSTEM, state.userData.theme)
            assertEquals(BookSortOrder.BY_ACTIVITY, state.userData.sortOrder)
            assertEquals(LibraryViewMode.LIST_COMPACT, state.userData.viewMode)
            assertTrue(state.userData.autoPlayNext)
            assertEquals(1.0f, state.userData.playbackSpeed, 0.001f)
            assertEquals("ru", state.userData.languageCode)
            collector.cancel()
        }

    @Test
    fun `multiple rapid userData emissions settle to latest`() =
        runTest(testDispatcher.scheduler) {
            val viewModel = createViewModel()
            val collector = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            userDataFlow.value = defaultUserData.copy(playbackSpeed = 1.5f)
            userDataFlow.value = defaultUserData.copy(playbackSpeed = 2.0f)
            userDataFlow.value = defaultUserData.copy(playbackSpeed = 0.75f)
            advanceUntilIdle()

            val state = viewModel.uiState.value as MainActivityUiState.Success
            assertEquals(0.75f, state.userData.playbackSpeed, 0.001f)
            collector.cancel()
        }

    private fun createViewModel(): MainViewModel =
        MainViewModel(
            userPreferencesRepository = userPreferencesRepository,
            settingsRepository = settingsRepository,
        )

    private companion object {
        val defaultUserData = UserData()
        val defaultUserPreferences =
            UserPreferences
                .newBuilder()
                .setUseDynamicColors(true)
                .build()
    }
}
