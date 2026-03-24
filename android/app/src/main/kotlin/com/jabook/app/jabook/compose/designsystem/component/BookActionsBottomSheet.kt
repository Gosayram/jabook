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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider

/**
 * Modal bottom sheet with contextual actions for a book.
 *
 * Displays available contextual actions based on BookActionsProvider configuration.
 * Actions include:
 * - Share book details/link
 * - Delete book from library
 * - Add to playlist
 * - Show detailed information
 *
 * @param book The book for which to show actions
 * @param actionsProvider Provider containing action callbacks
 * @param sheetState State for controlling the bottom sheet
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun BookActionsBottomSheet(
    book: Book,
    actionsProvider: BookActionsProvider,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            // Sheet header with book title
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 1,
            )

            HorizontalDivider()

            // Share action
            actionsProvider.onShareBook?.let { onShare ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.share)) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier =
                        Modifier.clickableWithoutRipple {
                            onShare(book.id)
                            onDismiss()
                        },
                )
            }

            // Add to Playlist action
            actionsProvider.onAddToPlaylist?.let { onAdd ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.addToPlaylist)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    modifier =
                        Modifier.clickableWithoutRipple {
                            onAdd(book.id)
                            onDismiss()
                        },
                )
            }

            // Book Info action
            actionsProvider.onShowBookInfo?.let { onInfo ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.bookInfo)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier =
                        Modifier.clickableWithoutRipple {
                            onInfo(book.id)
                            onDismiss()
                        },
                )
            }

            // Delete action (with different styling)
            actionsProvider.onDeleteBook?.let { onDelete ->
                HorizontalDivider()
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier =
                        Modifier.clickableWithoutRipple {
                            onDelete(book.id)
                            onDismiss()
                        },
                )
            }
        }
    }
}

/**
 * Helper modifier for clickable items without ripple effect.
 */
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier =
    this.then(
        clickable(
            interactionSource =
                androidx.compose.foundation.interaction
                    .MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        ),
    )
