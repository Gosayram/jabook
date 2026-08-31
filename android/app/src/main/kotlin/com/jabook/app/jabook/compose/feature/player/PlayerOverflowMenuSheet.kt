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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R

/**
 * Player overflow menu bottom sheet — Level 3 (Tune/EQ, visualizer, chapter repeat, A-B
 * repeat, stats, lyrics). Bookmarks is Level 2 (icon row) so not duplicated here.
 */
@Composable
public fun PlayerOverflowMenuSheet(
    isFavorite: Boolean,
    hasLyrics: Boolean = false,
    showingLyrics: Boolean = false,
    onShareClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: (() -> Unit)? = null,
    onAudioSettingsClick: () -> Unit,
    onVisualizerModeCycle: () -> Unit,
    onChapterRepeatClick: () -> Unit,
    onABRepeatClick: () -> Unit,
    chapterRepeatMode: ChapterRepeatMode,
    abRepeatState: ABRepeatState,
    onStatsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OverflowMenuItem(
            icon = Icons.Default.Share,
            titleRes = R.string.playerShare,
            onClick = {
                onShareClick()
                onDismiss()
            },
        )

        OverflowMenuItem(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            titleRes = if (isFavorite) R.string.removeFromFavorites else R.string.addToFavorites,
            iconTint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
        )

        if (hasLyrics && onToggleLyrics != null) {
            OverflowMenuItem(
                icon = if (showingLyrics) Icons.Filled.Description else Icons.Outlined.Description,
                titleRes = R.string.lyrics,
                onClick = {
                    onToggleLyrics()
                    onDismiss()
                },
            )
        }

        OverflowMenuItem(
            icon = Icons.Filled.Tune,
            titleRes = R.string.audioSettingsTitle,
            onClick = {
                onAudioSettingsClick()
                onDismiss()
            },
        )

        OverflowMenuItem(
            icon = Icons.Filled.Visibility,
            titleRes = R.string.enableVisualizer,
            onClick = {
                onVisualizerModeCycle()
                onDismiss()
            },
        )

        OverflowMenuItem(
            icon =
                when (chapterRepeatMode) {
                    ChapterRepeatMode.INFINITE -> Icons.Filled.RepeatOne
                    ChapterRepeatMode.ONCE -> Icons.Filled.RepeatOne
                    ChapterRepeatMode.OFF -> Icons.Outlined.Repeat
                },
            titleRes =
                when (chapterRepeatMode) {
                    ChapterRepeatMode.OFF -> R.string.noRepeat
                    ChapterRepeatMode.ONCE -> R.string.repeatTrack
                    ChapterRepeatMode.INFINITE -> R.string.repeatTrack
                },
            onClick = {
                onChapterRepeatClick()
                onDismiss()
            },
        )

        OverflowMenuItemWithSubtitle(
            icon = Icons.Filled.Repeat,
            title = "A-B Repeat",
            subtitle =
                when (abRepeatState.phase) {
                    ABRepeatPhase.INACTIVE -> "Inactive"
                    ABRepeatPhase.A_SET -> "A set"
                    ABRepeatPhase.ACTIVE -> "A→B active"
                },
            onClick = {
                onABRepeatClick()
                onDismiss()
            },
        )

        // Developer tool — only shown in debug builds (menu item hidden in release).
        if (com.jabook.app.jabook.BuildConfig.DEBUG) {
            OverflowMenuItem(
                icon = Icons.Default.BarChart,
                titleRes = R.string.playerStatistics,
                onClick = {
                    onStatsClick()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun OverflowMenuItem(
    icon: ImageVector,
    titleRes: Int,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun OverflowMenuItemWithSubtitle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
