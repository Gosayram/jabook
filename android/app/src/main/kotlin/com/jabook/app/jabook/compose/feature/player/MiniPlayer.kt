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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import com.jabook.app.jabook.R
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
fun MiniPlayer(
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
    val alpha by animateFloatAsState(
        targetValue = 1f - (dragProgress * 0.5f),
        animationSpec = tween(100),
        label = "miniPlayerAlpha",
    )

    // Scale animation during drag
    val scale by animateFloatAsState(
        targetValue = 1f - (dragProgress * 0.05f),
        animationSpec = tween(100),
        label = "miniPlayerScale",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                    translationY = animatedOffsetY
                    scaleX = scale
                    scaleY = scale
                }.alpha(alpha)
                .clickable(
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
                                        onPreviousClick()
                                    } else {
                                        // Swiped Left -> Next
                                        onNextClick()
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
                                    onDismiss()
                                } else if (offsetY < -dismissThreshold) {
                                    // Swiped Up -> Open
                                    onMiniPlayerClick()
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
                    ) { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                        change.consume()

                        // Update offsets
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                        maxLines = 1,
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

                // Play/Pause button with larger touch target
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector =
                            if (isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                        contentDescription =
                            if (isPlaying) {
                                stringResource(R.string.pause)
                            } else {
                                stringResource(R.string.play)
                            },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Progress indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/**
 * Animated container for MiniPlayer with slide-in/out animations.
 */
@Composable
fun AnimatedMiniPlayer(
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
    AnimatedVisibility(
        visible = visible,
        enter =
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
            ),
        exit =
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200),
            ),
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
