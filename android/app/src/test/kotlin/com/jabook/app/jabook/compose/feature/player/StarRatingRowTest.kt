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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StarRatingRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `StarRatingRow renders without crash`() {
        composeTestRule.setContent {
            MaterialTheme {
                StarRatingRow(selected = 0, onRate = {})
            }
        }
    }

    @Test
    fun `StarRatingRow renders with selected stars`() {
        composeTestRule.setContent {
            MaterialTheme {
                StarRatingRow(selected = 3, onRate = {})
            }
        }
    }
}
