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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.processors.EqualizerPreset
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import kotlin.math.ln

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
                                            EqualizerPreset.FLAT -> stringResource(R.string.equalizer_preset_flat)
                                            EqualizerPreset.VOICE_CLARITY -> stringResource(R.string.equalizer_preset_voice_clarity)
                                            EqualizerPreset.NIGHT -> stringResource(R.string.equalizer_preset_night)
                                        },
                                )
                            },
                        )
                    }
                }
            }

            EqualizerCurveCard(
                preset = selectedEqPreset,
                modifier = Modifier.padding(horizontal = contentPadding).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun EqualizerCurveCard(
    preset: EqualizerPreset,
    modifier: Modifier = Modifier,
) {
    val frequencies = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val gainsDb =
        preset
            .bandGainsMb
            .map { it / 100f + preset.effectivePreamp() / 100f }
            .toFloatArray()
    val animatedGains =
        gainsDb.mapIndexed { index, gain ->
            animateFloatAsState(
                targetValue = gain,
                animationSpec = tween(durationMillis = 260 + index * 12),
                label = "eq_gain_$index",
            ).value
        }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val curveColor = MaterialTheme.colorScheme.primary
    val selectedPointColor = MaterialTheme.colorScheme.tertiary
    val selectedIndexState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val selectedIndex = selectedIndexState.intValue

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            val width = size.width.toFloat()
                            if (width <= 0f) return@detectTapGestures
                            val closestIndex =
                                frequencies.indices.minByOrNull { index ->
                                    val minFreq = frequencies.first()
                                    val maxFreq = frequencies.last()
                                    val lnMin = ln(minFreq)
                                    val lnMax = ln(maxFreq)
                                    val x = ((ln(frequencies[index]) - lnMin) / (lnMax - lnMin)) * width
                                    kotlin.math.abs(tapOffset.x - x)
                                } ?: -1
                            selectedIndexState.intValue = closestIndex
                        }
                    },
        ) {
            val minFreq = frequencies.first()
            val maxFreq = frequencies.last()
            val lnMin = ln(minFreq)
            val lnMax = ln(maxFreq)
            val minDb = -8f
            val maxDb = 8f

            fun xFor(freq: Float): Float = ((ln(freq) - lnMin) / (lnMax - lnMin)) * size.width

            fun yFor(db: Float): Float {
                val norm = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                return size.height - norm * size.height
            }

            val zeroY = yFor(0f)
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 2f,
            )

            frequencies.forEach { freq ->
                val x = xFor(freq)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
            }

            val curve = Path().apply { moveTo(xFor(frequencies.first()), yFor(animatedGains.first())) }
            for (i in 1 until frequencies.size) {
                curve.lineTo(xFor(frequencies[i]), yFor(animatedGains[i]))
            }
            drawPath(
                path = curve,
                color = curveColor,
                style = Stroke(width = 4f),
            )

            if (selectedIndex in frequencies.indices) {
                val pointX = xFor(frequencies[selectedIndex])
                val pointY = yFor(animatedGains[selectedIndex])
                drawCircle(
                    color = selectedPointColor,
                    radius = 6f,
                    center = Offset(pointX, pointY),
                )
                drawCircle(
                    color = curveColor,
                    radius = 3f,
                    center = Offset(pointX, pointY),
                )
            }
        }

        if (selectedIndex in frequencies.indices) {
            val selectedFrequency = frequencies[selectedIndex]
            val selectedGainDb = animatedGains[selectedIndex]
            val freqLabel =
                if (selectedFrequency >= 1000f) {
                    String.format(java.util.Locale.US, "%.1fkHz", selectedFrequency / 1000f)
                } else {
                    String.format(java.util.Locale.US, "%.0fHz", selectedFrequency)
                }
            val gainLabel = String.format(java.util.Locale.US, "%+.1f dB", selectedGainDb)
            Text(
                text = stringResource(R.string.equalizer_point_tooltip, freqLabel, gainLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "31Hz", style = MaterialTheme.typography.labelSmall)
            Text(text = "125Hz", style = MaterialTheme.typography.labelSmall)
            Text(text = "500Hz", style = MaterialTheme.typography.labelSmall)
            Text(text = "2kHz", style = MaterialTheme.typography.labelSmall)
            Text(text = "8kHz", style = MaterialTheme.typography.labelSmall)
            Text(text = "16kHz", style = MaterialTheme.typography.labelSmall)
        }
    }
}
