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

import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.LocalJabookMotionScheme
import com.jabook.app.jabook.compose.core.theme.SurfaceElevationTokens
import com.jabook.app.jabook.compose.core.util.rememberReduceMotion
import com.jabook.app.jabook.compose.designsystem.component.CircularIconButton
import com.jabook.app.jabook.compose.designsystem.component.CircularIconButtonStyle
import com.jabook.app.jabook.compose.designsystem.component.ThinProgressBar
import com.jabook.app.jabook.ui.theme.JabookTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shared empty flows so default parameters keep a stable identity across recompositions. */
private val NoPosition: StateFlow<Long> = MutableStateFlow(0L)
private val NoDuration: StateFlow<Long> = MutableStateFlow(0L)

/**
 * Mini player component displayed above bottom navigation.
 *
 * Static bar — no swipe/drag gestures. Dismiss is exposed as an accessibility
 * custom action only.
 *
 * Features:
 * - Tap opens the full player
 * - Play/pause control
 * - Progress indicator
 *
 * @param coverUrl Book cover URL
 * @param title Book title
 * @param author Book author
 * @param isPlaying Whether audio is playing
 * @param onPlayPauseClick Callback for play/pause button
 * @param onMiniPlayerClick Callback when mini player card is clicked
 * @param onDismiss Callback when mini player is dismissed via the accessibility action
 * @param modifier Modifier
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun MiniPlayer(
    coverUrl: String?,
    title: String,
    author: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    hasNextChapter: Boolean = true,
    hasPreviousChapter: Boolean = true,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    currentPositionMs: StateFlow<Long> = NoPosition,
    durationMs: StateFlow<Long> = NoDuration,
    // ponytail: shared transition — same key as PlayerScreen ("cover_${bookId}") for cover morph
    bookId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val motionScheme = LocalJabookMotionScheme.current
    val currentPosition by currentPositionMs.collectAsStateWithLifecycle()
    val duration by durationMs.collectAsStateWithLifecycle()
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "miniPlayerProgress",
    )
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnMiniPlayerClick by rememberUpdatedState(onMiniPlayerClick)
    val currentOnNextClick by rememberUpdatedState(onNextClick)
    val currentOnPreviousClick by rememberUpdatedState(onPreviousClick)
    val dismissActionLabel = stringResource(R.string.dismissAction)
    val nextChapterActionLabel = stringResource(R.string.nextChapter)
    val previousChapterActionLabel = stringResource(R.string.previousChapter)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(dismissActionLabel) {
                                currentOnDismiss()
                                true
                            },
                            CustomAccessibilityAction(nextChapterActionLabel) {
                                if (hasNextChapter) currentOnNextClick()
                                true
                            },
                            CustomAccessibilityAction(previousChapterActionLabel) {
                                if (hasPreviousChapter) currentOnPreviousClick()
                                true
                            },
                        )
                }.clickable(
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = currentOnMiniPlayerClick,
                ),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = SurfaceElevationTokens.Level3,
    ) {
        Column {
            // Cover image with rounded corners
            val context = LocalContext.current
            val displayDensity = context.resources.displayMetrics.density
            val cornerRadiusPx = 8f * displayDensity // 8dp rounded corners for mini player
            val surfaceVariantArgb = MaterialTheme.colorScheme.surfaceVariant.toArgb()
            val imageRequest =
                remember(coverUrl, context, surfaceVariantArgb) {
                    ImageRequest
                        .Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .placeholder(ColorDrawable(surfaceVariantArgb))
                        .error(ColorDrawable(surfaceVariantArgb))
                        .transformations(RoundedCornersTransformation(cornerRadiusPx))
                        .build()
                }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverModifier =
                    if (
                        sharedTransitionScope != null &&
                        animatedVisibilityScope != null &&
                        bookId != null
                    ) {
                        with(sharedTransitionScope) {
                            Modifier
                                .size(48.dp)
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "cover_$bookId"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                        }
                    } else {
                        Modifier.size(48.dp)
                    }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = coverModifier,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and author
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (author.isNotBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ponytail: max 2 actions (play/pause + next) — previous removed, still accessible via swipe right
                CircularIconButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription =
                        if (isPlaying) {
                            stringResource(R.string.pause)
                        } else {
                            stringResource(R.string.play)
                        },
                    onClick = onPlayPauseClick,
                    style = CircularIconButtonStyle.DEFAULT,
                    size = 28.dp,
                )

                Spacer(modifier = Modifier.width(4.dp))

                CircularIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.nextChapter),
                    onClick = onNextClick,
                    style = CircularIconButtonStyle.DEFAULT,
                    enabled = hasNextChapter,
                    size = 24.dp,
                )
            }

            // Progress indicator
            ThinProgressBar(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                progressColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(name = "MiniPlayer Large Font", fontScale = 1.5f, showBackground = true)
@Preview(name = "MiniPlayer Huge Font", fontScale = 2.0f, showBackground = true)
@Composable
private fun MiniPlayerFontScalePreview() {
    JabookTheme {
        MiniPlayer(
            coverUrl = null,
            title = "Очень длинное название аудиокниги для проверки адаптивности в мини-плеере",
            author = "Очень длинное имя автора",
            isPlaying = true,
            onPlayPauseClick = {},
            onMiniPlayerClick = {},
        )
    }
}

/**
 * Animated container for MiniPlayer with slide-in/out animations.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun AnimatedMiniPlayer(
    visible: Boolean,
    coverUrl: String?,
    title: String,
    author: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    hasNextChapter: Boolean = true,
    hasPreviousChapter: Boolean = true,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    currentPositionMs: StateFlow<Long> = NoPosition,
    durationMs: StateFlow<Long> = NoDuration,
    bookId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val motionScheme = LocalJabookMotionScheme.current
    AnimatedVisibility(
        visible = visible,
        enter =
            if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                )
            },
        exit =
            if (reduceMotion) {
                ExitTransition.None
            } else {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                )
            },
        modifier = modifier,
    ) {
        MiniPlayer(
            coverUrl = coverUrl,
            title = title,
            author = author,
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            onMiniPlayerClick = onMiniPlayerClick,
            onNextClick = onNextClick,
            onPreviousClick = onPreviousClick,
            hasNextChapter = hasNextChapter,
            hasPreviousChapter = hasPreviousChapter,
            onDismiss = onDismiss,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            bookId = bookId,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
