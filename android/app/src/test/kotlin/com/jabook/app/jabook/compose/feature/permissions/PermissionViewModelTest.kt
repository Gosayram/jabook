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

package com.jabook.app.jabook.compose.feature.permissions

import android.content.Context
import android.os.Environment
import com.jabook.app.jabook.compose.data.model.UserData
import com.jabook.app.jabook.compose.data.permissions.PermissionManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private val permissionManager: PermissionManager = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val userPreferencesRepository: UserPreferencesRepository = mock()
    private val userDataFlow = MutableStateFlow(UserData())
    private val externalFilesDir = java.io.File("/tmp/jabook-test-external-downloads")
    private val filesDir = java.io.File("/tmp/jabook-test-files")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context =
            mock {
                on { getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } doReturn externalFilesDir
                on { filesDir } doReturn filesDir
            }
        whenever(userPreferencesRepository.userData).thenReturn(userDataFlow)
        whenever(permissionManager.hasNotificationPermission()).thenReturn(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `hasStoragePermission is false when full access and fallback are both unavailable`() =
        runTest {
            whenever(permissionManager.hasStoragePermission()).thenReturn(false)

            val viewModel = PermissionViewModel(context, permissionManager, userPreferencesRepository, settingsRepository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.hasFullStoragePermission)
            assertFalse(state.hasStorageFallbackEnabled)
            assertFalse(state.hasStoragePermission)
        }

    @Test
    fun `hasStoragePermission is true when fallback mode is enabled without full access`() =
        runTest {
            whenever(permissionManager.hasStoragePermission()).thenReturn(false)
            userDataFlow.value = UserData(storageFallbackEnabled = true)

            val viewModel = PermissionViewModel(context, permissionManager, userPreferencesRepository, settingsRepository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.hasFullStoragePermission)
            assertTrue(state.hasStorageFallbackEnabled)
            assertTrue(state.hasStoragePermission)
        }

    @Test
    fun `enableStorageFallbackMode persists fallback and updates effective permission state`() =
        runTest {
            whenever(permissionManager.hasStoragePermission()).thenReturn(false)
            doAnswer {
                userDataFlow.value = userDataFlow.value.copy(storageFallbackEnabled = true)
                Unit
            }.whenever(userPreferencesRepository).setStorageFallbackEnabled(true)

            val viewModel = PermissionViewModel(context, permissionManager, userPreferencesRepository, settingsRepository)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasStoragePermission)

            viewModel.enableStorageFallbackMode()
            advanceUntilIdle()

            verify(userPreferencesRepository).setStorageFallbackEnabled(true)
            val expectedPath =
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                    ?: context.filesDir.absolutePath
            verify(settingsRepository).updateDownloadPath(expectedPath)
            assertTrue(viewModel.uiState.value.hasStorageFallbackEnabled)
            assertTrue(viewModel.uiState.value.hasStoragePermission)
        }
}
