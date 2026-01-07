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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.constants.PlaybackSpeedConstants
import kotlin.math.roundToInt

/**
 * Bottom sheet for selecting playback speed.
 *
 * Features:
 * - Slider for fine-grained speed selection (0.5x - 2.0x)
 * - Preset chips for quick selection of common speeds
 * - Live preview of current speed
 *
 * @param currentSpeed Current playback speed (e.g., 1.0f)
 * @param onSpeedSelected Callback when speed is selected
 * @param onDismiss Callback to dismiss the sheet
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    // Local state for slider - allows real-time preview
    var sliderSpeed by remember { mutableFloatStateOf(currentSpeed) }

    // Preset speeds for quick selection
    val presetSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = stringResource(R.string.playbackSpeed),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Current speed display - large and prominent
            Text(
                text = formatSpeedDisplay(sliderSpeed),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Speed slider
            Slider(
                value = sliderSpeed,
                onValueChange = { newSpeed ->
                    // Round to nearest step for cleaner values
                    sliderSpeed = roundToStep(newSpeed)
                },
                onValueChangeFinished = {
                    onSpeedSelected(sliderSpeed)
                },
                valueRange = PlaybackSpeedConstants.MIN_SPEED..PlaybackSpeedConstants.MAX_SPEED,
                steps = PlaybackSpeedConstants.SLIDER_STEPS,
                modifier = Modifier.fillMaxWidth(),
            )

            // Min/Max labels
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${PlaybackSpeedConstants.MIN_SPEED}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${PlaybackSpeedConstants.MAX_SPEED}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Preset speed chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetSpeeds.forEach { speed ->
                    FilterChip(
                        selected = isSpeedSelected(sliderSpeed, speed),
                        onClick = {
                            sliderSpeed = speed
                            onSpeedSelected(speed)
                        },
                        label = {
                            Text(formatSpeedChip(speed))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Round speed to nearest step (0.05).
 */
private fun roundToStep(speed: Float): Float {
    val step = PlaybackSpeedConstants.SPEED_STEP
    return (speed / step).roundToInt() * step
}

/**
 * Check if speed matches preset (with tolerance for floating point).
 */
private fun isSpeedSelected(
    current: Float,
    preset: Float,
): Boolean = kotlin.math.abs(current - preset) < 0.01f

/**
 * Format speed for large display.
 */
private fun formatSpeedDisplay(speed: Float): String =
    if (speed % 1.0f == 0.0f) {
        "${speed.toInt()}x"
    } else {
        String.format("%.2fx", speed)
    }

/**
 * Format speed for chip label.
 */
private fun formatSpeedChip(speed: Float): String =
    if (speed % 1.0f == 0.0f) {
        "${speed.toInt()}x"
    } else if (speed * 100 % 10 == 0f) {
        String.format("%.1fx", speed)
    } else {
        String.format("%.2fx", speed)
    }
