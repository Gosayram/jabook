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

package com.jabook.app.jabook.compose.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.jabook.app.jabook.audio.AudioVisualizerStateBridge
import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import com.jabook.app.jabook.data.lyrics.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies the setScrubbingMode idempotence guard: the UI fires onScrubbingMode
 * on every drag frame, so repeated calls with the same value must not spam the
 * player controller (PlayerViewModel.setScrubbingMode).
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class PlayerViewModelScrubbingTest {
    private val playerController: AudioPlayerController = mock()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerStats).thenReturn(MutableStateFlow(PlayerStats()))
        whenever(playerController.currentPosition).thenReturn(MutableStateFlow(0L))
        whenever(playerController.isPlaying).thenReturn(MutableStateFlow(false))
        whenever(playerController.currentChapterIndex).thenReturn(MutableStateFlow(0))
        whenever(playerController.currentBookId).thenReturn(MutableStateFlow<String?>(null))
        whenever(playerController.hasNextChapter).thenReturn(MutableStateFlow(false))
        whenever(playerController.hasPreviousChapter).thenReturn(MutableStateFlow(false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setScrubbingMode forwards each distinct value once and ignores repeats`() {
        val viewModel = createViewModel()

        viewModel.setScrubbingMode(true)
        viewModel.setScrubbingMode(true)
        viewModel.setScrubbingMode(true)

        verify(playerController, times(1)).setScrubbingMode(true)

        viewModel.setScrubbingMode(false)
        viewModel.setScrubbingMode(false)

        verify(playerController, times(1)).setScrubbingMode(false)
    }

    @Test
    fun `setScrubbingMode still propagates real value transitions`() {
        val viewModel = createViewModel()

        viewModel.setScrubbingMode(true)
        viewModel.setScrubbingMode(false)
        viewModel.setScrubbingMode(true)

        verify(playerController, times(2)).setScrubbingMode(true)
        verify(playerController, times(1)).setScrubbingMode(false)
    }

    private fun createViewModel(): PlayerViewModel =
        PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "book-1", "chapterIndex" to 0)),
            getBookDetailsUseCase =
                mock {
                    whenever(it.invoke(any())).thenReturn(emptyFlow())
                },
            getChaptersUseCase =
                mock {
                    whenever(it.invoke(any())).thenReturn(emptyFlow())
                },
            playerController = playerController,
            settingsRepository =
                mock {
                    whenever(it.userPreferences).thenReturn(emptyFlow())
                    whenever(it.audioVisualizerMode).thenReturn(emptyFlow())
                },
            userPreferencesRepository =
                mock {
                    whenever(it.userData).thenReturn(emptyFlow())
                },
            sleepTimerRepository =
                mock {
                    whenever(it.timerState).thenReturn(MutableStateFlow(SleepTimerState.Idle))
                },
            updateBookSettingsUseCase = mock<UpdateBookSettingsUseCase>(),
            booksRepository = mock<BooksRepository>(),
            bookmarkRepository =
                mock {
                    whenever(it.observeBookmarks(any())).thenReturn(emptyFlow())
                },
            playbackPositionRepository = mock<PlaybackPositionRepository>(),
            lyricsRepository = mock<LyricsRepository>(),
            audioVisualizerStateBridge = AudioVisualizerStateBridge(),
            listeningSessionRepository = mock<ListeningSessionRepository>(),
            loggerFactory =
                mock<LoggerFactory> {
                    whenever(it.get(any<String>())).thenReturn(NoOpLogger)
                },
            audioMetadataParser = mock<AudioMetadataParser>(),
            context = mock<Context>(),
        )
}
