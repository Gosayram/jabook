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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.ChapterUtils
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.coroutines.launch

/**
 * Bottom sheet for selecting a chapter.
 * Features auto-scroll to current chapter, highlight, and smart chapter naming.
 *
 * @param chapters List of all chapters
 * @param currentChapterIndex Currently playing chapter index
 * @param onChapterSelected Callback when a chapter is selected
 * @param onDismiss Callback to dismiss the sheet
 * @param sheetState Bottom sheet state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ChapterSelectorSheet(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    onChapterSelected: (Int) -> Unit,
    onChaptersReordered: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    normalizeEnabled: Boolean = true,
) {
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // Local list for reordering
    var editedChapters by remember { mutableStateOf(chapters) }

    // Sync editedChapters when entering edit mode or when chapters change (if not editing)
    LaunchedEffect(chapters, isEditing) {
        if (!isEditing) {
            editedChapters = chapters
        }
    }

    // Filter chapters by search query (only valid when not editing)
    val chapterPrefix = stringResource(R.string.chapter_prefix)
    val displayChapters =
        remember(chapters, editedChapters, searchQuery, isEditing, chapterPrefix) {
            if (isEditing) {
                // Return all chapters in current edited order
                editedChapters.mapIndexed { index, chapter -> index to chapter }
            } else {
                if (searchQuery.isBlank()) {
                    chapters.mapIndexed { index, chapter -> index to chapter }
                } else {
                    chapters
                        .mapIndexed { index, chapter -> index to chapter }
                        .filter { (index, chapter) ->
                            val chapterName =
                                ChapterUtils.formatChapterName(
                                    chapter,
                                    index,
                                    chapterPrefix,
                                    normalizeEnabled,
                                )
                            val chapterNumber = ChapterUtils.extractChapterNumber(chapter.title, index)
                            searchQuery.toIntOrNull()?.let { searchNum ->
                                chapterNumber == searchNum
                            } ?: chapterName.contains(searchQuery, ignoreCase = true)
                        }
                }
            }
        }

    // Auto-scroll to current chapter when sheet opens
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0 && currentChapterIndex < chapters.size && !isEditing) {
            listState.animateScrollToItem(currentChapterIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
        ) {
            // Fixed header and search section (not scrollable)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Header with Edit button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chaptersLabelText),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    // Edit / Done / Cancel buttons
                    Row {
                        if (isEditing) {
                            // Cancel
                            androidx.compose.material3.TextButton(
                                onClick = { isEditing = false },
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            // Done (Save)
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    onChaptersReordered(editedChapters.map { it.id })
                                    isEditing = false
                                },
                            ) {
                                Text(stringResource(R.string.doneButtonText))
                            }
                        } else {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    editedChapters = chapters
                                    isEditing = true
                                    searchQuery = "" // Clear search when editing
                                    keyboardController?.hide() // Hide keyboard when entering edit mode
                                },
                            ) {
                                Text(stringResource(R.string.edit))
                            }
                        }
                    }
                }

                if (!isEditing) {
                    Text(
                        text = stringResource(R.string.selectChapterNote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search field (fixed, not in scrollable area)
                    // Keyboard will open automatically when user clicks on the field
                    TextField(
                        value = searchQuery,
                        onValueChange = { newValue -> searchQuery = newValue },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    // Hide keyboard when focus is lost
                                    if (!focusState.isFocused) {
                                        keyboardController?.hide()
                                    }
                                },
                        placeholder = { Text(stringResource(R.string.searchChapterPlaceholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.reorderChaptersInstruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider()
            }

            // Chapter list (scrollable, independent from search)
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) {
                itemsIndexed(
                    items = displayChapters,
                    key = { _, item -> item.second.id },
                ) { listIndex, (originalIndex, chapter) ->
                    ChapterSelectorItem(
                        chapter = chapter,
                        index = if (isEditing) listIndex else originalIndex,
                        // Don't highlight current in edit mode
                        isCurrent = !isEditing && originalIndex == currentChapterIndex,
                        isEditing = isEditing,
                        normalizeEnabled = normalizeEnabled,
                        onClick = {
                            if (!isEditing) {
                                // Start playback immediately (this will trigger play() automatically)
                                onChapterSelected(originalIndex)
                                keyboardController?.hide() // Hide keyboard when selecting chapter

                                // Smoothly close the sheet with animation
                                scope.launch {
                                    try {
                                        sheetState.hide()
                                        // Wait for animation to complete
                                        kotlinx.coroutines.delay(300L) // Typical sheet animation duration
                                    } catch (e: Exception) {
                                        // Sheet might already be hidden, ignore
                                    }
                                    // Call onDismiss after animation completes
                                    onDismiss()
                                }
                            }
                        },
                        onMoveUp = {
                            if (listIndex > 0) {
                                val newList = editedChapters.toMutableList()
                                java.util.Collections.swap(newList, listIndex, listIndex - 1)
                                editedChapters = newList
                            }
                        },
                        onMoveDown = {
                            if (listIndex < editedChapters.size - 1) {
                                val newList = editedChapters.toMutableList()
                                java.util.Collections.swap(newList, listIndex, listIndex + 1)
                                editedChapters = newList
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Individual chapter item in the selector sheet.
 */
@Composable
private fun ChapterSelectorItem(
    chapter: Chapter,
    index: Int,
    isCurrent: Boolean,
    isEditing: Boolean = false,
    normalizeEnabled: Boolean = true,
    onClick: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !isEditing, onClick = onClick)
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    },
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Chapter info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    ChapterUtils.formatChapterName(
                        chapter,
                        index,
                        stringResource(R.string.chapter_prefix),
                        normalizeEnabled,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight =
                    if (isCurrent) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                color =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Duration
            Text(
                text = formatDuration(chapter.duration.inWholeMilliseconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isEditing) {
            Row {
                androidx.compose.material3.IconButton(onClick = onMoveUp) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.moveUp),
                    )
                }
                androidx.compose.material3.IconButton(onClick = onMoveDown) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.moveDown),
                    )
                }
            }
        } else if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
