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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jabook.app.jabook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

public class SleepTimerSheetUiTest {
    @get:Rule
    public val composeTestRule = createComposeRule()

    @Test
    public fun `preset chips render for every provided duration`() {
        val durations = listOf(5, 10, 15)
        val labels = mutableStateListOf<String>()
        composeTestRule.setContent {
            MaterialTheme {
                labels.clear()
                durations.forEach { minutes ->
                    labels += pluralStringResource(R.plurals.durationMinutesFull, minutes, minutes)
                }
                SleepTimerPresetChips(
                    durations = durations,
                    onPresetClick = {},
                )
            }
        }

        labels.forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    public fun `clicking preset chip triggers start and dismiss sequence`() {
        val durations = listOf(5, 10, 15)
        var startedAt: Int? = null
        var dismissed = false
        val labels = mutableStateListOf<String>()

        composeTestRule.setContent {
            MaterialTheme {
                labels.clear()
                durations.forEach { minutes ->
                    labels += pluralStringResource(R.plurals.durationMinutesFull, minutes, minutes)
                }
                SleepTimerPresetChips(
                    durations = durations,
                    onPresetClick = { minutes ->
                        startedAt = minutes
                        dismissed = true
                    },
                )
            }
        }

        val targetMinutes = 10
        val label = labels[durations.indexOf(targetMinutes)]
        composeTestRule.onNodeWithText(label).performClick()

        composeTestRule.runOnIdle {
            assertEquals(targetMinutes, startedAt)
            assertTrue(dismissed)
        }
    }
}
