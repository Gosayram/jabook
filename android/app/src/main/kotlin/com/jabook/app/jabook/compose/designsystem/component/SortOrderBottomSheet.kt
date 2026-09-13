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

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.model.BookSortOrder

/**
 * String resource for the display label of a [BookSortOrder].
 */
public fun BookSortOrder.displayStringRes(): Int =
    when (this) {
        BookSortOrder.BY_ACTIVITY -> R.string.sort_by_activity
        BookSortOrder.TITLE_ASC -> R.string.sort_title_asc
        BookSortOrder.TITLE_DESC -> R.string.sort_title_desc
        BookSortOrder.AUTHOR_ASC -> R.string.sort_author_asc
        BookSortOrder.AUTHOR_DESC -> R.string.sort_author_desc
        BookSortOrder.RECENTLY_ADDED -> R.string.sort_recently_added
        BookSortOrder.OLDEST_FIRST -> R.string.sort_oldest_first
    }

/**
 * Sort order bottom sheet listing all [BookSortOrder] entries.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun SortOrderBottomSheet(
    currentSortOrder: BookSortOrder,
    onSortOrderChanged: (BookSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    JabookModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sort_by),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val sortEntries = BookSortOrder.entries
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sortEntries.forEachIndexed { index, order ->
                val shape = connectedItemShape(index, sortEntries.size)
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clip(shape),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(order.displayStringRes()))
                        },
                        leadingContent = {
                            if (order == currentSortOrder) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        },
                        modifier = Modifier.combinedClickable(onClick = { onSortOrderChanged(order) }),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
