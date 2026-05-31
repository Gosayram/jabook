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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SnackbarUndoTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `snackbar displays message and action label`() {
        val snackbarHostState = SnackbarHostState()

        composeTestRule.setContent {
            MaterialTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                ) {
                    LaunchedEffect(Unit) {
                        snackbarHostState.showSnackbar(
                            message = "Bookmark added",
                            actionLabel = "Undo",
                        )
                    }
                }
            }
        }

        composeTestRule.waitUntil(5000L) {
            composeTestRule
                .onAllNodesWithText("Bookmark added")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Bookmark added").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun `clicking undo action returns ActionPerformed`() {
        val snackbarHostState = SnackbarHostState()
        var result: SnackbarResult? = null

        composeTestRule.setContent {
            val hostState = remember { snackbarHostState }
            MaterialTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = hostState) },
                ) {
                    LaunchedEffect(Unit) {
                        result =
                            hostState.showSnackbar(
                                message = "Bookmark added",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Indefinite,
                            )
                    }
                }
            }
        }

        composeTestRule.waitUntil(5000L) {
            composeTestRule
                .onAllNodesWithText("Undo")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Undo").performClick()

        composeTestRule.waitUntil(5000L) { result != null }
        assertEquals(SnackbarResult.ActionPerformed, result)
    }

    @Test
    fun `snackbar without action label shows only message`() {
        val snackbarHostState = SnackbarHostState()

        composeTestRule.setContent {
            MaterialTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                ) {
                    LaunchedEffect(Unit) {
                        snackbarHostState.showSnackbar(
                            message = "Thanks! You rated this book 4/5.",
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                }
            }
        }

        composeTestRule.waitUntil(5000L) {
            composeTestRule
                .onAllNodesWithText("Thanks! You rated this book 4/5.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Thanks! You rated this book 4/5.").assertIsDisplayed()
        assertTrue(
            composeTestRule
                .onAllNodesWithText("Undo")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
