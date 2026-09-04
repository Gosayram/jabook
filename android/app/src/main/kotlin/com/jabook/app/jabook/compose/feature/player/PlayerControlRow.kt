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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.designsystem.component.connectedItemShape
import com.jabook.app.jabook.compose.domain.model.SleepTimerState

/**
 * Secondary player control buttons — Level 2 single row per wireframe:
 * Speed | Sleep Timer | Chapters | Bookmarks. Level 3 (Lyrics, Tune/EQ, visualizer,
 * chapter repeat, A-B repeat, stats) lives in overflow.
 * Single row on all sizes; compact uses smaller heights.
 *
 * ponytail: ButtonGroup (M3 1.5 alpha) downgraded to stable Row + connectedItemShape + weight(1f)
 * spacedBy 2.dp faux — add ButtonGroup(expandedRatio=0.15f) when M3 1.5 stable.
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
    val sleepTimerLabel =
        when (sleepTimerState) {
            is SleepTimerState.Active -> formatSleepTimerRemaining(sleepTimerState.remainingSeconds)
            SleepTimerState.EndOfChapter -> stringResource(R.string.endOfChapterLabel)
            is SleepTimerState.EndOfTrack -> stringResource(R.string.endOfTrackLabel)
            SleepTimerState.Idle -> stringResource(R.string.sleepTimer)
        }
    // ponytail: 2dp faux connected spacing — ButtonGroup.ConnectedSpaceBetween when stable
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSpeedButtonClick,
            interactionSource = speedButtonInteractionSource,
            shape = connectedItemShape(0, 4),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(1f).height(controlButtonHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                modifier = Modifier.size(controlButtonIconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(playbackSpeedLabel)
        }
        Button(
            onClick = onSleepTimerClick,
            shape = connectedItemShape(1, 4),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(1f).height(controlButtonHeight),
        ) {
            Icon(
                imageVector = if (sleepTimerState is SleepTimerState.Idle) Icons.Outlined.Timer else Icons.Filled.Timer,
                contentDescription = null,
                modifier = Modifier.size(controlButtonIconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(sleepTimerLabel)
        }
        Button(
            onClick = onChaptersClick,
            shape = connectedItemShape(2, 4),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(1f).height(controlButtonHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.List,
                contentDescription = null,
                modifier = Modifier.size(controlButtonIconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.chaptersLabel))
        }
        Button(
            onClick = onBookmarksClick,
            shape = connectedItemShape(3, 4),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(1f).height(controlButtonHeight),
        ) {
            Icon(
                imageVector = if (bookmarkCount > 0) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(controlButtonIconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.bookmarks))
        }
    }
}
