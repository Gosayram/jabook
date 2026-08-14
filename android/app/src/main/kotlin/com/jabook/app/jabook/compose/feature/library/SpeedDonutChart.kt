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

package com.jabook.app.jabook.compose.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R

@Composable
public fun SpeedDonutChart(
    distribution: Map<Float, Long>,
    modifier: Modifier = Modifier,
) {
    val total =
        distribution.values
            .sum()
            .toFloat()
            .takeIf { it > 0f } ?: return
    val primary = MaterialTheme.colorScheme.primary

    val topSpeed = distribution.entries.maxByOrNull { it.value }?.key ?: 1.0f
    val accessibilityDesc =
        stringResource(R.string.speedChartAccessibilityDescription, topSpeed)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityDesc },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Canvas(
            modifier =
                Modifier
                    .size(96.dp)
                    .height(96.dp),
        ) {
            var startAngle = -90f
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            distribution.entries.sortedBy { it.key }.forEach { (speed, ms) ->
                val sweep = (ms / total) * 360f
                drawArc(
                    color = primary.copy(alpha = 0.3f + (speed / 3f) * 0.7f),
                    startAngle = startAngle,
                    sweepAngle = (sweep - 1f).coerceAtLeast(0.5f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                    size = Size(diameter, diameter),
                )
                startAngle += sweep
            }
        }
        Column {
            distribution.entries.sortedByDescending { it.value }.forEach { (speed, ms) ->
                Text(
                    text = stringResource(R.string.speedLegendFormat, speed, (ms / 60000L).coerceAtLeast(1)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
