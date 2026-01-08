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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.isActive

/**
 * A rotating vinyl record cover component.
 */
@Composable
fun VinylCover(
    imageRequest: ImageRequest,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    rotationSpeedMs: Int = 10000, // 10 seconds for full rotation
) {
    // Advanced rotation logic to pause at current angle
    val currentRotation =
        androidx.compose.runtime.remember {
            androidx.compose.animation.core
                .Animatable(0f)
        }

    androidx.compose.runtime.LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Infinite rotation
            // We use targetValue = currentRotation.value + 360f to continue from where we are
            // But Animatable.animateTo isn't infinite.
            // We need a loop.
            while (isActive) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(rotationSpeedMs, easing = LinearEasing),
                )
            }
        } else {
            // Just stop updating (the value remains at current point)
            currentRotation.stop()
        }
    }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .rotate(currentRotation.value)
                .clip(CircleShape)
                .background(Color.Black),
        // Vinyl record color
        contentAlignment = Alignment.Center,
    ) {
        // Vinyl grooves/texture effect (simplified as a gradient or just dark circle)
        // We can draw concentric circles or just rely on the cover being small in the middle.

        // 1. Full size dark background (Vinyl disk)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF222222),
                                    Color(0xFF111111),
                                    Color(0xFF000000),
                                ),
                        ),
                    ),
        )

        // 2. The Cover Art (Label) in the center
        // Typically vinyl labels are about 1/3 to 1/2 of the diameter.
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth(0.55f) // Adjust size for "Label" look
                    .aspectRatio(1f)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )

        // 3. Center hole
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black), // Or see-through if we want
        )

        // 4. Glossy reflection overlay (static) to simulate plastic
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.02f),
                                ),
                            start = Offset(0f, 0f),
                            end = Offset(100f, 100f), // approximate diagonal
                        ),
                    ),
        )
    }
}
