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

package com.jabook.app.jabook.compose.feature.torrent

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.jabook.app.jabook.compose.data.torrent.TorrentFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
public class FileSelectionDialogTest {
    @get:Rule
    public val composeTestRule = createComposeRule()

    @Test
    public fun `mixed folder selection is indeterminate`() {
        composeTestRule.setContent {
            MaterialTheme {
                FileSelectionDialog(
                    files =
                        listOf(
                            TorrentFile(index = 0, path = "book/first.mp3", size = 1, isSelected = true),
                            TorrentFile(index = 1, path = "book/second.mp3", size = 1, isSelected = false),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onAllNodes(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Indeterminate,
                ),
            ).assertCountEquals(1)
    }
}
