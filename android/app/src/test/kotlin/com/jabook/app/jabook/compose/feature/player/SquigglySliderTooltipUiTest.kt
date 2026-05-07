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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

public class SquigglySliderTooltipUiTest {
    @get:Rule
    public val composeTestRule = createComposeRule()

    @Test
    public fun `tooltip is hidden by default and appears while interacting`() {
        var sliderValue by mutableStateOf(0.3f)
        val expectedTooltip = "fmt:0.30"

        composeTestRule.setContent {
            MaterialTheme {
                SquigglySlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueFormatter = { "fmt:${"%.2f".format(it)}" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        composeTestRule.onNodeWithTag(SQUIGGLY_SLIDER_TOOLTIP_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(SQUIGGLY_SLIDER_TAG).performTouchInput {
            down(center)
        }

        composeTestRule.onNodeWithTag(SQUIGGLY_SLIDER_TOOLTIP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SQUIGGLY_SLIDER_TOOLTIP_TAG).assertTextContains(expectedTooltip)

        composeTestRule.onNodeWithTag(SQUIGGLY_SLIDER_TAG).performTouchInput {
            up()
        }
    }
}
