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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Audio visualizer style options.
 */
enum class VisualizerStyle {
    /** Smooth waveform line */
    WAVEFORM,

    /** Vertical bars (like equalizer) */
    BARS,

    /** Circular visualization */
    CIRCULAR,
}

/**
 * Audio visualizer component that displays waveform or FFT data.
 *
 * @param waveformData Normalized waveform samples (-1.0 to 1.0)
 * @param isPlaying Whether audio is currently playing
 * @param style Visualization style
 * @param height Component height
 * @param modifier Modifier
 */
@Composable
fun AudioVisualizer(
    waveformData: FloatArray,
    isPlaying: Boolean,
    style: VisualizerStyle = VisualizerStyle.BARS,
    height: Dp = 48.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
) {
    // Animate visibility based on playback state
    val alpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.3f,
        animationSpec = tween(300),
        label = "visualizerAlpha",
    )

    when (style) {
        VisualizerStyle.WAVEFORM ->
            WaveformVisualizer(
                waveformData = waveformData,
                color = primaryColor.copy(alpha = alpha),
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(height),
            )
        VisualizerStyle.BARS ->
            BarsVisualizer(
                waveformData = waveformData,
                primaryColor = primaryColor.copy(alpha = alpha),
                secondaryColor = secondaryColor.copy(alpha = alpha * 0.5f),
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(height),
            )
        VisualizerStyle.CIRCULAR ->
            CircularVisualizer(
                waveformData = waveformData,
                color = primaryColor.copy(alpha = alpha),
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(height),
            )
    }
}

/**
 * Waveform line visualization.
 */
@Composable
private fun WaveformVisualizer(
    waveformData: FloatArray,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (waveformData.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val centerY = height / 2

        val path =
            Path().apply {
                val sampleWidth = width / waveformData.size
                moveTo(0f, centerY)

                waveformData.forEachIndexed { index, amplitude ->
                    val x = index * sampleWidth
                    val y = centerY - (amplitude * centerY * 0.8f)
                    if (index == 0) {
                        moveTo(x, y)
                    } else {
                        lineTo(x, y)
                    }
                }
            }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Bar/equalizer style visualization.
 */
@Composable
private fun BarsVisualizer(
    waveformData: FloatArray,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    // Reduce data to fewer bars for cleaner look
    val barCount = 32
    val reducedData =
        remember(waveformData) {
            if (waveformData.isEmpty()) {
                FloatArray(barCount) { 0f }
            } else {
                val step = waveformData.size / barCount
                FloatArray(barCount) { i ->
                    val startIdx = i * step
                    val endIdx = minOf(startIdx + step, waveformData.size)
                    var sum = 0f
                    for (j in startIdx until endIdx) {
                        sum += abs(waveformData[j])
                    }
                    sum / step
                }
            }
        }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.7f
        val spacing = (width / barCount) * 0.3f

        reducedData.forEachIndexed { index, amplitude ->
            val barHeight = (amplitude * height).coerceIn(4f, height)
            val x = index * (barWidth + spacing) + spacing / 2
            val y = (height - barHeight) / 2

            // Gradient from bottom to top
            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(secondaryColor, primaryColor),
                        startY = y + barHeight,
                        endY = y,
                    ),
                topLeft = Offset(x, y),
                size =
                    androidx.compose.ui.geometry
                        .Size(barWidth, barHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(barWidth / 2),
            )
        }
    }
}

/**
 * Circular visualization.
 */
@Composable
private fun CircularVisualizer(
    waveformData: FloatArray,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Reduce data points for cleaner radial bars
    val barCount = 40 // Amount of bars around the circle
    val reducedData =
        remember(waveformData) {
            if (waveformData.isEmpty()) {
                FloatArray(barCount) { 0f }
            } else {
                // Simple downsampling
                val step = waveformData.size / barCount
                FloatArray(barCount) { i ->
                    val startIdx = i * step
                    // Average amplitude for this chunk
                    var sum = 0f
                    val end = minOf((i + 1) * step, waveformData.size)
                    for (k in startIdx until end) {
                        sum += abs(waveformData[k])
                    }
                    if (end > startIdx) sum / (end - startIdx) else 0f
                }
            }
        }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        // Radius for the inner empty circle
        val innerRadius = minOf(centerX, centerY) * 0.4f
        val maxBarHeight = minOf(centerX, centerY) * 0.5f

        val angleStep = 360f / barCount
        val barWidth = (2 * Math.PI * innerRadius / barCount).toFloat() * 0.6f

        reducedData.forEachIndexed { index, amplitude ->
            // Mirrored visualization (two sides or full circle)
            // Here we map 0..barCount to 0..360 degrees

            // Smooth amplitude (scaling)
            val barHeight = (amplitude * maxBarHeight).coerceAtLeast(4f)

            val angle = index * angleStep

            // Rotate canvas to draw bar at correct angle
            rotate(degrees = angle, pivot = Offset(centerX, centerY)) {
                drawRoundRect(
                    color = color.copy(alpha = 0.8f),
                    topLeft = Offset(centerX - barWidth / 2, centerY - innerRadius - barHeight),
                    size =
                        androidx.compose.ui.geometry
                            .Size(barWidth, barHeight),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(barWidth / 2),
                )

                // Reflection (inner bar or opacity variation)
               /* drawRoundRect(
                    color = color.copy(alpha = 0.3f),
                    topLeft = Offset(centerX - barWidth / 2, centerY - innerRadius + 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight * 0.3f), // Small reflection inside
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                ) */
            }
        }
    }
}
