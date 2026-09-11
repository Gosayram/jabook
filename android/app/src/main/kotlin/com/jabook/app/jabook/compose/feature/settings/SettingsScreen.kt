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

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.constants.PlaybackSpeedConstants
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.core.util.UiFormatters
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.data.network.MirrorHealth
import com.jabook.app.jabook.compose.data.permissions.PersistedTreeUriPermissionGuard
import com.jabook.app.jabook.compose.designsystem.component.ConfirmDialog
import com.jabook.app.jabook.compose.designsystem.component.endItemShape
import com.jabook.app.jabook.compose.designsystem.component.leadingItemShape
import com.jabook.app.jabook.compose.designsystem.component.middleItemShape
import com.jabook.app.jabook.compose.feature.library.ListeningHeatmap
import com.jabook.app.jabook.compose.feature.library.shareYearRecap
import kotlinx.coroutines.launch
import java.util.Locale

private object GitHubUrls {
    public const val REPOSITORY = "https://github.com/Gosayram/jabook"
    public const val LICENSE = "$REPOSITORY/blob/main/LICENSE"
    public const val CHANGELOG = "$REPOSITORY/blob/main/CHANGELOG.md"
    public const val APACHE_LICENSE = "https://www.apache.org/licenses/LICENSE-2.0"

    public fun releaseTag(version: String): String {
        val cleanVersion = version.replace("-dev", "").replace("-beta", "").replace("-prod", "")
        return "$REPOSITORY/releases/tag/$cleanVersion"
    }
}

/**
 * Settings screen for app configuration.
 *
 * M3-compliant section order:
 * 1. Profile
 * 2. Account
 * 3. Appearance (theme, colors, language, font, player cover)
 * 4. Playback (speed, auto-play, seek, resume rewind, crossfade, skip silence)
 * 5. Audio Processing (equalizer)
 * 6. Library (scan, folders, statistics)
 * 7. Downloads
 * 8. Data Management (backup, cache, indexing)
 * 9. Network (mirrors)
 * 10. About
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun SettingsScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    onNavigateToScanSettings: () -> Unit = {},
    onNavigateToAudioSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    indexingViewModel: com.jabook.app.jabook.compose.feature.indexing.IndexingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val persistedTreePermissionGuard =
        remember(context) {
            PersistedTreeUriPermissionGuard(
                takePermission = { uri ->
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                },
                releasePermission = {},
                isPermissionPersisted = { uri ->
                    context.contentResolver.persistedUriPermissions.any {
                        it.uri == uri && it.isReadPermission && it.isWritePermission
                    }
                },
            )
        }
    val notificationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestPermission(),
        ) { isGranted ->
            if (!isGranted) {
                android.widget.Toast
                    .makeText(
                        context,
                        context.getString(R.string.notificationPermissionDeniedToast),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }
        }
    val wsc = LocalWindowSizeClass.current
    val windowSizeClass = wsc?.let { AdaptiveUtils.resolveWindowSizeClassOrNull(it, context) } ?: wsc
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)
    val smallSpacing = AdaptiveUtils.getSmallSpacingOrDefault(windowSizeClass)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val weeklyRecap by viewModel.weeklyRecapState.collectAsStateWithLifecycle()
    val yearRecap by viewModel.yearRecapState.collectAsStateWithLifecycle()
    val dailyListeningMinutes by viewModel.dailyListeningMinutes.collectAsStateWithLifecycle()
    var showStatsExpanded by remember { mutableStateOf(false) }

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateToAuth = dropUnlessResumed { navigationClickGuard.run(onNavigateToAuth) }
    val safeNavigateToDebug = dropUnlessResumed { navigationClickGuard.run(onNavigateToDebug) }
    val safeNavigateToScanSettings = dropUnlessResumed { navigationClickGuard.run(onNavigateToScanSettings) }
    val safeNavigateToAudioSettings = dropUnlessResumed { navigationClickGuard.run(onNavigateToAudioSettings) }
    val safeNavigateToDownloads = dropUnlessResumed { navigationClickGuard.run(onNavigateToDownloads) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navSettingsText)) },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // ─── 1. Profile ────────────────────────────────────────────────
            ProfileHeader(
                authStatus = viewModel.authStatus.collectAsStateWithLifecycle().value,
                onSignIn = { safeNavigateToAuth() },
                contentPadding = contentPadding,
            )

            HorizontalDivider()

            // ─── 2. Account ────────────────────────────────────────────────
            val authStatus by viewModel.authStatus.collectAsStateWithLifecycle()
            SettingsSection(
                title = stringResource(R.string.account),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            when (val status = authStatus) {
                is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated -> {
                    SettingsItem(
                        title = stringResource(R.string.loggedInAs, status.username),
                        subtitle = stringResource(R.string.tapToLogout),
                        onClick = { viewModel.logout() },
                    )
                }
                else -> {
                    SettingsItem(
                        title = stringResource(R.string.loginToRutracker),
                        subtitle = stringResource(R.string.requiredToDownloadTorrents),
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { safeNavigateToAuth() },
                    )
                }
            }

            HorizontalDivider()

            // ─── 3. Appearance ─────────────────────────────────────────────
            val protoSettings by viewModel.protoSettings.collectAsStateWithLifecycle()

            SettingsSection(
                title = stringResource(R.string.appearance),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // Language (consolidated from General + Appearance)
            var selectedLang by remember(userPreferences?.languageCode) { mutableStateOf(userPreferences?.languageCode ?: "ru") }
            StackedSegmentedControl(
                label = stringResource(R.string.settingsLanguage),
                options = listOf("Русский" to "ru", "English" to "en"),
                selectedValue = selectedLang,
                onSelect = { value: String ->
                    selectedLang = value
                    viewModel.updateLanguage(value)
                },
                contentPadding = contentPadding,
            )

            SettingsItemWithContent(
                title = stringResource(R.string.themeTitle),
                subtitle = stringResource(R.string.chooseAppTheme),
            ) {
                ThemeSelector(
                    selectedTheme = userPreferences?.theme ?: AppTheme.SYSTEM,
                    onThemeSelected = { theme -> viewModel.updateTheme(theme) },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsSwitchItem(
                    title = stringResource(R.string.dynamicColorsTitle),
                    subtitle = stringResource(R.string.dynamicColorsDescription),
                    checked = protoSettings.useDynamicColors,
                    onCheckedChange = viewModel::updateDynamicColors,
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            SettingsItemWithContent(
                title = stringResource(R.string.accentColorTitle),
                subtitle = stringResource(R.string.accentColorDescription),
            ) {
                AccentSwatchSelector(
                    selectedIndex = protoSettings.accentSwatchIndex,
                    onSwatchSelected = { viewModel.updateAccentSwatchIndex(it) },
                )
            }

            SettingsItemWithContent(
                title = stringResource(R.string.fontTitle),
                subtitle = stringResource(R.string.chooseFontFamily),
            ) {
                FontSelector(
                    selectedFont = userPreferences?.font ?: com.jabook.app.jabook.compose.data.model.AppFont.DEFAULT,
                    onFontSelected = { font -> viewModel.updateFont(font) },
                )
            }

            // Player cover mode (moved from Device and Layout)
            SettingsItemWithContent(
                title = stringResource(R.string.playerCoverModeTitle),
                subtitle = stringResource(R.string.playerCoverModeDescription),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = protoSettings.playerCoverMode == 0,
                        onClick = { viewModel.updatePlayerCoverMode(0) },
                        label = { Text(stringResource(R.string.coverModeCard)) },
                        modifier =
                            Modifier.semantics {
                                role = Role.RadioButton
                                selected = protoSettings.playerCoverMode == 0
                            },
                    )
                    FilterChip(
                        selected = protoSettings.playerCoverMode == 1,
                        onClick = { viewModel.updatePlayerCoverMode(1) },
                        label = { Text(stringResource(R.string.coverModeVinyl)) },
                        modifier =
                            Modifier.semantics {
                                role = Role.RadioButton
                                selected = protoSettings.playerCoverMode == 1
                            },
                    )
                }
            }

            HorizontalDivider()

            // ─── 4. Playback ───────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.playback),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsSwitchItem(
                title = stringResource(R.string.autoplayNextChapter),
                subtitle = stringResource(R.string.automaticallyPlayNextChapterWhenCurrentEnds),
                checked = userPreferences?.autoPlayNext ?: true,
                onCheckedChange = viewModel::updateAutoPlayNext,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSliderItem(
                title = stringResource(R.string.playbackSpeed),
                sliderValue = userPreferences?.playbackSpeed ?: 1.0f,
                onValueChange = { viewModel.updatePlaybackSpeed(it) },
                valueRange = PlaybackSpeedConstants.MIN_SPEED..PlaybackSpeedConstants.MAX_SPEED,
                steps = PlaybackSpeedConstants.SLIDER_STEPS,
                valueFormatter = { PlaybackSpeedConstants.formatSpeed(it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSliderItem(
                title = stringResource(R.string.rewindDurationTitle),
                sliderValue = protoSettings.rewindDurationSeconds.toFloat(),
                onValueChange = { viewModel.updateAudioSettings(rewindSeconds = it.toInt()) },
                valueRange = 5f..60f,
                steps = 10,
                valueFormatter = { "${it.toInt()}s" },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSliderItem(
                title = stringResource(R.string.forwardDurationTitle),
                sliderValue = protoSettings.forwardDurationSeconds.toFloat(),
                onValueChange = { viewModel.updateAudioSettings(forwardSeconds = it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
                valueFormatter = { "${it.toInt()}s" },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            // Resume rewind (inlined from AudioSettingsScreen)
            SettingsItemWithContent(
                title = stringResource(R.string.resume_rewind_title),
                subtitle = stringResource(R.string.resume_rewind_desc),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART,
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
                                    protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.SMART
                            },
                    )
                    FilterChip(
                        selected = protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED,
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
                                    protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED
                            },
                    )
                }
            }

            if (protoSettings.resumeRewindMode == com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode.FIXED) {
                SettingsItemWithContent(
                    title = stringResource(R.string.resume_rewind_fixed_title),
                    subtitle = stringResource(R.string.resume_rewind_fixed_desc),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 10, 30).forEach { seconds ->
                            FilterChip(
                                selected = protoSettings.resumeRewindSeconds == seconds,
                                onClick = { viewModel.updateAudioSettings(resumeRewindSeconds = seconds) },
                                label = { Text(stringResource(R.string.resume_rewind_option_seconds, seconds)) },
                                modifier =
                                    Modifier.semantics {
                                        role = Role.Checkbox
                                        selected =
                                            protoSettings.resumeRewindSeconds == seconds
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
                    onValueChange = { viewModel.updateAudioSettings(resumeRewindAggressiveness = it) },
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
                onCheckedChange = { viewModel.updateAudioSettings(sleepTimerShakeExtendEnabled = it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSwitchItem(
                title = stringResource(R.string.auto_sleep_timer_title),
                subtitle = stringResource(R.string.auto_sleep_timer_desc),
                checked = protoSettings.autoSleepTimerEnabled,
                onCheckedChange = { viewModel.updateAudioSettings(autoSleepTimerEnabled = it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            if (protoSettings.autoSleepTimerEnabled) {
                SettingsItemWithContent(
                    title = stringResource(R.string.auto_sleep_timer_duration),
                    subtitle = stringResource(R.string.auto_sleep_timer_desc),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90).forEach { minutes ->
                            FilterChip(
                                selected = protoSettings.autoSleepTimerMinutes == minutes,
                                onClick = { viewModel.updateAudioSettings(autoSleepTimerMinutes = minutes) },
                                label = {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.durationMinutesFull,
                                            minutes,
                                            minutes,
                                        ),
                                    )
                                },
                                modifier =
                                    Modifier.semantics {
                                        role = Role.Checkbox
                                        selected =
                                            protoSettings.autoSleepTimerMinutes == minutes
                                    },
                            )
                        }
                    }
                }
            }

            SettingsItemWithContent(
                title = stringResource(R.string.hold_to_boost_speed_title),
                subtitle = stringResource(R.string.hold_to_boost_speed_desc),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2.0f, 2.5f, 3.0f).forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(protoSettings.holdToBoostSpeed - speed) < 0.01f,
                            onClick = { viewModel.updateAudioSettings(holdToBoostSpeed = speed) },
                            label = { Text(stringResource(R.string.playback_speed_format, speed)) },
                            modifier =
                                Modifier.semantics {
                                    role = Role.Checkbox
                                    selected =
                                        kotlin.math.abs(protoSettings.holdToBoostSpeed - speed) < 0.01f
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

            SettingsSwitchItem(
                title = stringResource(R.string.notification_lockscreen_title),
                subtitle = stringResource(R.string.notification_lockscreen_desc),
                checked = !protoSettings.notificationLockscreenPrivate,
                onCheckedChange = { viewModel.updateAudioSettings(notificationLockscreenPrivate = !it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
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
                    steps = 8,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = protoSettings.skipSilenceMode == com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP,
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
                                        protoSettings.skipSilenceMode == com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP
                                },
                        )
                        FilterChip(
                            selected =
                                protoSettings.skipSilenceMode == com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SPEED_UP,
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

            // Reset Global Book Settings
            var showResetBookSettingsDialog by remember { mutableStateOf(false) }

            SettingsItem(
                title = stringResource(R.string.resetAllBookSettings),
                subtitle =
                    stringResource(R.string.resetAllBookSettingsConfirmation)
                        .lineSequence()
                        .firstOrNull()
                        ?.trim()
                        .orEmpty(),
                onClick = { showResetBookSettingsDialog = true },
            )

            if (showResetBookSettingsDialog) {
                ConfirmDialog(
                    title = stringResource(R.string.resetAllBookSettings),
                    text = stringResource(R.string.resetAllBookSettingsConfirmation),
                    confirmLabel = stringResource(R.string.resetButton),
                    onConfirm = {
                        viewModel.resetAllBookSettings()
                        showResetBookSettingsDialog = false
                    },
                    onDismiss = { showResetBookSettingsDialog = false },
                )
            }

            HorizontalDivider()

            // ─── 5. Audio Processing ───────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.audioTitle),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.audioSettingsTitle),
                subtitle = stringResource(R.string.audioDescription),
                onClick = { safeNavigateToAudioSettings() },
            )

            HorizontalDivider()

            // ─── 6. Library ────────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.library),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // ponytail: connected shapes demo (Grit ListItemExt: leading 16/4, middle 4, end 4/16)
            val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier.padding(horizontal = contentPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Surface(shape = leadingItemShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    SettingsItemWithContent(
                        title = stringResource(R.string.scan_library),
                        subtitle =
                            when (val p = scanProgress) {
                                is ScanProgress.Idle -> stringResource(R.string.tap_to_scan_now)
                                is ScanProgress.Discovery -> stringResource(R.string.scan_status_discovery, p.fileCount)
                                is ScanProgress.Parsing -> stringResource(R.string.scan_status_parsing, p.currentBook, p.progress, p.total)
                                is ScanProgress.Saving -> stringResource(R.string.scan_status_saving)
                                is ScanProgress.Completed ->
                                    pluralStringResource(
                                        R.plurals.scan_status_complete_plural,
                                        p.booksAdded,
                                        p.booksAdded,
                                    )
                                is ScanProgress.Error -> stringResource(R.string.scan_status_error, p.message)
                            },
                        onClick =
                            if (scanProgress is ScanProgress.Idle ||
                                scanProgress is ScanProgress.Completed ||
                                scanProgress is ScanProgress.Error
                            ) {
                                { viewModel.scanLibrary() }
                            } else {
                                null
                            },
                    ) {
                        if (scanProgress is ScanProgress.Discovery ||
                            scanProgress is ScanProgress.Parsing ||
                            scanProgress is ScanProgress.Saving
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { viewModel.cancelScan() },
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(shape = middleItemShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    SettingsItem(
                        title = stringResource(R.string.libraryFoldersTitle),
                        subtitle = stringResource(R.string.manageFoldersToScanForAudiobooks),
                        onClick = { safeNavigateToScanSettings() },
                    )
                }
                Surface(shape = endItemShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.normalizeChapterTitles),
                        subtitle = stringResource(R.string.normalizeChapterTitlesDesc),
                        checked = userPreferences?.normalizeChapterTitles ?: false,
                        onCheckedChange = { viewModel.updateNormalizeChapterTitles(it) },
                        contentPadding = contentPadding,
                        itemSpacing = itemSpacing,
                        smallSpacing = smallSpacing,
                    )
                }
            }

            // Statistics (moved into Library)
            weeklyRecap?.let { recap ->
                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.statistics),
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                )

                WeeklyRecapCard(
                    stats = recap,
                    modifier = Modifier.padding(horizontal = contentPadding, vertical = 6.dp),
                )
                yearRecap?.let { recapYear ->
                    YearRecapPromptCard(
                        yearRecap = recapYear,
                        onShareClick = { shareYearRecap(context, recapYear) },
                        modifier = Modifier.padding(horizontal = contentPadding, vertical = 6.dp),
                    )
                }
                if (showStatsExpanded) {
                    ListeningHeatmap(
                        data = dailyListeningMinutes,
                        modifier = Modifier.padding(horizontal = contentPadding, vertical = 6.dp),
                    )
                }
                TextButton(
                    onClick = { showStatsExpanded = !showStatsExpanded },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding),
                ) {
                    Icon(
                        imageVector = if (showStatsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text =
                            stringResource(
                                if (showStatsExpanded) R.string.hideStatistics else R.string.showStatistics,
                            ),
                    )
                }
            }

            HorizontalDivider()

            // ─── 7. Downloads ──────────────────────────────────────────────
            // Active Downloads Card
            val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()

            if (activeDownloads.isNotEmpty()) {
                val totalSpeed = activeDownloads.sumOf { it.downloadSpeed }
                val downloadCount =
                    activeDownloads.count {
                        it.state == com.jabook.app.jabook.compose.data.torrent.TorrentState.DOWNLOADING
                    }

                SettingsItemWithContent(
                    title = stringResource(R.string.active_downloads),
                    subtitle =
                        if (downloadCount > 0) {
                            stringResource(
                                R.string.downloading_count_speed,
                                downloadCount,
                                UiFormatters.formatSpeedBytes(totalSpeed.toLong()),
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.downloads_active_plural,
                                activeDownloads.size,
                                activeDownloads.size,
                            )
                        },
                    onClick = { safeNavigateToDownloads() },
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    )
                }

                HorizontalDivider()
            }

            SettingsSection(
                title = stringResource(R.string.downloads),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            val folderLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                        androidx.activity.result.contract.ActivityResultContracts
                            .OpenDocumentTree(),
                ) { uri ->
                    uri?.takeIf(persistedTreePermissionGuard::take)?.let {
                        viewModel.updateDownloadPath(it.toString())
                    }
                }

            SettingsItem(
                title = stringResource(R.string.downloadLocationTitle),
                subtitle =
                    if (protoSettings.downloadPath.isNotEmpty()) {
                        android.net.Uri
                            .parse(protoSettings.downloadPath)
                            .path ?: protoSettings.downloadPath
                    } else {
                        stringResource(R.string.internalAppStorageDefault)
                    },
                onClick = { folderLauncher.launch(null) },
            )

            SettingsSwitchItem(
                title = stringResource(R.string.wifiOnly),
                subtitle = stringResource(R.string.downloadOnlyViaWifi),
                checked = protoSettings.wifiOnlyDownload,
                onCheckedChange = { enabled -> viewModel.updateWifiOnly(enabled) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSwitchItem(
                title = stringResource(R.string.autoLoadCoversOnCellular),
                subtitle = stringResource(R.string.autoLoadCoversOnCellularDesc),
                checked = protoSettings.autoLoadCoversOnCellular,
                onCheckedChange = viewModel::updateAutoLoadCoversOnCellular,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSwitchItem(
                title = stringResource(R.string.limitDownloadSpeed),
                subtitle = stringResource(R.string.setMaximumDownloadSpeed),
                checked = protoSettings.limitDownloadSpeed,
                onCheckedChange = viewModel::updateLimitDownloadSpeed,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            if (protoSettings.limitDownloadSpeed) {
                SettingsSliderItem(
                    title = stringResource(R.string.maxSpeed),
                    sliderValue = protoSettings.maxDownloadSpeedKb.toFloat(),
                    onValueChange = { viewModel.updateMaxDownloadSpeed(it.toInt()) },
                    valueRange = 100f..10000f,
                    steps = 98,
                    valueFormatter = { "${it.toInt()} KB/s" },
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            SettingsSliderItem(
                title = stringResource(R.string.concurrentDownloads),
                sliderValue = protoSettings.maxConcurrentDownloads.toFloat(),
                onValueChange = { viewModel.updateMaxConcurrentDownloads(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                valueFormatter = { "${it.toInt()}" },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            Spacer(modifier = Modifier.height(itemSpacing))

            val torrentStorageSize by viewModel.torrentStorageSize.collectAsStateWithLifecycle()
            LaunchedEffect(protoSettings.downloadPath) {
                viewModel.loadTorrentStorageSize()
            }

            SettingsItem(
                title = stringResource(R.string.downloadsStorage),
                subtitle = stringResource(R.string.storageUsedFormat, UiFormatters.formatFileSize(torrentStorageSize)),
            )

            var showDeleteAllDialog by remember { mutableStateOf(false) }

            SettingsItem(
                title = stringResource(R.string.deleteAllDownloads),
                subtitle = stringResource(R.string.deleteAllDownloadsDesc),
                onClick = { showDeleteAllDialog = true },
            )

            if (showDeleteAllDialog) {
                ConfirmDialog(
                    title = stringResource(R.string.deleteAllDownloads),
                    text = stringResource(R.string.deleteAllConfirmation),
                    confirmLabel = stringResource(R.string.deleteButton),
                    onConfirm = {
                        viewModel.deleteAllTorrents(true)
                        showDeleteAllDialog = false
                    },
                    onDismiss = { showDeleteAllDialog = false },
                )
            }

            HorizontalDivider()

            // ─── 8. Data Management ────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.dataManagement),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // Backup & Restore
            val backupState by viewModel.backupState.collectAsStateWithLifecycle()

            var showImportConfirmation by remember { mutableStateOf(false) }
            var selectedBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }

            val importFilePicker =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                        androidx.activity.result.contract.ActivityResultContracts
                            .GetContent(),
                ) { uri: android.net.Uri? ->
                    uri?.let {
                        showImportConfirmation = true
                        selectedBackupUri = it
                    }
                }

            SettingsItem(
                title = stringResource(R.string.exportDataButton),
                subtitle = stringResource(R.string.saveSettingsAndLibraryToBackupFile),
                onClick = { viewModel.exportData() },
            )

            SettingsItem(
                title = stringResource(R.string.importDataButton),
                subtitle = stringResource(R.string.restoreSettingsAndLibraryFromBackup),
                onClick = {
                    importFilePicker.launch("application/json")
                },
            )

            if (showImportConfirmation) {
                ConfirmDialog(
                    title = stringResource(R.string.importBackup),
                    text = stringResource(R.string.thisWillReplaceYourCurrentSettingsAreYouSureYouWan),
                    confirmLabel = stringResource(R.string.importButton),
                    onConfirm = {
                        selectedBackupUri?.let { viewModel.importData(it) }
                        showImportConfirmation = false
                    },
                    onDismiss = { showImportConfirmation = false },
                )
            }

            LaunchedEffect(backupState) {
                when (val state = backupState) {
                    is BackupUiState.ExportReady -> {
                        val intent =
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(android.content.Intent.EXTRA_STREAM, state.uri)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.jabookBackup))
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    context.getString(R.string.backupOfJabookSettingsAndData),
                                )
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, context.getString(R.string.exportBackup)),
                        )
                        viewModel.resetBackupState()
                    }
                    is BackupUiState.ImportComplete -> {
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.importSuccessfulStats),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        viewModel.resetBackupState()
                    }
                    is BackupUiState.Error -> {
                        android.widget.Toast
                            .makeText(context, state.message, android.widget.Toast.LENGTH_LONG)
                            .show()
                        viewModel.resetBackupState()
                    }
                    else -> {}
                }
            }

            // Cache Management
            val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
            val cacheOperation by viewModel.cacheOperation.collectAsStateWithLifecycle()

            var showClearCacheDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                viewModel.loadCacheStatistics()
            }

            SettingsItem(
                title = stringResource(R.string.totalCacheSize),
                subtitle =
                    cacheStats?.let { UiFormatters.formatFileSize(it.totalSize) }
                        ?: if (cacheOperation is CacheOperationState.Loading) {
                            stringResource(R.string.calculating)
                        } else {
                            stringResource(R.string.unknown)
                        },
            )

            SettingsItem(
                title = stringResource(R.string.lastCleanup),
                subtitle =
                    cacheStats?.let {
                        if (it.lastCleanup > 0) {
                            formatTimestamp(it.lastCleanup)
                        } else {
                            stringResource(R.string.neverDate)
                        }
                    } ?: "-",
            )

            SettingsItem(
                title = stringResource(R.string.clearAllCacheButton),
                subtitle =
                    cacheStats?.let {
                        stringResource(
                            R.string.freeUpCacheSize,
                            UiFormatters.formatFileSize(it.totalSize),
                        )
                    } ?: "",
                onClick = {
                    if (cacheOperation != CacheOperationState.Clearing) {
                        showClearCacheDialog = true
                    }
                },
            )

            if (showClearCacheDialog) {
                ConfirmDialog(
                    title = stringResource(R.string.clearCache),
                    text =
                        stringResource(
                            R.string.clearCacheConfirmation,
                            cacheStats?.let { UiFormatters.formatFileSize(it.totalSize) } ?: stringResource(R.string.unknown),
                        ),
                    confirmLabel = stringResource(R.string.clearButton),
                    onConfirm = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    },
                    onDismiss = { showClearCacheDialog = false },
                )
            }

            LaunchedEffect(cacheOperation) {
                when (cacheOperation) {
                    is CacheOperationState.Success -> {
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.cacheClearedSuccessMessage),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        viewModel.resetCacheOperation()
                    }
                    is CacheOperationState.Error -> {
                        val error = (cacheOperation as CacheOperationState.Error).message
                        android.widget.Toast
                            .makeText(context, error, android.widget.Toast.LENGTH_LONG)
                            .show()
                        viewModel.resetCacheOperation()
                    }
                    else -> {}
                }
            }

            // Indexing
            val indexingProgress by indexingViewModel.indexingProgress.collectAsStateWithLifecycle()
            val isIndexing by indexingViewModel.isIndexing.collectAsStateWithLifecycle()
            val indexingStartTime by indexingViewModel.indexingStartTime.collectAsStateWithLifecycle()
            val clearingInProgress by indexingViewModel.clearingInProgress.collectAsStateWithLifecycle()
            val forumStatuses by indexingViewModel.forumStatuses.collectAsStateWithLifecycle()

            var showIndexingDialog by remember { mutableStateOf(false) }
            var indexSize by remember { mutableStateOf(0) }
            var indexMetadata by remember {
                mutableStateOf<com.jabook.app.jabook.compose.data.local.dao.IndexMetadata?>(
                    null,
                )
            }
            var elapsedTimeStr by remember { mutableStateOf("") }

            LaunchedEffect(isIndexing, indexingStartTime) {
                if (isIndexing && indexingStartTime != null) {
                    val start = indexingStartTime ?: return@LaunchedEffect
                    while (true) {
                        val duration = System.currentTimeMillis() - start
                        elapsedTimeStr = UiFormatters.formatDuration(duration)
                        kotlinx.coroutines.delay(1000L)
                    }
                } else {
                    elapsedTimeStr = ""
                }
            }

            LaunchedEffect(Unit) {
                indexSize = indexingViewModel.getIndexSize()
                indexMetadata = indexingViewModel.getIndexMetadata()
            }

            LaunchedEffect(indexingProgress) {
                if (indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed) {
                    indexSize = indexingViewModel.getIndexSize()
                    indexMetadata = indexingViewModel.getIndexMetadata()
                }
            }

            val indexTopicsCount = pluralStringResource(R.plurals.indexTopicsCount, indexSize, indexSize)

            // Forum selection for indexing (ponytail: direct multi-select with a two-forum preset)
            val allForumIds = com.jabook.app.jabook.compose.data.remote.api.RutrackerApi.AUDIOBOOKS_FORUM_IDS
            val quickPreset = "574,1036" // ponytail: popular child forums
            var selectedForums by rememberSaveable { mutableStateOf(protoSettings.selectedForumIds) }
            var forumSelectorExpanded by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(protoSettings.selectedForumIds) {
                selectedForums = protoSettings.selectedForumIds
            }

            val effectiveForums = selectedForums.ifBlank { allForumIds }
            val forumCount = effectiveForums.split(",").size

            SettingsItem(
                title = stringResource(R.string.forumsToIndex),
                subtitle =
                    if (selectedForums.isBlank()) {
                        stringResource(R.string.allForumsCount, forumCount)
                    } else {
                        stringResource(R.string.selectedForumsCount, forumCount)
                    },
                onClick = { forumSelectorExpanded = !forumSelectorExpanded },
            )

            if (forumSelectorExpanded) {
                Column(modifier = Modifier.padding(horizontal = contentPadding, vertical = 4.dp)) {
                    // Preset buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedForums.isBlank(),
                            onClick = {
                                selectedForums = ""
                                viewModel.updateSelectedForumIds("")
                            },
                            label = { Text(stringResource(R.string.all)) },
                        )
                        FilterChip(
                            selected = selectedForums == quickPreset,
                            onClick = {
                                selectedForums = quickPreset
                                viewModel.updateSelectedForumIds(quickPreset)
                            },
                            label = { Text(stringResource(R.string.quickForumsCount, 2)) },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Forum chips
                    val chips = allForumIds.split(",").map { it.trim() }
                    val selectedSet =
                        remember(selectedForums) {
                            selectedForums
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()
                        }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        chips.forEach { forumId ->
                            val isSel = forumId in selectedSet || (selectedForums.isBlank())
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    val newSet =
                                        if (selectedForums.isBlank()) {
                                            // Selecting from "all" → only this one
                                            setOf(forumId)
                                        } else if (forumId in selectedSet) {
                                            selectedSet - forumId
                                        } else {
                                            selectedSet + forumId
                                        }
                                    val newIds = chips.filter { it in newSet }.joinToString(",")
                                    selectedForums = newIds
                                    viewModel.updateSelectedForumIds(newIds)
                                },
                                label = { Text(stringResource(R.string.forumId, forumId)) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.affectsOfflineIndexingOnly),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsItem(
                title =
                    if (indexSize == 0) {
                        stringResource(R.string.indexNotCreatedTitle)
                    } else {
                        stringResource(R.string.indexStatusWithTopics, indexTopicsCount)
                    },
                subtitle =
                    if (indexSize == 0) {
                        stringResource(R.string.indexTapToCreate)
                    } else {
                        stringResource(R.string.indexTapToUpdate)
                    },
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    showIndexingDialog = true
                    indexingViewModel.startIndexing(context)
                },
            )

            if (isIndexing || indexSize > 0 || clearingInProgress) {
                val statusOnlyProgress =
                    (indexingProgress as? com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress)
                        ?.detail
                        ?.takeUnless { it.hasDetailedProgress }
                SettingsItemWithContent(
                    title =
                        if (clearingInProgress) {
                            stringResource(R.string.indexClearingTitle)
                        } else {
                            stringResource(R.string.indexingStatusTitle)
                        },
                    subtitle =
                        when {
                            clearingInProgress -> stringResource(R.string.pleaseWait)
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress -> {
                                val progress = indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress
                                val timeText = if (elapsedTimeStr.isNotEmpty()) " • $elapsedTimeStr" else ""
                                if (progress.detail.hasDetailedProgress) {
                                    stringResource(
                                        R.string.indexingCompactStatus,
                                        progress.detail.currentForumName,
                                        progress.detail.totalForumsCompleted + 1,
                                        progress.detail.totalForums,
                                        timeText,
                                    )
                                } else {
                                    progress.detail.currentForumName
                                        .ifBlank { stringResource(R.string.indexingPreparing) }
                                }
                            }
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed -> {
                                val completed =
                                    indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed
                                val displayCount =
                                    if (indexSize > 0) {
                                        indexSize
                                    } else {
                                        completed.totalTopics
                                    }
                                val durationMs =
                                    (indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed)
                                        .durationMs
                                val durationText =
                                    if (durationMs > 0) {
                                        stringResource(R.string.indexDurationSeconds, durationMs / 1000)
                                    } else {
                                        ""
                                    }
                                val completedTopicsCount =
                                    pluralStringResource(
                                        R.plurals.indexTopicsCount,
                                        displayCount,
                                        displayCount,
                                    )
                                stringResource(R.string.indexCompletedCompactStatus, completedTopicsCount, durationText)
                            }
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error ->
                                stringResource(
                                    R.string.errorWithMessage,
                                    (indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error).message,
                                )
                            else -> stringResource(R.string.indexReadyToStart)
                        },
                    subtitleModifier =
                        if (statusOnlyProgress != null) {
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        } else {
                            Modifier
                        },
                ) {
                    if (isIndexing &&
                        indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress
                    ) {
                        val progress = indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (progress.detail.hasDetailedProgress) {
                                val progressValue = progress.detail.percentComplete
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { progressValue },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                                val indexedTopics =
                                    pluralStringResource(
                                        R.plurals.indexTopicsCount,
                                        progress.detail.topicsFound,
                                        progress.detail.topicsFound,
                                    )
                                Text(
                                    text =
                                        stringResource(
                                            R.string.indexProgressWithTopics,
                                            (progressValue * 100).toInt(),
                                            indexedTopics,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            } else {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                            }
                        }
                    } else if (clearingInProgress) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )
                    }
                }

                if (indexSize > 0 && !isIndexing && !clearingInProgress) {
                    var showClearConfirmDialog by remember { mutableStateOf(false) }

                    SettingsItem(
                        title = stringResource(R.string.resetIndexTitle),
                        subtitle = stringResource(R.string.resetIndexSubtitle, indexTopicsCount),
                        onClick = {
                            showClearConfirmDialog = true
                        },
                    )

                    if (showClearConfirmDialog) {
                        ConfirmDialog(
                            title = stringResource(R.string.resetIndexDialogTitle),
                            text = stringResource(R.string.resetIndexDialogMessage, indexTopicsCount),
                            confirmLabel = stringResource(R.string.reset),
                            onConfirm = {
                                showClearConfirmDialog = false
                                coroutineScope.launch {
                                    val success = indexingViewModel.clearIndex()
                                    if (success) {
                                        indexSize = indexingViewModel.getIndexSize()
                                        indexMetadata = indexingViewModel.getIndexMetadata()
                                    }
                                }
                            },
                            onDismiss = { showClearConfirmDialog = false },
                        )
                    }
                }

                indexMetadata?.let { metadata ->
                    if (indexSize > 0) {
                        val oldestDate =
                            metadata.oldest?.let { timestamp ->
                                java.time.Instant
                                    .ofEpochMilli(timestamp)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(
                                        java.time.format.DateTimeFormatter
                                            .ofPattern("dd.MM.yyyy"),
                                    )
                            } ?: stringResource(R.string.unknown)
                        val newestDate =
                            metadata.newest?.let { timestamp ->
                                java.time.Instant
                                    .ofEpochMilli(timestamp)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(
                                        java.time.format.DateTimeFormatter
                                            .ofPattern("dd.MM.yyyy"),
                                    )
                            } ?: stringResource(R.string.unknown)

                        SettingsItem(
                            title = stringResource(R.string.indexCheckTitle),
                            subtitle = stringResource(R.string.indexCheckSubtitle, indexTopicsCount, oldestDate, newestDate),
                        )
                    }
                }
            }

            if (showIndexingDialog &&
                indexingProgress !is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Idle
            ) {
                com.jabook.app.jabook.compose.feature.indexing.IndexingProgressDialog(
                    progress = indexingProgress,
                    indexSize = indexSize,
                    forumStatuses = forumStatuses,
                    onDismiss = {
                        if (indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed ||
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error
                        ) {
                            showIndexingDialog = false
                            coroutineScope.launch {
                                indexSize = indexingViewModel.getIndexSize()
                            }
                        }
                    },
                    onHide = {
                        showIndexingDialog = false
                        indexingViewModel.startIndexingInBackground(context)
                    },
                )
            }

            HorizontalDivider()

            // ─── 9. Network ────────────────────────────────────────────────
            val currentMirror by viewModel.currentMirror.collectAsStateWithLifecycle()
            val availableMirrors by viewModel.availableMirrors.collectAsStateWithLifecycle()

            var showAddMirrorDialog by remember { mutableStateOf(false) }
            var customMirrorUrl by rememberSaveable { mutableStateOf("") }
            var healthCheckInProgress by remember { mutableStateOf<String?>(null) }
            val healthStatus = remember { mutableStateOf<Map<String, MirrorHealth?>>(emptyMap()) }

            SettingsSection(
                title = stringResource(R.string.networkAndMirrors),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.currentMirror),
                subtitle = currentMirror,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = contentPadding),
            ) {
                availableMirrors.forEach { mirror ->
                    MirrorOption(
                        domain = mirror,
                        selected = mirror == currentMirror,
                        healthStatus = healthStatus.value[mirror],
                        isChecking = healthCheckInProgress == mirror,
                        onSelected = { viewModel.updateMirror(mirror) },
                        onCheckHealth = {
                            healthCheckInProgress = mirror
                            viewModel.checkMirrorHealth(mirror) { health ->
                                healthCheckInProgress = null
                                healthStatus.value = healthStatus.value + (mirror to health)
                            }
                        },
                        onRemove =
                            if (mirror !in com.jabook.app.jabook.compose.data.network.MirrorManager.DEFAULT_MIRRORS) {
                                { viewModel.removeCustomMirror(mirror) }
                            } else {
                                null
                            },
                    )
                }
            }

            Spacer(modifier = Modifier.height(itemSpacing))

            SettingsSwitchItem(
                title = stringResource(R.string.autoSwitching),
                subtitle = stringResource(R.string.autoSwitchToWorkingMirrorOnError),
                checked = protoSettings.autoSwitchMirror,
                onCheckedChange = viewModel::updateAutoSwitch,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.addCustomMirror),
                subtitle = stringResource(R.string.enterMirrorUrl),
                onClick = {
                    customMirrorUrl = ""
                    showAddMirrorDialog = true
                },
            )

            if (showAddMirrorDialog) {
                AddMirrorDialog(
                    currentValue = customMirrorUrl,
                    onValueChange = { customMirrorUrl = it },
                    onDismiss = { showAddMirrorDialog = false },
                    onConfirm = {
                        val domain = extractDomain(customMirrorUrl)
                        if (domain != null && domain !in availableMirrors) {
                            viewModel.addCustomMirror(domain)
                            showAddMirrorDialog = false
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.mirrorAddedFormat),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        } else if (domain in availableMirrors) {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.mirrorAlreadyAddedError),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.invalidUrlFormatError),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            HorizontalDivider()

            // ─── 10. About ─────────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.aboutTitle),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.version),
                subtitle = getVersionName(context),
                onClick = {
                    val url = GitHubUrls.releaseTag(getVersionName(context))
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.openSourceLicenses),
                subtitle = stringResource(R.string.viewLicenses),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.LICENSE),
                        )
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.changelog),
                subtitle = stringResource(R.string.changelogDescription),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.CHANGELOG),
                        )
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.github),
                subtitle = stringResource(R.string.githubRepositoryDescription),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.REPOSITORY),
                        )
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.privacyPolicy),
                subtitle = stringResource(R.string.apache20OpenSourceLicense),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.APACHE_LICENSE),
                        )
                    context.startActivity(intent)
                },
            )

            if (BuildConfig.DEBUG || BuildConfig.FLAVOR != "prod") {
                SettingsSection(
                    title = stringResource(R.string.developer),
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                )

                SettingsItem(
                    title = stringResource(R.string.debugToolsTitle),
                    subtitle = stringResource(R.string.viewLogsTestMirrorsCheckCache),
                    onClick = { safeNavigateToDebug() },
                )
            }

            Spacer(modifier = Modifier.height(itemSpacing))
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { heading() }
                .padding(start = 72.dp, top = itemSpacing, end = contentPadding, bottom = 4.dp),
    )
}

@Composable
internal fun SettingsItem(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        trailingContent = {
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick, role = Role.Button)
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
internal fun SettingsItemWithContent(
    title: String,
    subtitle: String? = null,
    subtitleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Column {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().then(subtitleModifier),
                    )
                }
                content()
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClickLabel = stringResource(R.string.openSettings), onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
internal fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    smallSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch,
                ).padding(horizontal = contentPadding, vertical = itemSpacing / 2),
    )
}

@Composable
internal fun SettingsSliderItem(
    title: String,
    subtitle: String? = null,
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueFormatter: (Float) -> String = { it.toString() },
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    smallSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    var currentValue by remember(sliderValue) { mutableStateOf(sliderValue) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = itemSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(smallSpacing))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = valueFormatter(currentValue),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = valueFormatter(valueRange.start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Slider(
                value = currentValue,
                onValueChange = { currentValue = it },
                onValueChangeFinished = { onValueChange(currentValue) },
                valueRange = valueRange,
                steps = steps,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics {
                            contentDescription = title
                        },
            )

            Text(
                text = valueFormatter(valueRange.endInclusive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
