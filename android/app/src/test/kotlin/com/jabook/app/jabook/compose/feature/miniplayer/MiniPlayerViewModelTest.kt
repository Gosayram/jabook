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
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest {
    private val audioPlayerController: AudioPlayerController = mock()
    private val playerPersistenceManager: PlayerPersistenceManager = mock()
    private val booksRepository: BooksRepository = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val isPlayingFlow = MutableStateFlow(false)
    private val currentPositionFlow = MutableStateFlow(0L)
    private val durationFlow = MutableStateFlow(0L)

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
        whenever(playerPersistenceManager.lastPlayedBookId).thenReturn(MutableStateFlow<String?>(null))
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
}
