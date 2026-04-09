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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * A Premium "Squiggly" Slider that shows a sine wave animation when active/playing.
 * The wave straightens out when the user interacts (drags/presses) for precision.
 *
 * @param value Current value (0f..1f usually, but depends on valueRange)
 * @param onValueChange Callback for value change
 * @param modifier Modifier
 * @param enabled Whether slider is enabled
 * @param valueRange Range of values
 * @param isPlaying Whether media is playing (animates the wave)
 * @param squiggleAmplitude Max height of the wave
 * @param squiggleWavelength Width of one wave cycle
 * @param trackHeight Height of the track area
 * @param thumbRadius Radius of the thumb
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    squiggleAmplitude: Dp = 3.dp,
    squiggleWavelength: Dp = 20.dp,
    trackHeight: Dp = 4.dp, // Standard Material track is roughly 4dp
    thumbRadius: Dp = 10.dp,
    chapterMarkersFractions: List<Float> = emptyList(),
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    chapterMarkerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
) {
    val normalizedRange =
        remember(valueRange) {
            normalizeValueRange(valueRange)
        }
    val sanitizedChapterMarkers by remember(chapterMarkersFractions) {
        derivedStateOf {
            chapterMarkersFractions
                .asSequence()
                .filter { marker -> marker.isFinite() && marker > 0f && marker < 1f }
                .distinct()
                .sorted()
                .toList()
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    // Animation for the wave phase (movement)
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase",
    )

    // Animation for amplitude:
    // - 0f when interacting (straight line for precision)
    // - 0f when not playing (static straight or subtle?) -> Let's do 0f for static if paused, or maybe keep subtle?
    // Plan said "Squiggly Slider". Usually it's squiggly when playing.
    val targetAmplitude =
        if (isInteracting) {
            0f
        } else if (isPlaying) {
            1f
        } else {
            0f
        }
    val animatedAmplitudeScale by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(300),
        label = "amplitude",
    )

    Box(
        modifier = modifier.height(thumbRadius * 2), // Ensure enough height for thumb
        contentAlignment = Alignment.Center,
    ) {
        // Custom Track Drawing
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(thumbRadius * 2), // Match container
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2

            // Validate and sanitize value: check for NaN, Infinity, and ensure it's in valid range
            val sanitizedValue =
                when {
                    !value.isFinite() -> normalizedRange.start // Default to start if invalid
                    else -> value.coerceIn(normalizedRange.start, normalizedRange.endInclusive)
                }

            // Calculate progress ratio (0..1) with protection against division by zero
            val range = normalizedRange.endInclusive - normalizedRange.start
            val fraction =
                if (range > 0 && range.isFinite()) {
                    ((sanitizedValue - normalizedRange.start) / range).coerceIn(0f, 1f)
                } else {
                    0f
                }

            // Ensure activeWidth is valid and finite
            val activeWidth = (width * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f).coerceAtMost(width)

            // Draw Inactive Track (Straight line usually, strictly)
            // Or should the WHOLE track be squiggly? InnerTune usually has squiggly active part.
            // Let's draw inactive as straight line.
            drawLine(
                color = inactiveTrackColor,
                start = Offset(activeWidth, centerY),
                end = Offset(width, centerY),
                strokeWidth = trackHeight.toPx(),
                cap = StrokeCap.Round,
            )

            // Draw Active Track (Squiggly)
            if (activeWidth > 0) {
                val amplitudePx = squiggleAmplitude.toPx() * animatedAmplitudeScale
                val wavelengthPx = squiggleWavelength.toPx()

                if (amplitudePx < 1f) {
                    // Optimized: Draw straight line if amplitude is negligible
                    drawLine(
                        color = activeTrackColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeWidth, centerY),
                        strokeWidth = trackHeight.toPx(),
                        cap = StrokeCap.Round,
                    )
                } else {
                    val path = Path()
                    path.moveTo(0f, centerY)

                    val step = 5f // Precision
                    var x = 0f
                    while (x <= activeWidth) {
                        // y = A * sin(2*PI * (x/L - phase))
                        // We shift phase to make it move
                        val relX = x / wavelengthPx
                        val yOffset = amplitudePx * sin(2 * Math.PI * (relX - phase)).toFloat()
                        path.lineTo(x, centerY + yOffset)
                        x += step
                    }
                    // Ensure we connect exactly to the end point logic
                    // Actually lineTo covers it roughly, but let's be careful about the Thumb connection.
                    // The thumb will be at 'activeWidth'.

                    drawPath(
                        path = path,
                        color = activeTrackColor,
                        style =
                            Stroke(
                                width = trackHeight.toPx(),
                                cap = StrokeCap.Round,
                            ),
                    )
                }
            }

            // Draw chapter markers over the track.
            val markerHalfHeight = (trackHeight.toPx() * 1.5f).coerceAtLeast(3f)
            sanitizedChapterMarkers.forEach { markerFraction ->
                val markerX = width * markerFraction
                drawLine(
                    color = chapterMarkerColor,
                    start = Offset(markerX, centerY - markerHalfHeight),
                    end = Offset(markerX, centerY + markerHalfHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Invisible Material Slider to handle interactions and Thumb
        // We make the track transparent colors so we see our custom Canvas below
        Slider(
            value =
                if (value.isFinite()) {
                    value.coerceIn(normalizedRange.start, normalizedRange.endInclusive)
                } else {
                    normalizedRange.start
                },
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = normalizedRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors =
                SliderDefaults.colors(
                    thumbColor = activeTrackColor, // Visible Thumb
                    activeTrackColor = Color.Transparent, // Hidden standard track
                    inactiveTrackColor = Color.Transparent, // Hidden standard track
                    disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                ),
        )
    }
}

private fun normalizeValueRange(valueRange: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
    val start =
        if (valueRange.start.isFinite()) {
            valueRange.start
        } else {
            0f
        }
    val end =
        if (valueRange.endInclusive.isFinite()) {
            valueRange.endInclusive
        } else {
            1f
        }
    return if (start < end) {
        start..end
    } else {
        0f..1f
    }
}
