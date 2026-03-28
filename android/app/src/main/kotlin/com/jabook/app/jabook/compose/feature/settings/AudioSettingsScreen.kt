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

package com.jabook.app.jabook.compose.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun AudioSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClassOrNull(rawWindowSizeClass, context)
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)
    val smallSpacing = AdaptiveUtils.getSmallSpacingOrDefault(windowSizeClass)

    val protoSettings by viewModel.protoSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audioSettingsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // General Playback
            SettingsSection(title = stringResource(R.string.playback_general), contentPadding = contentPadding, itemSpacing = itemSpacing)

            // Auto-rewind on pause
            SettingsSwitchItem(
                title = stringResource(R.string.auto_rewind_title),
                subtitle = stringResource(R.string.auto_rewind_desc),
                checked = protoSettings.autoRewindOnPause,
                onCheckedChange = {
                    viewModel.updateAudioSettings(rewindSeconds = if (it) 2 else 0)
                },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            // Audio Quality (Phase 1.2 features)
            HorizontalDivider()
            SettingsSection(
                title = stringResource(R.string.audio_quality_title),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // Crossfade
            SettingsSwitchItem(
                title = stringResource(R.string.crossfade_title),
                subtitle = stringResource(R.string.crossfade_desc),
                checked = protoSettings.crossfadeEnabled,
                onCheckedChange = { viewModel.updateAudioSettings(crossfadeEnabled = it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            if (protoSettings.crossfadeEnabled) {
                SettingsSliderItem(
                    title = stringResource(R.string.crossfade_duration),
                    sliderValue = protoSettings.crossfadeDurationMs.toFloat(),
                    onValueChange = { viewModel.updateAudioSettings(crossfadeDurationMs = it.toLong()) },
                    valueRange = 1000f..10000f,
                    steps = 8, // 1s to 10s
                    valueFormatter = { "${(it / 1000).toInt()} s" },
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            // Skip Silence
            SettingsSwitchItem(
                title = stringResource(R.string.skip_silence_title),
                subtitle = stringResource(R.string.skip_silence_desc),
                checked = protoSettings.skipSilence,
                onCheckedChange = { viewModel.updateAudioSettings(skipSilence = it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            if (protoSettings.skipSilence) {
                SettingsSliderItem(
                    title = stringResource(R.string.skip_silence_threshold_title),
                    subtitle = stringResource(R.string.skip_silence_threshold_desc),
                    sliderValue = protoSettings.skipSilenceThresholdDb,
                    onValueChange = { viewModel.updateAudioSettings(skipSilenceThresholdDb = it) },
                    valueRange = -40f..-20f,
                    steps = 19,
                    valueFormatter = { "${it.toInt()} dB" },
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )

                SettingsSliderItem(
                    title = stringResource(R.string.skip_silence_min_ms_title),
                    subtitle = stringResource(R.string.skip_silence_min_ms_desc),
                    sliderValue = protoSettings.skipSilenceMinMs.toFloat(),
                    onValueChange = { viewModel.updateAudioSettings(skipSilenceMinMs = it.toInt()) },
                    valueRange = 150f..300f,
                    steps = 14,
                    valueFormatter = { "${it.toInt()} ms" },
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            // Volume Normalization
            SettingsSwitchItem(
                title = stringResource(R.string.normalizeVolumeTitle),
                subtitle = stringResource(R.string.normalizeVolumeDescription),
                checked = protoSettings.normalizeVolume,
                onCheckedChange = { viewModel.updateAudioSettings(normalizeVolume = it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            HorizontalDivider()
            SettingsSection(
                title = stringResource(R.string.audioEnhancementTitle),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // Volume Boost
            // We need a selector for string enum "Off", "Boost50", etc.
            // Using a simple dialog or dropdown could work, but SettingsItem usually has dialog logic internal or we implement it here.

            // For now, I'll rely on string resources which I need to create.
        }
    }
}
