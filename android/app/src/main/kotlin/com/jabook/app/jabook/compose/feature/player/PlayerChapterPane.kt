// Copyright 2025 Jabook Contributors
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Side panel component for displaying book chapters on wide screens.
 *
 * This component is shown in the supporting pane of SupportingPaneScaffold on medium/expanded screens.
 * It displays a scrollable list of chapters with the current chapter highlighted.
 *
 * @param chapters List of chapters to display
 * @param currentChapterIndex Index of the currently playing chapter
 * @param onChapterClick Callback when a chapter is clicked
 * @param modifier Modifier for the root composable
 */
@Composable
fun PlayerChapterPane(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    onChapterClick: (Int) -> Unit,
    normalizeEnabled: Boolean, // NEW: normalization preference
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Header with search
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chaptersLabelText),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${currentChapterIndex + 1}/${chapters.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search/Jump field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        // Allow only numbers
                        if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                            searchQuery = newValue

                            // Auto-jump when valid number entered
                            val chapterNum = newValue.toIntOrNull()
                            if (chapterNum != null && chapterNum in 1..chapters.size) {
                                val targetIndex = chapterNum - 1
                                scope.launch {
                                    lazyListState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.jumpToChapterNumber)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clearSearch),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onGo = {
                                val chapterNum = searchQuery.toIntOrNull()
                                if (chapterNum != null && chapterNum in 1..chapters.size) {
                                    onChapterClick(chapterNum - 1)
                                    searchQuery = ""
                                }
                            },
                        ),
                )
            }
        }

        HorizontalDivider()

        // Chapter list
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                ChapterListItem(
                    chapter = chapter,
                    index = index,
                    isSelected = index == currentChapterIndex,
                    normalizeEnabled = normalizeEnabled,
                    onClick = { onChapterClick(index) },
                )
            }
        }
    }
}

/**
 * Individual chapter list item.
 */
@Composable
private fun ChapterListItem(
    chapter: Chapter,
    index: Int,
    isSelected: Boolean,
    normalizeEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chapter number badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chapter info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (normalizeEnabled) {
                            com.jabook.app.jabook.compose.core.util.ChapterUtils.formatChapterName(
                                chapter = chapter,
                                index = index,
                                localizedPrefix = stringResource(R.string.chapter_prefix),
                            )
                        } else {
                            chapter.title // Original title
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatChapterDuration(chapter.duration.inWholeMilliseconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Play indicator for current chapter
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.currentlyPlaying),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Formats a duration in milliseconds to a human-readable string.
 * Examples: "1h 23m", "45m", "12s"
 */
@Composable
private fun formatChapterDuration(millis: Long): String {
    val duration = millis.milliseconds
    val hours = duration.inWholeHours
    val minutes = (duration.inWholeMinutes % 60)
    val seconds = (duration.inWholeSeconds % 60)

    return when {
        hours > 0 -> stringResource(R.string.durationHoursMinutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.durationMinutes, minutes)
        else -> stringResource(R.string.durationSeconds, seconds)
    }
}
