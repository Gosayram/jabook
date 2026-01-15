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

package com.jabook.app.jabook.compose.feature.player.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


/**
 * A hypnotic, animated background effect for the player.
 * Uses extracted palette colors from the artwork to create a dynamic,
 * shifting gradient mesh that mimics an aurora or flow.
 *
 * Inspired by modern music players (Apple Music, Spotify).
 */
@Composable
public fun HypnoticBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (colors.isEmpty()) return

    // Ensure we have at least 3 colors for the effect
    val color1 = colors.getOrElse(0) { Color.Black }
    val color2 = colors.getOrElse(1) { Color.DarkGray }
    val color3 = colors.getOrElse(2) { Color.Gray }
    val color4 = colors.getOrElse(3) { color1 } // Loop back or use another if available

    // Animation transition
    val infiniteTransition = rememberInfiniteTransition(label = "HypnoticAnimation")

    // Animate gradient positions/phases
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Phase2"
    )
    
     val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Phase3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Base
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw a base gradient
             drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color1.copy(alpha=0.4f), color2.copy(alpha = 0.8f))
                )
            )

            // Draw moving "blobs" or gradients
            // Blob 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color3.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(width * 0.2f + (width * 0.6f * phase1), height * 0.3f),
                    radius = width * 0.8f
                ),
                center = Offset(width * 0.2f + (width * 0.6f * phase1), height * 0.3f),
                radius = width * 0.8f
            )

            // Blob 2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color2.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(width * 0.8f - (width * 0.6f * phase2), height * 0.7f),
                    radius = width * 0.7f
                ),
                center = Offset(width * 0.8f - (width * 0.6f * phase2), height * 0.7f),
                radius = width * 0.7f
            )
            
             // Blob 3
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color4.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(width * 0.5f, height * 0.5f + (height * 0.3f * phase3)),
                    radius = width * 0.9f
                ),
                center = Offset(width * 0.5f, height * 0.5f + (height * 0.3f * phase3)),
                radius = width * 0.9f
            )

            // Overlay a blur if possible, or use a scrim to soften
            // Since Blur on Canvas isn't directly supported in Compose without render effects (Android 12+),
            // we rely on the large radial gradients to create softness.
            
            // Vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    center = center,
                    radius = size.minDimension
                )
            )
        }
        
        // Add a blur layer using RenderEffect if on Android 12+ 
        // For now, we stick to standard Canvas drawing for compatibility.
        // If we wanted to use RenderEffect, we would use .graphicsLayer { renderEffect = ... }
    }
}
