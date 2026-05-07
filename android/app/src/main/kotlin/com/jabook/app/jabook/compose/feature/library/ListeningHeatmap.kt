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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max

@Composable
public fun ListeningHeatmap(
    data: Map<LocalDate, Int>,
    weeks: Int = 26,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val startDate = today.minusWeeks(weeks.toLong())
    val maxMinutes = max(data.values.maxOrNull() ?: 0, 1)
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(100.dp),
    ) {
        val gap = 2.dp.toPx()
        val cellWidth = ((size.width - gap * (weeks - 1)) / weeks).coerceAtLeast(2.dp.toPx())
        val cellHeight = ((size.height - gap * 6f) / 7f).coerceAtLeast(2.dp.toPx())

        var date = startDate
        var col = 0
        while (!date.isAfter(today) && col < weeks) {
            val row = date.dayOfWeek.value - 1
            val minutes = data[date] ?: 0
            val intensity = (minutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
            drawRoundRect(
                color = primaryColor.copy(alpha = if (minutes == 0) 0.08f else 0.15f + intensity * 0.85f),
                topLeft = Offset(x = col * (cellWidth + gap), y = row * (cellHeight + gap)),
                size = Size(cellWidth, cellHeight),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            date = date.plusDays(1)
            if (date.dayOfWeek == DayOfWeek.MONDAY) {
                col++
            }
        }
    }
}
