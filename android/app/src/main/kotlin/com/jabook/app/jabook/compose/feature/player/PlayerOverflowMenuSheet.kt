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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
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
 * Player overflow menu bottom sheet with share, favorite, go-to-book, and statistics actions.
 */
@Composable
public fun PlayerOverflowMenuSheet(
    isFavorite: Boolean,
    onShareClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToBookClick: () -> Unit,
    onBookmarksClick: () -> Unit,
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
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
        )

        OverflowMenuItem(
            icon = Icons.Default.Book,
            titleRes = R.string.playerGoToBook,
            onClick = {
                onGoToBookClick()
                onDismiss()
            },
        )

        OverflowMenuItem(
            icon = Icons.Filled.Bookmark,
            titleRes = R.string.bookmarks,
            onClick = {
                onBookmarksClick()
                onDismiss()
            },
        )

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

@Composable
private fun OverflowMenuItem(
    icon: ImageVector,
    titleRes: Int,
    onClick: () -> Unit,
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
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
