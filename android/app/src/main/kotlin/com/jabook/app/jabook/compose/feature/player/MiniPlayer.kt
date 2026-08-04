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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.MotionTokens
import com.jabook.app.jabook.compose.core.theme.SurfaceElevationTokens
import com.jabook.app.jabook.compose.core.util.rememberReduceMotion
import com.jabook.app.jabook.compose.designsystem.component.CircularIconButton
import com.jabook.app.jabook.compose.designsystem.component.CircularIconButtonStyle
import com.jabook.app.jabook.compose.designsystem.component.ThinProgressBar
import com.jabook.app.jabook.ui.theme.JabookTheme
import kotlin.math.abs

/**
 * Mini player component displayed above bottom navigation.
 *
 * Features:
 * - Smooth slide-in/out animations
 * - Swipe to dismiss with visual feedback
 * - Play/pause control
 * - Progress indicator
 *
 * @param coverUrl Book cover URL
 * @param title Book title
 * @param author Book author
 * @param isPlaying Whether audio is playing
 * @param progress Playback progress (0.0 to 1.0)
 * @param onPlayPauseClick Callback for play/pause button
 * @param onMiniPlayerClick Callback when mini player card is clicked
 * @param onDismiss Callback when mini player is dismissed via swipe
 * @param modifier Modifier
 */
@Composable
public fun MiniPlayer(
    coverUrl: String?,
    title: String,
    author: String,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Smooth spring animation for drag
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "miniPlayerOffset",
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "miniPlayerOffsetY",
    )

    // Calculate alpha based on vertical drag distance (dismiss)
    val dismissThreshold = with(density) { 100.dp.toPx() }
    val horizontalThreshold = with(density) { 100.dp.toPx() }

    // Alpha fades primarily on vertical dismiss
    val dragProgress = (animatedOffsetY.coerceAtLeast(0f) / dismissThreshold).coerceIn(0f, 1f)
    val dragAlpha = 1f - (dragProgress * 0.5f)

    // Scale during drag
    val scale = 1f - (dragProgress * 0.05f)

    val interactionSource = remember { MutableInteractionSource() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnMiniPlayerClick by rememberUpdatedState(onMiniPlayerClick)
    val currentOnNextClick by rememberUpdatedState(onNextClick)
    val currentOnPreviousClick by rememberUpdatedState(onPreviousClick)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                    translationY = animatedOffsetY
                    scaleX = scale
                    scaleY = scale
                    alpha = dragAlpha
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        // Only handle click if not dragging
                        if (abs(offsetX) < 10f && abs(offsetY) < 10f) {
                            onMiniPlayerClick()
                        }
                    },
                ).pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val absX = abs(offsetX)
                            val absY = abs(offsetY)

                            // Determine dominant axis
                            if (absX > absY) {
                                // Horizontal Swipe
                                if (absX > horizontalThreshold) {
                                    if (offsetX > 0) {
                                        // Swiped Right -> Previous
                                        currentOnPreviousClick()
                                    } else {
                                        // Swiped Left -> Next
                                        currentOnNextClick()
                                    }
                                    // Snap back after trigger (or maybe animate out? For now snap back like Spotify)
                                    offsetX = 0f
                                } else {
                                    offsetX = 0f
                                }
                                offsetY = 0f
                            } else {
                                // Vertical Swipe
                                if (offsetY > dismissThreshold) {
                                    // Swiped Down -> Dismiss
                                    currentOnDismiss()
                                } else if (offsetY < -dismissThreshold) {
                                    // Swiped Up -> Open
                                    currentOnMiniPlayerClick()
                                    offsetY = 0f
                                } else {
                                    offsetY = 0f
                                }
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                            offsetY = 0f
                        },
                    ) {
                        change: androidx.compose.ui.input.pointer.PointerInputChange,
                        dragAmount: androidx.compose.ui.geometry.Offset,
                        ->
                        change.consume()

                        // Update offsets
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = SurfaceElevationTokens.Level3,
    ) {
        Column {
            // Cover image with rounded corners
            val context = LocalContext.current
            val displayDensity = context.resources.displayMetrics.density
            val cornerRadiusPx = 8f * displayDensity // 8dp rounded corners for mini player
            val imageRequest =
                remember(coverUrl) {
                    ImageRequest
                        .Builder(context)
                        .data(coverUrl)
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
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop,
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

                // Previous chapter button
                CircularIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.previousChapter),
                    onClick = onPreviousClick,
                    style = CircularIconButtonStyle.DEFAULT,
                    size = 24.dp,
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Play/Pause button with larger touch target
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

                // Next chapter button
                CircularIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.nextChapter),
                    onClick = onNextClick,
                    style = CircularIconButtonStyle.DEFAULT,
                    size = 24.dp,
                )
            }

            // Progress indicator
            ThinProgressBar(
                progress = progress,
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
            progress = 0.42f,
            onPlayPauseClick = {},
            onMiniPlayerClick = {},
        )
    }
}

/**
 * Animated container for MiniPlayer with slide-in/out animations.
 */
@Composable
public fun AnimatedMiniPlayer(
    visible: Boolean,
    coverUrl: String?,
    title: String,
    author: String,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = visible,
        enter =
            if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                )
            },
        exit =
            if (reduceMotion) {
                ExitTransition.None
            } else {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec =
                        tween(
                            durationMillis = MotionTokens.MEDIUM1,
                            easing = MotionTokens.EmphasizedAccelerate,
                        ),
                )
            },
        modifier = modifier,
    ) {
        MiniPlayer(
            coverUrl = coverUrl,
            title = title,
            author = author,
            isPlaying = isPlaying,
            progress = progress,
            onPlayPauseClick = onPlayPauseClick,
            onMiniPlayerClick = onMiniPlayerClick,
            onNextClick = onNextClick,
            onPreviousClick = onPreviousClick,
            onDismiss = onDismiss,
        )
    }
}
