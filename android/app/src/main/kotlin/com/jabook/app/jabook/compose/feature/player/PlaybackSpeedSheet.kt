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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.processors.SpeedDialPolicy

/**
 * Bottom sheet for selecting playback speed.
 *
 * Features:
 * - Preset chips for quick selection of common speeds
 * - Long-press on any preset opens fine-tuning slider (0.5x - 3.5x)
 * - Live preview of current speed
 * - Pitch correction toggle
 *
 * @param currentSpeed Current playback speed (e.g., 1.0f)
 * @param onSpeedSelected Callback when speed is selected
 * @param onDismiss Callback to dismiss the sheet
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
public fun PlaybackSpeedSheet(
    currentSpeed: Float,
    pitchCorrectionEnabled: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onPitchCorrectionChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    var sliderSpeed by remember { mutableFloatStateOf(currentSpeed) }
    var fineTuneSpeed by remember { mutableFloatStateOf(currentSpeed) }
    var showFineTuneDialog by remember { mutableStateOf(false) }
    val recentSpeeds =
        rememberSaveable(
            saver =
                listSaver(
                    save = { it.toList() },
                    restore = {
                        mutableStateListOf<Float>().apply {
                            addAll(it)
                        }
                    },
                ),
        ) {
            mutableStateListOf(currentSpeed)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.playbackSpeed),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = SpeedDialPolicy.formatSpeed(sliderSpeed),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            SpeedPresetsRow(
                currentSpeed = sliderSpeed,
                onPresetClick = { speed ->
                    sliderSpeed = speed
                    onSpeedSelected(speed)
                    addRecentSpeed(recentSpeeds, speed)
                },
                onPresetLongClick = { speed ->
                    fineTuneSpeed = speed
                    showFineTuneDialog = true
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.foundation.layout.Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .semantics { role = Role.Switch }
                        .toggleable(
                            value = pitchCorrectionEnabled,
                            onValueChange = onPitchCorrectionChanged,
                            role = Role.Switch,
                        ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pitchCorrection),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.pitchCorrectionDesc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = pitchCorrectionEnabled,
                    onCheckedChange = null,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (recentSpeeds.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.recentSpeedsTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentSpeeds.take(3).forEach { speed ->
                        FilterChip(
                            selected = isSpeedSelected(sliderSpeed, speed),
                            onClick = {
                                sliderSpeed = speed
                                onSpeedSelected(speed)
                                addRecentSpeed(recentSpeeds, speed)
                            },
                            label = { Text(SpeedDialPolicy.formatSpeed(speed)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showFineTuneDialog) {
        SpeedFineTuneDialog(
            initialSpeed = fineTuneSpeed,
            onSpeedSelected = { speed ->
                sliderSpeed = speed
                onSpeedSelected(speed)
                addRecentSpeed(recentSpeeds, speed)
            },
            onDismiss = { showFineTuneDialog = false },
        )
    }
}

@Composable
private fun SpeedFineTuneDialog(
    initialSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderSpeed by remember { mutableFloatStateOf(initialSpeed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.fineTuneSpeed))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = SpeedDialPolicy.formatSpeed(sliderSpeed),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderSpeed,
                    onValueChange = { sliderSpeed = SpeedDialPolicy.snapToStep(it) },
                    valueRange = SpeedDialPolicy.MIN_SPEED..SpeedDialPolicy.MAX_SPEED,
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = SpeedDialPolicy.formatSpeed(SpeedDialPolicy.MIN_SPEED),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = SpeedDialPolicy.formatSpeed(SpeedDialPolicy.MAX_SPEED),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSpeedSelected(SpeedDialPolicy.snapToStep(sliderSpeed))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

internal fun isSpeedSelected(
    current: Float,
    preset: Float,
): Boolean = kotlin.math.abs(current - preset) < 0.01f

internal fun addRecentSpeed(
    recentSpeeds: MutableList<Float>,
    speed: Float,
) {
    recentSpeeds.removeAll { kotlin.math.abs(it - speed) < 0.01f }
    recentSpeeds.add(0, speed)
    while (recentSpeeds.size > 3) {
        recentSpeeds.removeAt(recentSpeeds.lastIndex)
    }
}
