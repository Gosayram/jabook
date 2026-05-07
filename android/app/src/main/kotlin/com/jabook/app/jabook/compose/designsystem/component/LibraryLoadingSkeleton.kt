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

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Library skeleton placeholders with shimmer effect.
 */
@Composable
public fun LibraryLoadingSkeleton(
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "library_skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "skeleton_shift",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val shimmerBrush =
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start =
                androidx.compose.ui.geometry
                    .Offset(shift * 400f - 400f, 0f),
            end =
                androidx.compose.ui.geometry
                    .Offset(shift * 400f, 400f),
        )

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
                                    .size(height = 180.dp, width = 120.dp)
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
                .size(height = 12.dp, width = 40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
    )
}
