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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.audio.processors.VolumeBoostLevel
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet

/**
 * Audio settings sheet for configuring audio effects.
 *
 * Allows users to configure:
 * - Volume Boost
 * - Silence Skipping
 * - Volume Normalization
 * - Speech Enhancement
 * - Auto Volume Leveling
 *
 * @param state Current audio settings state
 * @param onUpdateSettings Callback to update audio settings
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AudioSettingsSheet(
    state: PlayerViewModel.AudioSettingsState,
    onUpdateSettings: (
        volumeBoostLevel: VolumeBoostLevel?,
        skipSilence: Boolean?,
        skipSilenceThresholdDb: Float?,
        skipSilenceMinMs: Int?,
        normalizeVolume: Boolean?,
        speechEnhancer: Boolean?,
        autoVolumeLeveling: Boolean?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    JabookModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Audio Enhancements", // TODO: Move to strings.xml
                style = MaterialTheme.typography.headlineSmall,
            )

            // Volume Boost Section
            Text(
                text = "Volume Boost", // TODO: Move to strings.xml
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            // Volume Boost Chips
            // Arranged in rows to fit
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeBoostChip(
                        label = "Off",
                        selected = state.volumeBoostLevel == VolumeBoostLevel.Off,
                        onClick = { onUpdateSettings(VolumeBoostLevel.Off, null, null, null, null, null, null) },
                    )
                    VolumeBoostChip(
                        label = "+50%",
                        selected = state.volumeBoostLevel == VolumeBoostLevel.Boost50,
                        onClick = { onUpdateSettings(VolumeBoostLevel.Boost50, null, null, null, null, null, null) },
                    )
                    VolumeBoostChip(
                        label = "+100%",
                        selected = state.volumeBoostLevel == VolumeBoostLevel.Boost100,
                        onClick = { onUpdateSettings(VolumeBoostLevel.Boost100, null, null, null, null, null, null) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeBoostChip(
                        label = "+200%",
                        selected = state.volumeBoostLevel == VolumeBoostLevel.Boost200,
                        onClick = { onUpdateSettings(VolumeBoostLevel.Boost200, null, null, null, null, null, null) },
                    )
                    VolumeBoostChip(
                        label = "Auto",
                        selected = state.volumeBoostLevel == VolumeBoostLevel.Auto,
                        onClick = { onUpdateSettings(VolumeBoostLevel.Auto, null, null, null, null, null, null) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Smart Effects Section
            Text(
                text = "Smart Effects", // TODO: Move to strings.xml
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            // Silence Skip
            AudioSettingSwitch(
                title = "Skip Silence", // TODO: strings.xml
                description = "Automatically skip silent parts",
                checked = state.skipSilence,
                onCheckedChange = { onUpdateSettings(null, it, null, null, null, null, null) },
            )

            // Normalize Volume
            AudioSettingSwitch(
                title = "Normalize Volume", // TODO: strings.xml
                description = "Keep consistent volume across tracks",
                checked = state.normalizeVolume,
                onCheckedChange = { onUpdateSettings(null, null, null, null, it, null, null) },
            )

            // Speech Enhancer
            AudioSettingSwitch(
                title = "Speech Enhancer", // TODO: strings.xml
                description = "Clarify voices and reduce background noise",
                checked = state.speechEnhancer,
                onCheckedChange = { onUpdateSettings(null, null, null, null, null, it, null) },
            )

            // Auto Volume Leveling
            AudioSettingSwitch(
                title = "Auto Leveling", // TODO: strings.xml
                description = "Adjust volume dynamically",
                checked = state.autoVolumeLeveling,
                onCheckedChange = { onUpdateSettings(null, null, null, null, null, null, it) },
            )

            // Bottom padding for navigation bar
            androidx.compose.foundation.layout
                .Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VolumeBoostChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun AudioSettingSwitch(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
