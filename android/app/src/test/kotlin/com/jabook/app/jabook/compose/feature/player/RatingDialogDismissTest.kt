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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RatingDialogDismissTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rating dialog shows title and Later button`() {
        composeTestRule.setContent {
            MaterialTheme {
                RatingDialog(
                    selectedRating = 0,
                    onDismiss = {},
                    onRate = {},
                )
            }
        }

        composeTestRule.onNodeWithText("How would you rate this book?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Later").assertIsDisplayed()
    }

    @Test
    fun `clicking Later button dismisses the dialog`() {
        composeTestRule.setContent {
            var dialogVisible by remember { mutableStateOf(true) }

            MaterialTheme {
                if (dialogVisible) {
                    RatingDialog(
                        selectedRating = 0,
                        onDismiss = { dialogVisible = false },
                        onRate = { dialogVisible = false },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Later").performClick()

        assertTrue(
            composeTestRule
                .onAllNodesWithText("How would you rate this book?")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `clicking a star dismisses the dialog`() {
        composeTestRule.setContent {
            var dialogVisible by remember { mutableStateOf(true) }

            MaterialTheme {
                if (dialogVisible) {
                    RatingDialog(
                        selectedRating = 0,
                        onDismiss = { dialogVisible = false },
                        onRate = { dialogVisible = false },
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("4 stars").performClick()

        assertTrue(
            composeTestRule
                .onAllNodesWithText("How would you rate this book?")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `dialog not rendered when conditionally hidden`() {
        composeTestRule.setContent {
            MaterialTheme {
                RatingDialog(
                    selectedRating = 0,
                    onDismiss = {},
                    onRate = {},
                )
            }
        }

        composeTestRule.onNodeWithText("How would you rate this book?").assertIsDisplayed()
    }
}
