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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `loadTopicDetails with multiple pages loads last page first`() =
        runTest {
            // Given
            val lastPageComments =
                listOf(
                    RutrackerComment("5", "user5", "2024-01-05", "newest comment", null, null),
                    RutrackerComment("6", "user6", "2024-01-06", "newest comment 2", null, null),
                )
            val detailsLastPage =
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
                    coverUrl = null,
                    genres = emptyList(),
                    addedDate = "2023-01-01",
                    duration = null,
                    bitrate = null,
                    audioCodec = null,
                    description = null,
                    relatedBooks = emptyList(),
                    comments = lastPageComments,
                    currentPage = 3,
                    totalPages = 3,
                )
            whenever(rutrackerRepository.getTopicDetailsPage("12345", 3)).thenReturn(Result.Success(detailsLastPage))

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
            val state = viewModel.uiState.value as? TopicUiState.Success
            assertTrue(state != null)

            // Comments should be in reverse order (newest first)
            assertEquals(2, state!!.details.comments.size)
            assertEquals("6", state.details.comments[0].id) // newest first
            assertEquals("5", state.details.comments[1].id)
        }

    @Test
    fun `loadMoreComments loads previous page in reverse order`() =
        runTest {
            // Given
            val page3Comments =
                listOf(
                    RutrackerComment("5", "user5", "2024-01-05", "page3 comment1", null, null),
                    RutrackerComment("6", "user6", "2024-01-06", "page3 comment2", null, null),
                )
            val page2Comments =
                listOf(
                    RutrackerComment("3", "user3", "2024-01-03", "page2 comment1", null, null),
                    RutrackerComment("4", "user4", "2024-01-04", "page2 comment2", null, null),
                )

            val detailsPage3 =
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
                    coverUrl = null,
                    genres = emptyList(),
                    addedDate = "2023-01-01",
                    duration = null,
                    bitrate = null,
                    audioCodec = null,
                    description = null,
                    relatedBooks = emptyList(),
                    comments = page3Comments,
                    currentPage = 3,
                    totalPages = 3,
                )

            val detailsPage2 =
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
                    coverUrl = null,
                    genres = emptyList(),
                    addedDate = "2023-01-01",
                    duration = null,
                    bitrate = null,
                    audioCodec = null,
                    description = null,
                    relatedBooks = emptyList(),
                    comments = page2Comments,
                    currentPage = 2,
                    totalPages = 3,
                )

            whenever(rutrackerRepository.getTopicDetailsPage("12345", 3)).thenReturn(Result.Success(detailsPage3))
            whenever(rutrackerRepository.getTopicDetailsPage("12345", 2)).thenReturn(Result.Success(detailsPage2))

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

            // Load more (should fetch page 2)
            viewModel.loadMoreComments()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as? TopicUiState.Success
            assertTrue(state != null)

            // Should have 4 comments total, ordered newest to oldest
            assertEquals(4, state!!.details.comments.size)
            assertEquals("6", state.details.comments[0].id) // newest (from page 3)
            assertEquals("5", state.details.comments[1].id) // from page 3
            assertEquals("4", state.details.comments[2].id) // from page 2
            assertEquals("3", state.details.comments[3].id) // oldest (from page 2)
        }
}
