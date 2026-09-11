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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
public class ErrorScreenTest {
    @get:Rule
    public val composeTestRule = createComposeRule()

    @Test
    public fun `without details no view-details button is shown`() {
        composeTestRule.setContent {
            MaterialTheme {
                ErrorScreen(message = "boom")
            }
        }

        composeTestRule.onNodeWithText("boom").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("View details").assertCountEquals(0)
    }

    @Test
    public fun `with details button opens dialog with full text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ErrorScreen(message = "boom", details = "full stack trace details")
            }
        }

        composeTestRule.onNodeWithText("View details").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("full stack trace details").assertIsDisplayed()
    }

    @Test
    public fun `copy action shows transient copied feedback`() {
        composeTestRule.setContent {
            MaterialTheme {
                ErrorScreen(message = "boom", details = "full stack trace details")
            }
        }

        composeTestRule.onNodeWithText("View details").performClick()
        composeTestRule.onNodeWithText("Copy to clipboard").performClick()
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()
    }
}
