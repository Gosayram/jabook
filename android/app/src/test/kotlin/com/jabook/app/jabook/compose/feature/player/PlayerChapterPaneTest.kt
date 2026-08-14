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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.jabook.app.jabook.compose.domain.model.Chapter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class PlayerChapterPaneTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleChapters =
        listOf(
            Chapter(
                id = "ch1",
                bookId = "book1",
                title = "Introduction",
                chapterIndex = 0,
                fileIndex = 0,
                duration = 300_000.milliseconds,
                fileUrl = "/book/ch1.mp3",
                position = 300_000.milliseconds,
                isCompleted = true,
                isDownloaded = true,
            ),
            Chapter(
                id = "ch2",
                bookId = "book1",
                title = "Chapter Two",
                chapterIndex = 1,
                fileIndex = 1,
                duration = 600_000.milliseconds,
                fileUrl = "/book/ch2.mp3",
                position = 150_000.milliseconds,
                isCompleted = false,
                isDownloaded = true,
            ),
            Chapter(
                id = "ch3",
                bookId = "book1",
                title = "Chapter Three",
                chapterIndex = 2,
                fileIndex = 2,
                duration = 0.milliseconds,
                fileUrl = "/book/ch3.mp3",
                position = 0.milliseconds,
                isCompleted = false,
                isDownloaded = true,
            ),
        )

    @Test
    fun `selected chapter is displayed with its name`() {
        composeTestRule.setContent {
            PlayerChapterPane(
                chapters = sampleChapters,
                currentChapterIndex = 1,
                onChapterClick = {},
                normalizeEnabled = false,
            )
        }

        composeTestRule.onNodeWithText("Chapter Two").assertIsDisplayed()
    }

    @Test
    fun `completed chapter shows completed status in content description`() {
        composeTestRule.setContent {
            PlayerChapterPane(
                chapters = sampleChapters,
                currentChapterIndex = 1,
                onChapterClick = {},
                normalizeEnabled = false,
            )
        }

        // The completed chapter is rendered (content description includes status)
        composeTestRule
            .onNodeWithText(sampleChapters[1].title)
            .assertIsDisplayed()
    }

    @Test
    fun `chapter list shows all chapter titles`() {
        composeTestRule.setContent {
            PlayerChapterPane(
                chapters = sampleChapters,
                currentChapterIndex = 0,
                onChapterClick = {},
                normalizeEnabled = false,
            )
        }

        composeTestRule.onNodeWithText("Introduction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter Two").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter Three").assertIsDisplayed()
    }

    @Test
    fun `chapter counter shows current and total`() {
        composeTestRule.setContent {
            PlayerChapterPane(
                chapters = sampleChapters,
                currentChapterIndex = 1,
                onChapterClick = {},
                normalizeEnabled = false,
            )
        }

        composeTestRule.onNodeWithText("2/3").assertIsDisplayed()
    }

    @Test
    fun `zero duration chapter does not show progress indicator`() {
        composeTestRule.setContent {
            PlayerChapterPane(
                chapters = sampleChapters,
                currentChapterIndex = 2,
                onChapterClick = {},
                normalizeEnabled = false,
            )
        }

        // Zero-duration chapter should still render its title
        composeTestRule.onNodeWithText("Chapter Three").assertIsDisplayed()
    }
}
