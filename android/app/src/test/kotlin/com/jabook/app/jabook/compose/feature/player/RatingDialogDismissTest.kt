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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
                TestRatingDialog(
                    show = true,
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
                    TestRatingDialog(
                        show = true,
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
                    TestRatingDialog(
                        show = true,
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
    fun `dialog is not shown when show is false`() {
        composeTestRule.setContent {
            MaterialTheme {
                TestRatingDialog(
                    show = false,
                    onDismiss = {},
                    onRate = {},
                )
            }
        }

        assertTrue(
            composeTestRule
                .onAllNodesWithText("How would you rate this book?")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}

@Composable
private fun TestRatingDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit,
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("How would you rate this book?") },
            text = { StarRatingRow(selected = 0, onRate = onRate) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            },
        )
    }
}
