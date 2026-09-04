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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
public fun WeeklyListeningHistogram(
    minutesPerDay: List<Int>,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S"),
) {
    val max = minutesPerDay.maxOrNull()?.coerceAtLeast(1) ?: 1
    val avg = if (minutesPerDay.isEmpty()) 0 else minutesPerDay.sum() / minutesPerDay.size
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$avg min/day avg", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val labels = dayLabels.take(7)
            minutesPerDay.take(7).forEachIndexed { i, mins ->
                val h by animateDpAsState(
                    targetValue =
                        if (max ==
                            0
                        ) {
                            8.dp
                        } else {
                            (mins.toFloat() / max * 120).dp.coerceAtLeast(8.dp)
                        },
                    animationSpec = tween(400),
                    label = "bar$i",
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().height(h).background(
                                color =
                                    if (mins == minutesPerDay.maxOrNull() &&
                                        mins > 0
                                    ) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = labels.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun WeeklyListeningHistogramPreview() {
    MaterialTheme { WeeklyListeningHistogram(minutesPerDay = listOf(12, 45, 0, 30, 60, 15, 22)) }
}
