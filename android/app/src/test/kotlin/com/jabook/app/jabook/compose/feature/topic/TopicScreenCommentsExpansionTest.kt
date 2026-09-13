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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.RutrackerComment
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test: expanding comments used to crash with IllegalStateException
 * ("Vertically scrollable component was measured with an infinity maximum height
 * constraint") because a nested LazyColumn was hosted inside an item of the outer
 * LazyColumn. Comments are now flattened into the outer list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Category(com.jabook.app.jabook.test.SlowTest::class)
public class TopicScreenCommentsExpansionTest {
    @get:Rule
    public val composeTestRule = createComposeRule()

    @Test
    public fun `expanding comments renders them without nested-scroll crash`() {
        val details =
            RutrackerTopicDetails(
                topicId = "t1",
                title = "Test Topic",
                author = null,
                performer = null,
                category = "cat",
                size = "1 GB",
                seeders = 1,
                leechers = 1,
                magnetUrl = null,
                torrentUrl = "https://example.com/file.torrent",
                coverUrl = null,
                genres = emptyList(),
                addedDate = null,
                duration = null,
                bitrate = null,
                audioCodec = null,
                description = null,
                relatedBooks = emptyList(),
                comments =
                    listOf(
                        RutrackerComment(id = "c1", author = "Alice", date = "2026-01-01", text = "first"),
                        RutrackerComment(id = "c2", author = "Bob", date = "2026-01-02", text = "second"),
                    ),
            )
        val viewModel = mock<TopicViewModel>()
        whenever(viewModel.uiState).thenReturn(MutableStateFlow(TopicUiState.Success(details)))
        whenever(viewModel.isRefreshing).thenReturn(MutableStateFlow(false))
        whenever(viewModel.authStatus).thenReturn(MutableStateFlow(AuthStatus.Unauthenticated))
        whenever(viewModel.isLoadingMoreComments).thenReturn(MutableStateFlow(false))
        whenever(viewModel.messages).thenReturn(emptyFlow())

        composeTestRule.setContent {
            MaterialTheme {
                TopicScreen(
                    topicId = "t1",
                    onNavigateBack = {},
                    onNavigateToTopic = {},
                    viewModel = viewModel,
                )
            }
        }

        // The crash happened during layout of the expanded (nested LazyColumn) state.
        composeTestRule.onNodeWithText("Expand").performClick()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
    }
}
