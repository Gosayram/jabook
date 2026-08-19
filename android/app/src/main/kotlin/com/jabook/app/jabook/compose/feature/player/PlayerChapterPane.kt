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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.domain.model.Chapter

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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun PlayerChapterPane(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    onChapterClick: (Int) -> Unit,
    normalizeEnabled: Boolean, // NEW: normalization preference
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // Use Material 3 WindowSizeClass for adaptive padding
    val context = LocalContext.current
    val wsc = LocalWindowSizeClass.current
    val windowSizeClass = wsc?.let { AdaptiveUtils.resolveWindowSizeClassOrNull(it, context) } ?: wsc

    val horizontalPadding: Dp =
        when (windowSizeClass?.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 16.dp // Phone portrait
            WindowWidthSizeClass.Medium -> 24.dp // Phone landscape, small tablet
            WindowWidthSizeClass.Expanded -> 32.dp // Large tablet, desktop
            else -> 16.dp // Fallback
        }

    val verticalPadding: Dp =
        when (windowSizeClass?.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 12.dp
            else -> 16.dp
        }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Header with search
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
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

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search)) },
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(onDone = {
                            // Hide keyboard
                            defaultKeyboardAction(ImeAction.Done)
                        }),
                )
            }
        }

        HorizontalDivider()

        // Filter chapters
        val chapterPrefix = stringResource(R.string.chapter_prefix)
        val indexedChapters by remember(chapters) {
            derivedStateOf { chapters.mapIndexed { index, chapter -> index to chapter } }
        }
        val filteredChapters by
            remember(indexedChapters, searchQuery, normalizeEnabled, chapterPrefix) {
                derivedStateOf {
                    val normalizedSearchQuery = searchQuery.trim()
                    indexedChapters
                        .filter { (index, chapter) ->
                            if (normalizedSearchQuery.isBlank()) {
                                true
                            } else {
                                val titleToSearch =
                                    com.jabook.app.jabook.compose.core.util.ChapterUtils.formatChapterName(
                                        chapter = chapter,
                                        index = index,
                                        localizedPrefix = chapterPrefix,
                                        normalizeEnabled = normalizeEnabled,
                                    )

                                (index + 1).toString().contains(normalizedSearchQuery) ||
                                    titleToSearch.contains(normalizedSearchQuery, ignoreCase = true)
                            }
                        }
                }
            }

        LaunchedEffect(currentChapterIndex, filteredChapters) {
            val targetIndex = filteredChapters.indexOfFirst { it.first == currentChapterIndex }
            if (targetIndex < 0) return@LaunchedEffect

            val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
            val firstVisible = visibleItems.firstOrNull()?.index ?: 0
            val lastVisible = visibleItems.lastOrNull()?.index ?: firstVisible
            when (ChapterAutoScrollPolicy.resolve(targetIndex, firstVisible, lastVisible)) {
                ChapterAutoScrollPolicy.ScrollAction.NONE -> Unit
                ChapterAutoScrollPolicy.ScrollAction.ANIMATE -> lazyListState.animateScrollToItem(targetIndex)
                ChapterAutoScrollPolicy.ScrollAction.SNAP -> lazyListState.scrollToItem(targetIndex)
            }
        }

        // Chapter list
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(8.dp),
        ) {
            if (filteredChapters.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.noChaptersFoundInSearch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            items(
                count = filteredChapters.size,
                key = { index -> filteredChapters[index].second.id },
                contentType = { index ->
                    val (originalIndex, chapter) = filteredChapters[index]
                    when {
                        originalIndex == currentChapterIndex -> "currently-playing"
                        chapter.isCompleted -> "completed"
                        else -> "chapter"
                    }
                },
            ) { listIndex ->
                val (originalIndex, chapter) = filteredChapters[listIndex]
                ChapterListItem(
                    chapter = chapter,
                    index = originalIndex,
                    isSelected = originalIndex == currentChapterIndex,
                    normalizeEnabled = normalizeEnabled,
                    onClick = { onChapterClick(originalIndex) },
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
    val chapterName =
        com.jabook.app.jabook.compose.core.util.ChapterUtils.formatChapterName(
            chapter = chapter,
            index = index,
            localizedPrefix = stringResource(R.string.chapter_prefix),
            normalizeEnabled = normalizeEnabled,
        )
    val chapterStatus =
        when {
            isSelected -> stringResource(R.string.currentlyPlaying)
            chapter.isCompleted -> stringResource(R.string.completed)
            else -> chapterProgressLabel(chapter)
        }
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$chapterName, $chapterStatus"
                    selected = isSelected
                }.padding(vertical = 4.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapterName,
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
                        text = chapterProgressLabel(chapter),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (chapter.isCompleted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.completed),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = stringResource(R.string.currentlyPlaying),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (chapter.duration.inWholeMilliseconds > 0 && !chapter.isCompleted) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { chapter.progress },
                    modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

/**
 * Formats a progress label for a chapter item.
 * Completed: "1:15:00" (with checkmark icon alongside)
 * Partial:   "37:20 / 1:15:00"
 * Unstarted: "1:15:00"
 */
private fun chapterProgressLabel(chapter: Chapter): String {
    val total = PlayerTimeFormatter.formatDuration(chapter.duration.inWholeMilliseconds)
    if (!chapter.isStarted || chapter.isCompleted) return total
    val current = PlayerTimeFormatter.formatDuration(chapter.position.inWholeMilliseconds)
    return "$current / $total"
}
