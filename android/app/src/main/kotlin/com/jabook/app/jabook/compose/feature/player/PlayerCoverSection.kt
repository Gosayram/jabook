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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.feature.player.lyrics.LyricsView

/**
 * Book cover section for portrait layout. Handles three display modes:
 * lyrics overlay, vinyl cover, and standard cover image with breathing animation.
 */
@Composable
internal fun PlayerCoverSection(
    state: PlayerState.Active,
    imageModifier: Modifier,
    coverWidth: Float,
    showingLyrics: Boolean,
    showLyrics: (Boolean) -> Unit,
    isVinylMode: Boolean,
    reduceMotion: Boolean,
    hasLyrics: Boolean,
    onStatsClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMs: Long = 0L,
) {
    val context = LocalContext.current
    val imageRequest =
        CoverUtils
            .createCoverImageRequest(
                book = state.book,
                context = context,
                placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
                errorColor = MaterialTheme.colorScheme.error,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                cornerRadius = 16f,
            ).build()
    val canToggleLyrics = hasLyrics
    val toggleLyricsLabel = stringResource(R.string.toggleLyricsView)
    val toggleLyricsStateDescription =
        if (showingLyrics) {
            stringResource(R.string.lyricsVisibleState)
        } else {
            stringResource(R.string.lyricsHiddenState)
        }

    val coverScale =
        if (reduceMotion || !state.isPlaying) {
            1f // no transition created while paused -> zero per-frame ticking
        } else {
            val infiniteTransition =
                androidx.compose.animation.core
                    .rememberInfiniteTransition(label = "coverScale")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.03f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(4000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "scale",
            )
            scale
        }

    if (showingLyrics) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth(coverWidth)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        ) {
            LyricsView(
                lyrics = state.lyrics.orEmpty(),
                currentPosition = currentPositionMs,
                onSeek = onSeek,
            )
        }
    } else if (isVinylMode) {
        VinylCover(
            imageRequest = imageRequest,
            isPlaying = state.isPlaying,
            modifier =
                modifier
                    .fillMaxWidth(coverWidth)
                    .semantics {
                        if (canToggleLyrics) {
                            role = Role.Button
                            contentDescription = toggleLyricsLabel
                            stateDescription = toggleLyricsStateDescription
                        }
                    }.clickable(
                        enabled = canToggleLyrics,
                        onClickLabel = toggleLyricsLabel,
                    ) {
                        if (canToggleLyrics) {
                            showLyrics(!showingLyrics)
                        }
                    },
        )
    } else {
        AsyncImage(
            model = imageRequest,
            contentDescription =
                stringResource(
                    R.string.playerCoverAccessibilityDescription,
                    state.book.title,
                    state.book.author,
                ),
            modifier =
                modifier
                    .fillMaxWidth(coverWidth)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = if (state.isPlaying) coverScale else 1f
                        scaleY = if (state.isPlaying) coverScale else 1f
                    }.clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .semantics {
                        if (canToggleLyrics) {
                            role = Role.Button
                            stateDescription = toggleLyricsStateDescription
                        }
                    }.combinedClickable(
                        onClick = { if (canToggleLyrics) showLyrics(!showingLyrics) },
                        onDoubleClick = onStatsClick,
                        onClickLabel = toggleLyricsLabel,
                    ),
            contentScale = ContentScale.Crop,
        )
    }
}
