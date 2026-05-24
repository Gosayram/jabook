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

package com.jabook.app.jabook.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jabook.app.jabook.compose.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JabookAppSettingsBadgeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsIcon_whenNoActiveDownloads_doesNotShowBadge() {
        composeTestRule.setContent {
            TopLevelDestinationIcon(
                destination = TopLevelDestination.SETTINGS,
                icon = TopLevelDestination.SETTINGS.unselectedIcon,
                activeDownloadsCount = 0,
            )
        }

        composeTestRule.onNodeWithTag(SETTINGS_BADGE_TEST_TAG).assertIsNotDisplayed()
    }

    @Test
    fun settingsIcon_whenActiveDownloadsExist_showsBadgeWithCount() {
        val activeDownloadsCount = 3

        composeTestRule.setContent {
            TopLevelDestinationIcon(
                destination = TopLevelDestination.SETTINGS,
                icon = TopLevelDestination.SETTINGS.unselectedIcon,
                activeDownloadsCount = activeDownloadsCount,
            )
        }

        composeTestRule.onNodeWithTag(SETTINGS_BADGE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(activeDownloadsCount.toString()).assertIsDisplayed()
    }
}
