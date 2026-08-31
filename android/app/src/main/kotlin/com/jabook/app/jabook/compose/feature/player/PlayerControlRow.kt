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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.SleepTimerState

/**
 * Secondary player control buttons — Level 2 single row per wireframe:
 * Speed | Sleep Timer | Chapters | Bookmarks. Level 3 (Lyrics, Tune/EQ, visualizer,
 * chapter repeat, A-B repeat, stats) lives in overflow.
 * Single row on all sizes; compact uses smaller heights.
 */
@Composable
internal fun PlayerControlRow(
    isCompact: Boolean,
    playbackSpeedLabel: String,
    sleepTimerState: SleepTimerState,
    bookmarkCount: Int = 0,
    speedButtonInteractionSource: MutableInteractionSource,
    onSpeedButtonClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onBookmarksClick: () -> Unit,
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

    // Single row — wireframe Level 2: Speed | Sleep Timer | Chapters | Bookmarks
    Row(
        modifier = modifier.fillMaxWidth(),
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
        ChaptersControlButton(
            onClick = onChaptersClick,
            iconSize = controlButtonIconSize,
            modifier = Modifier.weight(1f).height(controlButtonHeight),
        )
        BookmarksControlButton(
            onClick = onBookmarksClick,
            bookmarkCount = bookmarkCount,
            iconSize = controlButtonIconSize,
            modifier = Modifier.weight(1f).height(controlButtonHeight),
        )
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
private fun ChaptersControlButton(
    onClick: () -> Unit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.List,
            stringResource(R.string.chaptersLabel),
            Modifier.size(iconSize),
        )
    }
}

@Composable
private fun BookmarksControlButton(
    onClick: () -> Unit,
    bookmarkCount: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            if (bookmarkCount > 0) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            stringResource(R.string.bookmarks),
            Modifier.size(iconSize),
        )
    }
}
