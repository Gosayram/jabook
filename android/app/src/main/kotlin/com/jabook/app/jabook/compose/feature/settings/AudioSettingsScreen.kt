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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.processors.EqualizerPreset
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import java.util.Locale
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun AudioSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val wsc = LocalWindowSizeClass.current
    val windowSizeClass = wsc?.let { AdaptiveUtils.resolveWindowSizeClassOrNull(it, context) } ?: wsc
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
        // TopAppBar applies statusBars insets itself; zeroed to avoid double inset under NavigationSuiteScaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
            SettingsItemWithContent(
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
                        modifier =
                            Modifier.semantics {
                                role = Role.Checkbox
                                selected =
                                    protoSettings.resumeRewindMode ==
                                    com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART
                            },
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
                        modifier =
                            Modifier.semantics {
                                role = Role.Checkbox
                                selected =
                                    protoSettings.resumeRewindMode ==
                                    com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED
                            },
                    )
                }
            }

            if (protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED) {
                SettingsItemWithContent(
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
                                modifier =
                                    Modifier.semantics {
                                        role = Role.Checkbox
                                        selected = protoSettings.resumeRewindSeconds == seconds
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
                    valueFormatter = { String.format(Locale.getDefault(), "%.2fx", it) },
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

            SettingsItemWithContent(
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
                            modifier =
                                Modifier.semantics {
                                    role = Role.Checkbox
                                    selected = kotlin.math.abs(protoSettings.holdToBoostSpeed - speed) < 0.01f
                                },
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

            SettingsSwitchItem(
                title = stringResource(R.string.headset_autoplay_title),
                subtitle = stringResource(R.string.headset_autoplay_desc),
                checked = protoSettings.headsetAutoplayEnabled,
                onCheckedChange = { viewModel.updateAudioSettings(headsetAutoplayEnabled = it) },
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

                SettingsItemWithContent(
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
                            modifier =
                                Modifier.semantics {
                                    role = Role.Checkbox
                                    selected =
                                        protoSettings.skipSilenceMode ==
                                        com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP
                                },
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
                            modifier =
                                Modifier.semantics {
                                    role = Role.Checkbox
                                    selected =
                                        protoSettings.skipSilenceMode ==
                                        com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SPEED_UP
                                },
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

            SettingsItemWithContent(
                title = stringResource(R.string.equalizer_preset_title),
                subtitle = stringResource(R.string.equalizer_preset_desc),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    EqualizerPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedEqPreset == preset,
                            onClick = { viewModel.updateEqualizerPreset(preset.name) },
                            label = {
                                Text(
                                    text =
                                        when (preset) {
                                            EqualizerPreset.FLAT -> stringResource(R.string.equalizer_preset_flat)
                                            EqualizerPreset.FLAT_RAW -> stringResource(R.string.equalizer_preset_flat_raw)
                                            EqualizerPreset.VOICE_CLARITY -> stringResource(R.string.equalizer_preset_voice_clarity)
                                            EqualizerPreset.NIGHT -> stringResource(R.string.equalizer_preset_night)
                                            EqualizerPreset.HEADPHONES -> stringResource(R.string.equalizer_preset_headphones)
                                            EqualizerPreset.CAR -> stringResource(R.string.equalizer_preset_car)
                                            EqualizerPreset.MALE_NARRATOR -> stringResource(R.string.equalizer_preset_male_narrator)
                                            EqualizerPreset.FEMALE_NARRATOR -> stringResource(R.string.equalizer_preset_female_narrator)
                                            EqualizerPreset.CAR_MODE -> stringResource(R.string.equalizer_preset_car_mode)
                                            EqualizerPreset.NIGHT_LISTENING -> stringResource(R.string.equalizer_preset_night_listening)
                                            EqualizerPreset.HEADPHONES_BUDGET -> stringResource(R.string.equalizer_preset_headphones_budget)
                                            EqualizerPreset.SPEAKER_PHONE -> stringResource(R.string.equalizer_preset_speaker_phone)
                                            EqualizerPreset.CUSTOM -> stringResource(R.string.equalizer_preset_custom)
                                        },
                                )
                            },
                            modifier =
                                Modifier.semantics {
                                    role = Role.Checkbox
                                    selected = selectedEqPreset == preset
                                },
                        )
                    }
                }
            }

            if (selectedEqPreset == EqualizerPreset.CUSTOM) {
                CustomEqBandsSliders(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                )
            } else {
                EQCurveVisualizer(
                    bands = selectedEqPreset.bandGainsMb,
                    preampDb = selectedEqPreset.effectivePreamp() / 100f,
                    modifier = Modifier.padding(horizontal = contentPadding).padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun EQCurveVisualizer(
    bands: IntArray,
    preampDb: Float,
    modifier: Modifier = Modifier,
) {
    val frequencies = remember { floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f) }
    val bandX =
        remember {
            val logs = frequencies.map { ln(it) }
            val lmin = logs.min()
            val lrange = logs.max() - lmin
            logs.map { (it - lmin) / lrange }
        }
    val freqLabels = remember { arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }
    val gridDbs = remember { floatArrayOf(-12f, -6f, 0f, 6f, 12f) }

    val targetDbs = remember(bands, preampDb) { bands.map { it / 100f + preampDb } }

    val animatedDbs =
        targetDbs.mapIndexed { i, target ->
            animateFloatAsState(targetValue = target, animationSpec = tween(400), label = "eq_band_$i").value
        }

    val accentColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    var selectedBand by rememberSaveable { mutableStateOf(-1) }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(bands) {
                    detectTapGestures { offset ->
                        val pl = 48.dp.toPx()
                        val pr = 16.dp.toPx()
                        val drawW = size.width - pl - pr
                        var nearest = -1
                        var nearestD = Float.MAX_VALUE
                        for (i in bandX.indices) {
                            val x = pl + bandX[i] * drawW
                            val d = kotlin.math.abs(offset.x - x)
                            if (d < nearestD) {
                                nearestD = d
                                nearest = i
                            }
                        }
                        selectedBand = if (nearestD < 30.dp.toPx()) nearest else -1
                    }
                },
    ) {
        val pl = 48.dp.toPx()
        val pr = 16.dp.toPx()
        val pt = 24.dp.toPx()
        val pb = 34.dp.toPx()
        val drawW = size.width - pl - pr
        val drawH = size.height - pt - pb

        for (db in gridDbs) {
            val y = pt + (1f - (db + 12f) / 24f) * drawH
            drawLine(gridColor, Offset(pl, y), Offset(size.width - pr, y), 1f)
            val label = if (db > 0) "+${db.toInt()}" else db.toInt().toString()
            val layout = textMeasurer.measure(label, axisStyle)
            drawText(layout, topLeft = Offset(pl - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
        }

        val zeroY = pt + (1f - (0f + 12f) / 24f) * drawH
        drawLine(gridColor.copy(alpha = 0.6f), Offset(pl, zeroY), Offset(size.width - pr, zeroY), 1.5f)

        for (i in animatedDbs.indices) {
            val x = pl + bandX[i] * drawW
            drawLine(gridColor, Offset(x, pt + drawH), Offset(x, pt + drawH + 4.dp.toPx()), 1f)
            val layout = textMeasurer.measure(freqLabels[i], axisStyle)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, pt + drawH + 6.dp.toPx()))
        }

        val points =
            animatedDbs.mapIndexed { i, db ->
                Offset(pl + bandX[i] * drawW, pt + (1f - (db + 12f) / 24f) * drawH)
            }

        if (points.size >= 2) {
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val cx = (points[i - 1].x + points[i].x) / 2f
                path.cubicTo(cx, points[i - 1].y, cx, points[i].y, points[i].x, points[i].y)
            }
            drawPath(path, accentColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }

        for (p in points) drawCircle(accentColor, 3.5.dp.toPx(), p)

        if (selectedBand in points.indices) {
            val pt2 = points[selectedBand]
            val freq = frequencies[selectedBand].toInt()
            val db = animatedDbs[selectedBand]
            val tooltip = "${if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"}\n${String.format(Locale.US, "%.1f dB", db)}"
            val tl = textMeasurer.measure(tooltip, TextStyle(color = Color.White, fontSize = 10.sp))
            val tw = tl.size.width + 12.dp.toPx()
            val th = tl.size.height + 8.dp.toPx()
            val tx = (pt2.x - tw / 2f).coerceIn(4.dp.toPx(), size.width - tw - 4.dp.toPx())
            val ty = (pt2.y - th - 10.dp.toPx()).coerceAtLeast(4.dp.toPx())
            drawRoundRect(accentColor, Offset(tx, ty), Size(tw, th), CornerRadius(6.dp.toPx()))
            drawText(tl, topLeft = Offset(tx + 6.dp.toPx(), ty + 4.dp.toPx()))
            drawCircle(accentColor, 6.dp.toPx(), pt2)
            drawCircle(Color.White, 2.5.dp.toPx(), pt2)
        }
    }
}

@Composable
private fun CustomEqBandsSliders(
    viewModel: SettingsViewModel,
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
) {
    val customBands by viewModel.customEqBands.collectAsStateWithLifecycle()
    val bandFrequencies = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }
    var showSaveDialog by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.equalizer_custom_bands_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = contentPadding),
    )

    for ((index, freqLabel) in bandFrequencies.withIndex()) {
        CustomEqSliderRow(
            index = index,
            label = freqLabel,
            bands = customBands,
            onBandChanged = { newMb ->
                val updated =
                    customBands.toMutableList().apply {
                        if (index < size) set(index, newMb) else add(index, newMb)
                    }
                viewModel.updateCustomEqBands(updated)
                viewModel.updateEqualizerPreset(EqualizerPreset.CUSTOM.name)
            },
            contentPadding = contentPadding,
        )
    }

    Spacer(modifier = Modifier.size(itemSpacing))

    Button(
        onClick = { showSaveDialog = true },
        modifier = Modifier.padding(horizontal = contentPadding),
    ) {
        Text(stringResource(R.string.equalizer_save_preset))
    }

    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.equalizer_save_preset_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.equalizer_save_preset_dialog_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            val bands = customBands.take(10).ifEmpty { List(10) { 0 } }
                            val maxPositive = bands.maxOrNull() ?: 0
                            val preamp = if (maxPositive > 0) -maxPositive else 0
                            viewModel.saveEqPreset(presetName, bands, preamp)
                            showSaveDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.saveButtonText))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun CustomEqSliderRow(
    index: Int,
    label: String,
    bands: List<Int>,
    onBandChanged: (Int) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp,
) {
    val currentDb =
        remember(bands, index) {
            mutableFloatStateOf((bands.getOrNull(index) ?: 0) / 100f)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label Hz",
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = currentDb.floatValue,
            onValueChange = { newValue ->
                val rounded =
                    (kotlin.math.round(newValue / 0.5f) * 0.5f)
                        .coerceIn(-12f, 12f)
                currentDb.floatValue = rounded
            },
            onValueChangeFinished = {
                val mb = (currentDb.floatValue * 100).toInt()
                onBandChanged(mb)
            },
            valueRange = -12f..12f,
            steps = 23,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = String.format(Locale.getDefault(), "%.1f dB", currentDb.floatValue),
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
