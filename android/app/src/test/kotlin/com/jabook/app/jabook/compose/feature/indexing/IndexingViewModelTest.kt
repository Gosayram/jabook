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

package com.jabook.app.jabook.compose.feature.indexing

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.auth.WithAuthorisedCheckUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class IndexingViewModelTest {
    private val forumIndexer: ForumIndexer = mock()
    private val authRepository: AuthRepository = mock()
    private val withAuthorisedCheckUseCase: WithAuthorisedCheckUseCase = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val logger: Logger = mock()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: IndexingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)

        viewModel =
            IndexingViewModel(
                forumIndexer = forumIndexer,
                authRepository = authRepository,
                withAuthorisedCheckUseCase = withAuthorisedCheckUseCase,
                loggerFactory = loggerFactory,
            )

        // Stop infinite service monitor loop started in init for deterministic JVM tests.
        runCatching {
            val monitorField = IndexingViewModel::class.java.getDeclaredField("serviceMonitorJob")
            monitorField.isAccessible = true
            (monitorField.get(viewModel) as? Job)?.cancel()
            monitorField.set(viewModel, null)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearIndex success resets index-related UI state`() =
        runTest {
            whenever(forumIndexer.getIndexSize()).thenReturn(42)
            whenever(forumIndexer.clearIndex()).thenAnswer { }
            // Bring view model state to non-zero index before clearing.
            viewModel.getIndexSize()
            testDispatcher.scheduler.runCurrent()

            val cleared = viewModel.clearIndex()

            assertTrue(cleared)
            assertEquals(0, viewModel.indexSize.value)
            assertFalse(viewModel.isIndexing.value)
            assertEquals(IndexingProgress.Idle, viewModel.indexingProgress.value)
            assertFalse(viewModel.clearingInProgress.value)
        }

    @Test
    fun `clearIndex failure still resets clearingInProgress`() =
        runTest {
            whenever(forumIndexer.clearIndex()).thenThrow(RuntimeException("boom"))

            val cleared = viewModel.clearIndex()

            assertFalse(cleared)
            assertFalse(viewModel.clearingInProgress.value)
        }
}
