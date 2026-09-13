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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.core.theme.MotionTokens

/** Shimmer sweep duration — intentionally slow for visual sweep effect. */
private const val SHIMMER_DURATION_MS = 1200

/**
 * Creates a shimmer [Brush] that sweeps diagonally across the component.
 *
 * The animated shift is read inside [ShaderBrush.createShader] (draw phase),
 * so the sweep animates without recomposition and without allocating a new brush per frame.
 */
@Composable
public fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val shift =
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = MotionTokens.Linear),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer_shift",
        )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    return remember(base, highlight) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val width = size.width.coerceAtLeast(1f)
                val height = size.height.coerceAtLeast(1f)
                val x = shift.value * width
                return LinearGradientShader(
                    colors = listOf(base, highlight, base),
                    from = Offset(x - width, 0f),
                    to = Offset(x, height),
                    tileMode = TileMode.Clamp,
                )
            }
        }
    }
}

/**
 * Single book card skeleton for use inside LazyGrid items.
 * Matches the visual structure of a real [BookCard]: cover + title + author lines.
 */
@Composable
public fun ShimmerBookCard(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush),
        )
        SkeletonLine(widthFraction = 0.85f, brush = brush)
        SkeletonLine(widthFraction = 0.55f, brush = brush)
    }
}

/**
 * Library skeleton placeholders with shimmer effect.
 * Shows a 2-column grid of placeholder book cards.
 */
@Composable
public fun LibraryLoadingSkeleton(
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val shimmerBrush = rememberShimmerBrush()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(2) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(shimmerBrush),
                        )
                        SkeletonLine(widthFraction = 0.85f, brush = shimmerBrush)
                        SkeletonLine(widthFraction = 0.55f, brush = shimmerBrush)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    widthFraction: Float,
    brush: Brush,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
    )
}
