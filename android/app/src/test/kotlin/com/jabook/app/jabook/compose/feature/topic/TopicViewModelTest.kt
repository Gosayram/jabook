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

package com.jabook.app.jabook.compose.feature.topic

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerComment
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.auth.WithAuthorisedCheckUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TopicViewModelTest {
    private val rutrackerRepository: RutrackerRepository = mock()
    private val authRepository: AuthRepository = mock()
    private val torrentManager: TorrentManager = mock()
    private val rutrackerApi: RutrackerApi = mock()
    private val mirrorManager: MirrorManager = mock()
    private val withAuthorisedCheckUseCase: WithAuthorisedCheckUseCase = mock()
    private val avatarPreloader: AvatarPreloader = mock()
    private val context: Context = mock()
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("topicId" to "12345"))

    private lateinit var viewModel: TopicViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val authStatusFlow = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepository.authStatus).thenReturn(authStatusFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTopicDetails success calls avatarPreloader`() =
        runTest {
            // Given
            val comments =
                listOf(
                    RutrackerComment("1", "user1", "date", "text", null, "http://avatar1.jpg"),
                    RutrackerComment("2", "user2", "date", "text", null, "http://avatar2.jpg"),
                )
            val details =
                RutrackerTopicDetails(
                    topicId = "12345",
                    title = "Test Topic",
                    author = "Author",
                    performer = null,
                    category = "Audiobook",
                    size = "100 MB",
                    seeders = 10,
                    leechers = 2,
                    magnetUrl = "magnet:?xt=urn:btih:123",
                    torrentUrl = "http://torrent",
                    coverUrl = "http://cover.jpg",
                    genres = emptyList(),
                    addedDate = "2023-01-01",
                    duration = null,
                    bitrate = null,
                    audioCodec = null,
                    description = "Desc",
                    relatedBooks = emptyList(),
                    comments = comments,
                )
            whenever(rutrackerRepository.getTopicDetails("12345")).thenReturn(Result.Success(details))

            // When
            viewModel =
                TopicViewModel(
                    rutrackerRepository,
                    authRepository,
                    torrentManager,
                    rutrackerApi,
                    mirrorManager,
                    withAuthorisedCheckUseCase,
                    avatarPreloader,
                    context,
                    savedStateHandle,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            verify(avatarPreloader).preloadAvatars(any(), any())
            // Verify specifically with the comments list
            verify(avatarPreloader).preloadAvatars(context, comments)
        }
}
