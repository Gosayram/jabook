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

package com.jabook.app.jabook.compose.feature.miniplayer

import com.jabook.app.jabook.audio.PlayerPersistenceManager
import com.jabook.app.jabook.compose.data.model.DownloadStatus
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest {
    private val audioPlayerController: AudioPlayerController = mock()
    private val playerPersistenceManager: PlayerPersistenceManager = mock()
    private val booksRepository: BooksRepository = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val isPlayingFlow = MutableStateFlow(false)
    private val currentPositionFlow = MutableStateFlow(0L)
    private val durationFlow = MutableStateFlow(0L)
    private val currentBookIdFlow = MutableStateFlow<String?>(null)
    private val terminalErrorsFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    private val lastPlayedBookIdFlow = MutableStateFlow<String?>(null)

    private lateinit var viewModel: MiniPlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(audioPlayerController.isPlaying).thenReturn(isPlayingFlow)
        whenever(audioPlayerController.currentPosition).thenReturn(currentPositionFlow)
        whenever(audioPlayerController.duration).thenReturn(durationFlow)
        whenever(audioPlayerController.currentChapterIndex).thenReturn(MutableStateFlow(0))
        whenever(audioPlayerController.hasNextChapter).thenReturn(MutableStateFlow(false))
        whenever(audioPlayerController.hasPreviousChapter).thenReturn(MutableStateFlow(false))
        whenever(audioPlayerController.currentBookId).thenReturn(currentBookIdFlow)
        whenever(audioPlayerController.terminalPlaybackErrors).thenReturn(terminalErrorsFlow)
        whenever(playerPersistenceManager.lastPlayedBookId).thenReturn(lastPlayedBookIdFlow)
        viewModel = MiniPlayerViewModel(audioPlayerController, playerPersistenceManager, booksRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle play pause pauses when playing`() =
        runTest {
            isPlayingFlow.value = true

            viewModel.togglePlayPause()

            verify(audioPlayerController).pause()
            verify(audioPlayerController, never()).play()
        }

    @Test
    fun `toggle play pause plays when paused`() =
        runTest {
            isPlayingFlow.value = false

            viewModel.togglePlayPause()

            verify(audioPlayerController).play()
            verify(audioPlayerController, never()).pause()
        }

    @Test
    fun `play and pause delegate to controller`() =
        runTest {
            viewModel.play()
            viewModel.pause()

            verify(audioPlayerController).play()
            verify(audioPlayerController).pause()
        }

    @Test
    fun `seek by clamps position to duration`() =
        runTest {
            currentPositionFlow.value = 90_000L
            durationFlow.value = 60_000L

            viewModel.seekBy(60_000L)

            // 90s + 60s = 150s requested, clamped to 60s duration
            verify(audioPlayerController).seekTo(60_000L)
        }

    @Test
    fun `seek by does not exceed current position plus delta`() =
        runTest {
            currentPositionFlow.value = 10_000L
            durationFlow.value = 120_000L

            viewModel.seekBy(5_000L)

            verify(audioPlayerController).seekTo(15_000L)
        }

    private fun testBook(id: String = "book-1"): Book =
        Book(
            id = id,
            title = "Test Audiobook",
            author = "Author",
            coverUrl = null,
            description = null,
            totalDuration = 3_600_000.milliseconds,
            currentPosition = 0.milliseconds,
            progress = 0f,
            currentChapterIndex = 0,
            downloadStatus = DownloadStatus.DOWNLOADED,
            downloadProgress = 1.0f,
            localPath = "/data/test.mp3",
            addedDate = 1000L,
            lastPlayedDate = null,
            isFavorite = false,
            sourceUrl = null,
        )

    /** isVisible uses WhileSubscribed — tests subscribe eagerly, then read the state value. */
    private fun TestScope.collectIsVisible() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isVisible.collect {}
        }
    }

    @Test
    fun `mini player hidden when playback never started`() =
        runTest {
            // No last-played book, no loaded session — defaults from setUp
            collectIsVisible()

            assertFalse(viewModel.isVisible.value)
        }

    @Test
    fun `mini player hidden when persisted book is not loaded into player`() =
        runTest {
            whenever(booksRepository.getBook(any())).thenReturn(MutableStateFlow(testBook()))
            lastPlayedBookIdFlow.value = "book-1"
            // controller never loaded the book
            collectIsVisible()

            assertFalse(viewModel.isVisible.value)
        }

    @Test
    fun `mini player visible when book is loaded even while paused`() =
        runTest {
            whenever(booksRepository.getBook(any())).thenReturn(MutableStateFlow(testBook()))
            lastPlayedBookIdFlow.value = "book-1"
            currentBookIdFlow.value = "book-1"
            isPlayingFlow.value = false
            collectIsVisible()

            assertTrue(viewModel.isVisible.value)
        }

    @Test
    fun `mini player hidden on terminal playback error and recovers on replay`() =
        runTest {
            whenever(booksRepository.getBook(any())).thenReturn(MutableStateFlow(testBook()))
            lastPlayedBookIdFlow.value = "book-1"
            currentBookIdFlow.value = "book-1"
            collectIsVisible()
            assertTrue(viewModel.isVisible.value)

            // terminal error gates visibility off…
            terminalErrorsFlow.tryEmit("decode failed")
            assertFalse(viewModel.isVisible.value)

            // …and a real (re)start of playback clears it
            isPlayingFlow.value = true
            assertTrue(viewModel.isVisible.value)
        }
}
