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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.processors.EqualizerPreset
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import java.util.Locale

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
    val selectedEqPreset =
        remember(protoSettings.equalizerPreset) {
            runCatching {
                EqualizerPreset.valueOf(protoSettings.equalizerPreset.ifBlank { EqualizerPreset.DEFAULT.name })
            }.getOrDefault(EqualizerPreset.DEFAULT)
        }

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateUp = dropUnlessResumed { navigationClickGuard.run(onNavigateUp) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audioSettingsTitle)) },
                navigationIcon = {
                    IconButton(onClick = { safeNavigateUp() }) {
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
            SettingsSection(
                title = stringResource(R.string.playback_general),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // Auto-rewind on pause
            SettingsItem(
                title = stringResource(R.string.resume_rewind_title),
                subtitle = stringResource(R.string.resume_rewind_desc),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected =
                            protoSettings.resumeRewindMode ==
                                com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART,
                        onClick = {
                            viewModel.updateAudioSettings(
                                resumeRewindMode = com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART,
                            )
                        },
                        label = { Text(stringResource(R.string.resume_rewind_mode_smart)) },
                    )
                    FilterChip(
                        selected =
                            protoSettings.resumeRewindMode ==
                                com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED,
                        onClick = {
                            viewModel.updateAudioSettings(
                                resumeRewindMode = com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED,
                            )
                        },
                        label = { Text(stringResource(R.string.resume_rewind_mode_fixed)) },
                    )
                }
            }

            if (protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED) {
                SettingsItem(
                    title = stringResource(R.string.resume_rewind_fixed_title),
                    subtitle = stringResource(R.string.resume_rewind_fixed_desc),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val options = listOf(0, 5, 10, 30)
                        options.forEach { seconds ->
                            FilterChip(
                                selected = protoSettings.resumeRewindSeconds == seconds,
                                onClick = {
                                    viewModel.updateAudioSettings(resumeRewindSeconds = seconds)
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.resume_rewind_option_seconds,
                                            seconds,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            } else {
                SettingsSliderItem(
                    title = stringResource(R.string.resume_rewind_aggressiveness_title),
                    subtitle = stringResource(R.string.resume_rewind_aggressiveness_desc),
                    sliderValue = protoSettings.resumeRewindAggressiveness,
                    onValueChange = {
                        viewModel.updateAudioSettings(
                            resumeRewindAggressiveness = it,
                        )
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    valueFormatter = { String.format(java.util.Locale.US, "%.2fx", it) },
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            SettingsSwitchItem(
                title = stringResource(R.string.sleep_timer_shake_extend_title),
                subtitle = stringResource(R.string.sleep_timer_shake_extend_desc),
                checked = protoSettings.sleepTimerShakeExtendEnabled,
                onCheckedChange = {
                    viewModel.updateAudioSettings(sleepTimerShakeExtendEnabled = it)
                },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.hold_to_boost_speed_title),
                subtitle = stringResource(R.string.hold_to_boost_speed_desc),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(2.0f, 2.5f, 3.0f).forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(protoSettings.holdToBoostSpeed - speed) < 0.01f,
                            onClick = { viewModel.updateAudioSettings(holdToBoostSpeed = speed) },
                            label = { Text(stringResource(R.string.playback_speed_format, speed)) },
                        )
                    }
                }
            }

            SettingsSwitchItem(
                title = stringResource(R.string.auto_pip_title),
                subtitle = stringResource(R.string.auto_pip_desc),
                checked = protoSettings.autoPipEnabled,
                onCheckedChange = { viewModel.updateAudioSettings(autoPipEnabled = it) },
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

                SettingsItem(
                    title = stringResource(R.string.skip_silence_mode_title),
                    subtitle = stringResource(R.string.skip_silence_mode_desc),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected =
                                protoSettings.skipSilenceMode ==
                                    com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP,
                            onClick = {
                                viewModel.updateAudioSettings(
                                    skipSilenceMode = com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP,
                                )
                            },
                            label = { Text(stringResource(R.string.skip_silence_mode_skip)) },
                        )
                        FilterChip(
                            selected =
                                protoSettings.skipSilenceMode ==
                                    com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SPEED_UP,
                            onClick = {
                                viewModel.updateAudioSettings(
                                    skipSilenceMode = com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SPEED_UP,
                                )
                            },
                            label = { Text(stringResource(R.string.skip_silence_mode_speed_up)) },
                        )
                    }
                }
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

            HorizontalDivider()
            SettingsSection(
                title = stringResource(R.string.equalizer_section_title),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.equalizer_preset_title),
                subtitle = stringResource(R.string.equalizer_preset_desc),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqualizerPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedEqPreset == preset,
                            onClick = { viewModel.updateEqualizerPreset(preset.name) },
                            label = {
                                Text(
                                    text =
                                        when (preset) {
                                            EqualizerPreset.FLAT -> preset.displayName
                                            EqualizerPreset.VOICE_CLARITY -> stringResource(R.string.equalizer_preset_voice_clarity)
                                            EqualizerPreset.NIGHT -> stringResource(R.string.equalizer_preset_night)
                                        },
                                )
                            },
                        )
                    }
                }
            }

            EqualizerFiveBandCard(
                preset = selectedEqPreset,
                modifier = Modifier.padding(horizontal = contentPadding).padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun EqualizerFiveBandCard(
    preset: EqualizerPreset,
    modifier: Modifier = Modifier,
) {
    val bandLabels = listOf("63", "250", "1k", "4k", "8k")
    val source = preset.bandGainsMb
    val effectivePreampDb = preset.effectivePreamp() / 100f
    val mapped =
        listOf(
            (source.getOrNull(1)?.div(100f) ?: 0f) + effectivePreampDb,
            (source.getOrNull(3)?.div(100f) ?: 0f) + effectivePreampDb,
            (source.getOrNull(5)?.div(100f) ?: 0f) + effectivePreampDb,
            (source.getOrNull(7)?.div(100f) ?: 0f) + effectivePreampDb,
            (source.getOrNull(8)?.div(100f) ?: 0f) + effectivePreampDb,
        )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.equalizer_five_band_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            mapped.forEachIndexed { index, gain ->
                EqBandPreview(
                    label = bandLabels[index],
                    gainDb = gain,
                )
            }
        }
    }
}

@Composable
private fun EqBandPreview(
    label: String,
    gainDb: Float,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val formattedGainDb = String.format(Locale.getDefault(), "%.1f dB", gainDb)
        Text(
            text = formattedGainDb,
            style = MaterialTheme.typography.labelSmall,
        )
        Box(
            modifier = Modifier.height(130.dp).width(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = gainDb.coerceIn(-12f, 12f),
                onValueChange = {},
                valueRange = -12f..12f,
                steps = 23,
                enabled = false,
                modifier =
                    Modifier
                        .requiredWidth(130.dp)
                        .clearAndSetSemantics { }
                        .graphicsLayer { rotationZ = -90f },
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
