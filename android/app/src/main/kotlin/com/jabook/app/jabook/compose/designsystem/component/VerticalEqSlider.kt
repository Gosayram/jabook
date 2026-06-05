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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vertical slider for a single equalizer band.
 *
 * Designed for use in a horizontal row of EQ bands. Shows:
 * - Current dB value label at top
 * - Vertical track with draggable thumb
 * - Frequency label at bottom
 *
 * @param frequencyHz Band center frequency in Hz (e.g. 60, 230, 910)
 * @param value Current gain in dB (-12 to +12)
 * @param onValueChange Callback when gain changes
 * @param modifier Modifier for sizing
 * @param minValue Minimum gain in dB
 * @param maxValue Maximum gain in dB
 * @param trackWidth Width of the slider track
 * @param thumbSize Size of the draggable thumb
 * @param trackHeight Total height of the track area
 */
@Composable
public fun VerticalEqSlider(
    frequencyHz: Int,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Float = -12f,
    maxValue: Float = 12f,
    trackWidth: Dp = 4.dp,
    thumbSize: Dp = 24.dp,
    trackHeight: Dp = 180.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val dbText =
            remember(value) {
                val sign = if (value >= 0) "+" else ""
                "$sign${"%.1f".format(value)} dB"
            }
        Text(
            text = dbText,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 9.sp,
        )

        VerticalTrack(
            value = value,
            onValueChange = onValueChange,
            minValue = minValue,
            maxValue = maxValue,
            trackWidth = trackWidth,
            thumbSize = thumbSize,
            trackHeight = trackHeight,
        )

        Text(
            text = formatFrequency(frequencyHz),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun VerticalTrack(
    value: Float,
    onValueChange: (Float) -> Unit,
    minValue: Float,
    maxValue: Float,
    trackWidth: Dp,
    thumbSize: Dp,
    trackHeight: Dp,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier =
            Modifier
                .width(thumbSize + 8.dp)
                .height(trackHeight),
        contentAlignment = Alignment.Center,
    ) {
        val fraction =
            remember(value, minValue, maxValue) {
                ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
            }

        val centerYFraction =
            remember(minValue, maxValue) {
                (-minValue) / (maxValue - minValue)
            }

        Canvas(
            modifier =
                Modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .pointerInput(minValue, maxValue) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val range = maxValue - minValue
                            val normalizedDrag = -dragAmount / size.height * range
                            val newValue = (value + normalizedDrag).coerceIn(minValue, maxValue)
                            val step = 0.5f
                            onValueChange(Math.round(newValue / step) * step)
                        }
                    },
        ) {
            val trackPx = trackWidth.toPx()
            val barX = (size.width - trackPx) / 2f
            val centerY = size.height * (1f - centerYFraction)

            drawRect(
                color = inactiveColor,
                topLeft = Offset(barX, 0f),
                size = Size(trackPx, size.height),
            )

            val thumbY = size.height * (1f - fraction)
            val topY = minOf(thumbY, centerY)
            val bottomY = maxOf(thumbY, centerY)

            drawRect(
                color = activeColor,
                topLeft = Offset(barX, topY),
                size = Size(trackPx, bottomY - topY),
            )

            drawCircle(
                color = thumbColor,
                radius = (thumbSize / 2).toPx(),
                center = Offset(size.width / 2f, thumbY),
                style = Fill,
            )
        }
    }
}

private fun formatFrequency(hz: Int): String =
    when {
        hz >= 1000 -> "${hz / 1000}.${(hz % 1000) / 100}kHz"
        else -> "${hz}Hz"
    }
