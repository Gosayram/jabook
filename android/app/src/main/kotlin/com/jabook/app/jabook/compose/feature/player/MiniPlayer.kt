// Copyright 2025 Jabook Contributors
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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import kotlin.math.roundToInt

/**
 * Mini player component displayed above bottom navigation.
 *
 * Shows current book cover, title, author, and play/pause control.
 * Tapping the card navigates to full player screen.
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
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = 300),
        label = "miniPlayerOffset",
    )

    // Threshold for dismissing (40% of screen width)
    val dismissThreshold = with(density) { 200.dp.toPx() }

    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        // Only handle click if not dragging
                        if (kotlin.math.abs(offsetX) < 10f) {
                            onMiniPlayerClick()
                        }
                    },
                ).pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // If dragged beyond threshold, dismiss
                            if (kotlin.math.abs(offsetX) > dismissThreshold) {
                                onDismiss()
                            } else {
                                // Otherwise, snap back
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            // Snap back on cancel
                            offsetX = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        // Accumulate drag amount
                        offsetX = (offsetX + dragAmount).coerceIn(-dismissThreshold * 1.5f, dismissThreshold * 1.5f)
                    }
                },
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover image (40dp square)
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and author
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
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

                // Play/Pause button
                IconButton(
                    onClick = onPlayPauseClick,
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
                    )
                }
            }

            // Progress indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
