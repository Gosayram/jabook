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

import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexProgress
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.data.worker.IndexingWorkScheduler
import com.jabook.app.jabook.compose.data.worker.IndexingWorker
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.auth.WithAuthorisedCheckUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class IndexingViewModelTest {
    private val forumIndexer: ForumIndexer = mock()
    private val authRepository: AuthRepository = mock()
    private val withAuthorisedCheckUseCase: WithAuthorisedCheckUseCase = mock()
    private val indexingWorkScheduler: IndexingWorkScheduler = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val userPreferencesRepository: UserPreferencesRepository = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val logger: Logger = mock()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: IndexingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)
        whenever(indexingWorkScheduler.observe()).thenReturn(emptyFlow())
        // Stub ForumIndexer StateFlows so ViewModel init can collect them
        whenever(forumIndexer.forumStatuses).thenReturn(MutableStateFlow(emptyList()))
        wheneverBlocking { userPreferencesRepository.getIndexingPageCursors() }.thenReturn(emptyMap())

        viewModel =
            IndexingViewModel(
                forumIndexer = forumIndexer,
                authRepository = authRepository,
                withAuthorisedCheckUseCase = withAuthorisedCheckUseCase,
                indexingWorkScheduler = indexingWorkScheduler,
                settingsRepository = settingsRepository,
                userPreferencesRepository = userPreferencesRepository,
                loggerFactory = loggerFactory,
            )

        // Stop the monitor started in init for deterministic JVM tests.
        stopMonitor()
    }

    private fun stopMonitor() {
        runCatching {
            val monitorField = IndexingViewModel::class.java.getDeclaredField("indexingMonitorJob")
            monitorField.isAccessible = true
            (monitorField.get(viewModel) as? Job)?.cancel()
            monitorField.set(viewModel, null)
        }
    }

    private fun startMonitor() {
        IndexingViewModel::class.java
            .getDeclaredMethod("startIndexingWorkMonitor")
            .apply { isAccessible = true }
            .invoke(viewModel)
    }

    private fun setIndexingStartTime(startTimeMs: Long) {
        val field = IndexingViewModel::class.java.getDeclaredField("_indexingStartTime")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<Long?>
        flow.value = startTimeMs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearIndex success resets index-related UI state`() =
        runTest(testDispatcher.scheduler) {
            whenever(forumIndexer.getIndexSize()).thenReturn(42)
            whenever(forumIndexer.clearIndex()).thenAnswer { }
            // Bring view model state to non-zero index before clearing.
            viewModel.getIndexSize()
            runCurrent()

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

    @Test
    fun `work manager progress keeps only its reported status`() =
        runTest(testDispatcher.scheduler) {
            val workInfo: WorkInfo = mock()
            whenever(workInfo.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(workInfo.progress).thenReturn(workDataOf("progress_message" to "Indexing fiction"))
            whenever(indexingWorkScheduler.observe()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(workInfo)))

            IndexingViewModel::class.java
                .getDeclaredMethod("startIndexingWorkMonitor")
                .apply { isAccessible = true }
                .invoke(viewModel)
            runCurrent()

            val progress = viewModel.indexingProgress.value as IndexingProgress.InProgress
            assertEquals("Indexing fiction", progress.detail.currentForumName)
            assertFalse(progress.detail.hasDetailedProgress)
            assertEquals(0, progress.detail.currentForumPage)
            assertEquals(0, progress.detail.topicsFound)
        }

    @Test
    fun `work manager progress maps crawl phase passthrough`() =
        runTest(testDispatcher.scheduler) {
            val workInfo: WorkInfo = mock()
            whenever(workInfo.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(workInfo.progress).thenReturn(
                workDataOf(
                    "progress_message" to "Forum 574",
                    IndexingWorker.KEY_PROGRESS_PHASE to IndexProgress.PHASE_FRESH,
                ),
            )
            whenever(indexingWorkScheduler.observe()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(workInfo)))

            IndexingViewModel::class.java
                .getDeclaredMethod("startIndexingWorkMonitor")
                .apply { isAccessible = true }
                .invoke(viewModel)
            runCurrent()

            val progress = viewModel.indexingProgress.value as IndexingProgress.InProgress
            assertEquals(IndexProgress.PHASE_FRESH, progress.detail.phase)
        }

    @Test
    fun `work manager progress without phase keeps phase null`() =
        runTest(testDispatcher.scheduler) {
            val workInfo: WorkInfo = mock()
            whenever(workInfo.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(workInfo.progress).thenReturn(workDataOf("progress_message" to "Forum 574"))
            whenever(indexingWorkScheduler.observe()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(workInfo)))

            startMonitor()
            runCurrent()

            val progress = viewModel.indexingProgress.value as IndexingProgress.InProgress
            assertEquals(null, progress.detail.phase)
        }

    @Test
    fun `work manager progress parses forum counters and reported percent`() =
        runTest(testDispatcher.scheduler) {
            val workInfo: WorkInfo = mock()
            whenever(workInfo.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(workInfo.progress).thenReturn(
                workDataOf(
                    IndexingWorker.KEY_PROGRESS_MESSAGE to "Forum 574",
                    IndexingWorker.KEY_PROGRESS_FORUMS_DONE to 2,
                    IndexingWorker.KEY_PROGRESS_FORUMS_TOTAL to 10,
                    IndexingWorker.KEY_PROGRESS_TOPICS to 120,
                    IndexingWorker.KEY_PROGRESS_PERCENT to 25,
                ),
            )
            whenever(indexingWorkScheduler.observe()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(workInfo)))

            startMonitor()
            runCurrent()

            val progress = viewModel.indexingProgress.value as IndexingProgress.InProgress
            assertTrue(progress.detail.hasDetailedProgress)
            assertEquals(2, progress.detail.totalForumsCompleted)
            assertEquals(10, progress.detail.totalForums)
            assertEquals(120, progress.detail.topicsFound)
            assertEquals(0.25f, progress.detail.percentComplete)
        }

    @Test
    fun `work manager completion computes duration from indexing start time`() =
        runTest(testDispatcher.scheduler) {
            whenever(forumIndexer.getIndexSize()).thenReturn(42)
            val running: WorkInfo = mock()
            whenever(running.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(running.progress).thenReturn(workDataOf())
            val succeeded: WorkInfo = mock()
            whenever(succeeded.state).thenReturn(WorkInfo.State.SUCCEEDED)
            whenever(succeeded.progress).thenReturn(workDataOf())
            whenever(succeeded.outputData).thenReturn(workDataOf())
            whenever(indexingWorkScheduler.observe()).thenReturn(
                kotlinx.coroutines.flow.flow {
                    emit(listOf(running))
                    emit(listOf(succeeded))
                },
            )

            setIndexingStartTime(System.currentTimeMillis() - 5_000L)
            startMonitor()
            runCurrent()

            val progress = viewModel.indexingProgress.value as IndexingProgress.Completed
            assertEquals(42, progress.totalTopics)
            assertTrue("duration=$progress", progress.durationMs >= 5_000L)
        }

    @Test
    fun `pause with persisted cursors enters Paused state`() =
        runTest(testDispatcher.scheduler) {
            val running: WorkInfo = mock()
            whenever(running.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(running.progress).thenReturn(workDataOf())
            val cancelled: WorkInfo = mock()
            whenever(cancelled.state).thenReturn(WorkInfo.State.CANCELLED)
            whenever(cancelled.progress).thenReturn(workDataOf())
            whenever(cancelled.outputData).thenReturn(workDataOf())
            whenever(indexingWorkScheduler.observe()).thenReturn(
                kotlinx.coroutines.flow.flow {
                    emit(listOf(running))
                    emit(listOf(cancelled))
                },
            )
            wheneverBlocking { userPreferencesRepository.getIndexingPageCursors() }.thenReturn(mapOf("574" to 3))

            setIsIndexing(true)
            viewModel.pauseIndexing()
            verify(indexingWorkScheduler).cancel()
            startMonitor()
            runCurrent()

            assertTrue(viewModel.indexingProgress.value is IndexingProgress.Paused)
        }

    @Test
    fun `pause without persisted cursors does not enter Paused state`() =
        runTest(testDispatcher.scheduler) {
            val running: WorkInfo = mock()
            whenever(running.state).thenReturn(WorkInfo.State.RUNNING)
            whenever(running.progress).thenReturn(workDataOf())
            val cancelled: WorkInfo = mock()
            whenever(cancelled.state).thenReturn(WorkInfo.State.CANCELLED)
            whenever(cancelled.progress).thenReturn(workDataOf())
            whenever(cancelled.outputData).thenReturn(workDataOf())
            whenever(indexingWorkScheduler.observe()).thenReturn(
                kotlinx.coroutines.flow.flow {
                    emit(listOf(running))
                    emit(listOf(cancelled))
                },
            )
            whenever(forumIndexer.getIndexSize()).thenReturn(42)

            setIsIndexing(true)
            viewModel.pauseIndexing()
            startMonitor()
            runCurrent()

            assertFalse(viewModel.indexingProgress.value is IndexingProgress.Paused)
        }

    @Test
    fun `startIndexingInBackground keeps an existing start time`() =
        runTest(testDispatcher.scheduler) {
            val existingStart = 1_234_567L
            setIndexingStartTime(existingStart)

            viewModel.startIndexingInBackground(mock())
            runCurrent()

            assertEquals(existingStart, viewModel.indexingStartTime.value)
        }

    private fun setIsIndexing(value: Boolean) {
        val field = IndexingViewModel::class.java.getDeclaredField("_isIndexing")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<Boolean>).value = value
    }
}
