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

package com.jabook.app.jabook.compose.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R

/**
 * Master/Detail pane layout for desktop/tablet mode.
 *
 * Shows a fixed-width master list on the left and detail pane on the right.
 * Used on expanded/expanded-width screens for efficient book browsing.
 */
@Composable
public fun LibraryMasterDetailPane(
    modifier: Modifier = Modifier,
    masterContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    emptyDetailContent: @Composable BoxScope.() -> Unit = {
        LibraryEmptyDetailPane()
    },
    showDetail: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxSize(),
    ) {
        // Master pane - fixed width list of books
        Box(
            modifier = Modifier.width(320.dp),
            content = masterContent,
        )

        // Vertical divider
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        )

        // Detail pane - shows book details when a book is selected
        if (showDetail) {
            Box(
                modifier = Modifier.weight(1f),
                content = detailContent,
            )
        } else {
            Box(
                modifier = Modifier.weight(1f),
                content = emptyDetailContent,
            )
        }
    }
}

/**
 * Empty detail pane shown when no book is selected in master/detail mode.
 */
@Composable
private fun LibraryEmptyDetailPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.desktopSelectBookHint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
