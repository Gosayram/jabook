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

package com.jabook.app.jabook.compose.feature.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
public fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(top = 100.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Find current line index (last line that started before currentPosition)
    val currentIndex by remember(currentPosition, lyrics) {
        derivedStateOf {
            lyrics.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
        }
    }

    // Auto-scroll logic
    // We want to scroll so the active item is centered.
    LaunchedEffect(currentIndex) {
        if (lyrics.isNotEmpty()) {
            try {
                // Calculate offset to center the item
                // This is an estimation, precise centering requires item height knowledge
                // which LazyList doesn't provide easily for non-visible items.
                // However, animateScrollToItem with generous padding usually works well.
                listState.animateScrollToItem(
                    index = currentIndex,
                    // 150dp offset is roughly half of the container height (300dp-ish usually)
                    scrollOffset = -with(density) { 100.dp.toPx() }.roundToInt(),
                )
            } catch (e: Exception) {
                // Ignore scroll errors
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrent = index == currentIndex

                // Animate properties for smooth transition
                val alpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isCurrent) 1f else 0.5f,
                    label = "alpha",
                )
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isCurrent) 1.1f else 1.0f,
                    label = "scale",
                )

                // Highlight color
                val color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.6f)

                Text(
                    text = line.text,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(line.timeMs) }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                            .graphicsLayer {
                                this.scaleX = scale
                                this.scaleY = scale
                                this.alpha = alpha
                            },
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            shadow =
                                if (isCurrent) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        blurRadius = 12f,
                                    )
                                } else {
                                    null
                                },
                        ),
                    color = color,
                )
            }
        }

        // Top Fade Gradient
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        brush =
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), // Matching container bg
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )

        // Bottom Fade Gradient
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        brush =
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ),
                            ),
                    ),
        )
    }
}
