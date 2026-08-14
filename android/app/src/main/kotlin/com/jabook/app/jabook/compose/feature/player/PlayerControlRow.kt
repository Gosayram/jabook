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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.SleepTimerState

/**
 * Secondary player control buttons (speed, EQ, visualizer, chapter repeat, A-B repeat, sleep
 * timer, bookmarks, lyrics). Compact screens split the buttons across two rows; larger screens
 * use a single row without the bookmarks button.
 */
@Composable
internal fun PlayerControlRow(
    isCompact: Boolean,
    playbackSpeedLabel: String,
    chapterRepeatMode: ChapterRepeatMode,
    abRepeatState: ABRepeatState,
    sleepTimerState: SleepTimerState,
    bookmarkCount: Int,
    hasLyrics: Boolean,
    showingLyrics: Boolean,
    speedButtonInteractionSource: MutableInteractionSource,
    onSpeedButtonClick: () -> Unit,
    onAudioSettingsClick: () -> Unit,
    onVisualizerModeCycle: () -> Unit,
    onChapterRepeatClick: () -> Unit,
    onABRepeatClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlButtonHeight = if (isCompact) 48.dp else 56.dp
    val controlButtonIconSize = if (isCompact) 22.dp else 24.dp
    val controlButtonTextSize = if (isCompact) 14.sp else 16.sp
    val controlButtonSpacing = if (isCompact) 8.dp else 12.dp
    val speedIconPaddingEnd = if (isCompact) 4.dp else 8.dp
    val sleepTimerAccessibilityDescription =
        when (sleepTimerState) {
            is SleepTimerState.Active ->
                "${stringResource(R.string.sleepTimer)}, ${formatSleepTimerRemaining(sleepTimerState.remainingSeconds)}"
            SleepTimerState.EndOfChapter ->
                "${stringResource(R.string.sleepTimer)}, ${stringResource(R.string.endOfChapterLabel)}"
            is SleepTimerState.EndOfTrack ->
                "${stringResource(R.string.sleepTimer)}, ${stringResource(R.string.endOfTrackLabel)}"
            SleepTimerState.Idle ->
                stringResource(R.string.sleepTimer)
        }

    if (isCompact) {
        // Compact: Two rows for better ergonomics
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // First row: Speed, EQ, Visualizer & Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        controlButtonSpacing,
                        Alignment.CenterHorizontally,
                    ),
            ) {
                SpeedControlButton(
                    onClick = onSpeedButtonClick,
                    interactionSource = speedButtonInteractionSource,
                    label = playbackSpeedLabel,
                    iconPaddingEnd = speedIconPaddingEnd,
                    iconSize = controlButtonIconSize,
                    textSize = controlButtonTextSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
                AudioSettingsControlButton(
                    onClick = onAudioSettingsClick,
                    iconSize = controlButtonIconSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
                VisualizerModeControlButton(
                    onClick = onVisualizerModeCycle,
                    iconSize = controlButtonIconSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
                ChapterRepeatControlButton(
                    onClick = onChapterRepeatClick,
                    chapterRepeatMode = chapterRepeatMode,
                    iconSize = controlButtonIconSize,
                    textSize = controlButtonTextSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
            }

            // Second row: Timer, AB Repeat, Bookmarks & Lyrics (if available)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        controlButtonSpacing,
                        Alignment.CenterHorizontally,
                    ),
            ) {
                SleepTimerControlButton(
                    onClick = onSleepTimerClick,
                    sleepTimerState = sleepTimerState,
                    iconSize = controlButtonIconSize,
                    textSize = controlButtonTextSize,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(controlButtonHeight)
                            .semantics { contentDescription = sleepTimerAccessibilityDescription },
                )
                AbRepeatControlButton(
                    onClick = onABRepeatClick,
                    abRepeatState = abRepeatState,
                    textSize = controlButtonTextSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
                BookmarksControlButton(
                    onClick = onBookmarksClick,
                    bookmarkCount = bookmarkCount,
                    iconSize = controlButtonIconSize,
                    textSize = controlButtonTextSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
                if (hasLyrics) {
                    LyricsControlButton(
                        showingLyrics = showingLyrics,
                        onToggleLyrics = onToggleLyrics,
                        iconSize = controlButtonIconSize,
                        modifier = Modifier.weight(1f).height(controlButtonHeight),
                    )
                } else {
                    // Empty spacer to balance the row when no lyrics
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        // Larger screens: Single row
        Row(
            modifier = modifier,
            horizontalArrangement =
                Arrangement.spacedBy(
                    controlButtonSpacing,
                    Alignment.CenterHorizontally,
                ),
        ) {
            SpeedControlButton(
                onClick = onSpeedButtonClick,
                interactionSource = speedButtonInteractionSource,
                label = playbackSpeedLabel,
                iconPaddingEnd = speedIconPaddingEnd,
                iconSize = controlButtonIconSize,
                textSize = controlButtonTextSize,
                modifier = Modifier.weight(1f).height(controlButtonHeight),
            )
            AudioSettingsControlButton(
                onClick = onAudioSettingsClick,
                iconSize = controlButtonIconSize,
                modifier = Modifier.weight(1f).height(controlButtonHeight),
            )
            VisualizerModeControlButton(
                onClick = onVisualizerModeCycle,
                iconSize = controlButtonIconSize,
                modifier = Modifier.weight(1f).height(controlButtonHeight),
            )
            ChapterRepeatControlButton(
                onClick = onChapterRepeatClick,
                chapterRepeatMode = chapterRepeatMode,
                iconSize = controlButtonIconSize,
                textSize = controlButtonTextSize,
                modifier = Modifier.weight(1f).height(controlButtonHeight),
            )
            AbRepeatControlButton(
                onClick = onABRepeatClick,
                abRepeatState = abRepeatState,
                textSize = controlButtonTextSize,
                modifier = Modifier.weight(1f).height(controlButtonHeight),
            )
            SleepTimerControlButton(
                onClick = onSleepTimerClick,
                sleepTimerState = sleepTimerState,
                iconSize = controlButtonIconSize,
                textSize = controlButtonTextSize,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(controlButtonHeight)
                        .semantics { contentDescription = sleepTimerAccessibilityDescription },
            )
            if (hasLyrics) {
                LyricsControlButton(
                    showingLyrics = showingLyrics,
                    onToggleLyrics = onToggleLyrics,
                    iconSize = controlButtonIconSize,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                )
            }
        }
    }
}

@Composable
private fun SpeedControlButton(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    label: String,
    iconPaddingEnd: Dp,
    iconSize: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Speed,
            contentDescription = stringResource(R.string.playbackSpeedTitle),
            modifier = Modifier.size(iconSize).padding(end = iconPaddingEnd),
        )
        Text(
            text = label,
            fontSize = textSize,
        )
    }
}

@Composable
private fun AudioSettingsControlButton(
    onClick: () -> Unit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = stringResource(R.string.audioSettingsTitle),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun VisualizerModeControlButton(
    onClick: () -> Unit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = stringResource(R.string.enableVisualizer),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ChapterRepeatControlButton(
    onClick: () -> Unit,
    chapterRepeatMode: ChapterRepeatMode,
    iconSize: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor =
                    when (chapterRepeatMode) {
                        ChapterRepeatMode.OFF -> MaterialTheme.colorScheme.surfaceVariant
                        ChapterRepeatMode.ONCE -> MaterialTheme.colorScheme.primaryContainer
                        ChapterRepeatMode.INFINITE -> MaterialTheme.colorScheme.primaryContainer
                    },
            ),
    ) {
        when (chapterRepeatMode) {
            ChapterRepeatMode.INFINITE ->
                Text(
                    "∞",
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            ChapterRepeatMode.OFF ->
                Icon(
                    Icons.Outlined.Repeat,
                    stringResource(R.string.noRepeat),
                    Modifier.size(iconSize),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            ChapterRepeatMode.ONCE ->
                Icon(
                    Icons.Filled.RepeatOne,
                    stringResource(R.string.repeatTrack),
                    Modifier.size(iconSize),
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
        }
    }
}

@Composable
private fun AbRepeatControlButton(
    onClick: () -> Unit,
    abRepeatState: ABRepeatState,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor =
                    when (abRepeatState.phase) {
                        ABRepeatPhase.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                        ABRepeatPhase.A_SET -> MaterialTheme.colorScheme.tertiaryContainer
                        ABRepeatPhase.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        when (abRepeatState.phase) {
            ABRepeatPhase.INACTIVE ->
                Text(
                    "A B",
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            ABRepeatPhase.A_SET ->
                Text(
                    "A",
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            ABRepeatPhase.ACTIVE ->
                Text(
                    "A→B",
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
        }
    }
}

@Composable
private fun SleepTimerControlButton(
    onClick: () -> Unit,
    sleepTimerState: SleepTimerState,
    iconSize: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            if (sleepTimerState is SleepTimerState.Idle) {
                Icons.Outlined.Timer
            } else {
                Icons.Filled.Timer
            },
            stringResource(R.string.sleepTimer),
            Modifier.size(iconSize),
        )
        if (sleepTimerState is SleepTimerState.Active) {
            val activeState = sleepTimerState
            Text(
                formatSleepTimerRemaining(activeState.remainingSeconds),
                fontSize = textSize,
            )
        }
    }
}

@Composable
private fun BookmarksControlButton(
    onClick: () -> Unit,
    bookmarkCount: Int,
    iconSize: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor =
                    if (bookmarkCount > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (bookmarkCount > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        Icon(
            if (bookmarkCount > 0) {
                Icons.Filled.Bookmark
            } else {
                Icons.Outlined.Bookmark
            },
            stringResource(R.string.bookmarks),
            Modifier.size(iconSize),
        )
        if (bookmarkCount > 0) {
            Text(
                text = stringResource(R.string.bookmarkCount, bookmarkCount),
                fontSize = textSize,
            )
        }
    }
}

@Composable
private fun LyricsControlButton(
    showingLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onToggleLyrics,
        modifier = modifier,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor =
                    if (showingLyrics) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (showingLyrics) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        Icon(
            if (showingLyrics) {
                Icons.Filled.Description
            } else {
                Icons.Outlined.Description
            },
            stringResource(R.string.lyrics),
            Modifier.size(iconSize),
        )
    }
}
