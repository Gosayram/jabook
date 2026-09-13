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

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScanSettingsViewModelTest {
    private val repository = mock<BooksRepository>()
    private val loggerFactory = mock<LoggerFactory>()
    private val dispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        val logger = mock<Logger>()
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)
        whenever(loggerFactory.get(any<kotlin.reflect.KClass<*>>())).thenReturn(logger)
        Dispatchers.setMain(dispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `addScanPath normalizes trailing slash and whitespace before persisting`() =
        runTest(dispatcher) {
            val viewModel = ScanSettingsViewModel(repository, loggerFactory)

            viewModel.addScanPath("  /storage/emulated/0/Audiobooks/  ")
            advanceUntilIdle()

            verify(repository).addScanPath(eq("/storage/emulated/0/Audiobooks"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `addScanPath rejects raw content uri instead of persisting a dead row`() =
        runTest(dispatcher) {
            val viewModel = ScanSettingsViewModel(repository, loggerFactory)

            viewModel.addScanPath("content://com.android.providers.downloads.documents/1234")
            advanceUntilIdle()

            verify(repository, never()).addScanPath(any<String>())
            verify(repository, never()).refresh()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `removeScanPath delegates to repository`() =
        runTest(dispatcher) {
            val viewModel = ScanSettingsViewModel(repository, loggerFactory)

            viewModel.removeScanPath("/storage/Books")
            advanceUntilIdle()

            verify(repository, times(1)).removeScanPath("/storage/Books")
        }
}
