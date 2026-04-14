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

import android.os.PowerManager
import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet
import com.jabook.app.jabook.compose.designsystem.component.LoadingScreen
import com.jabook.app.jabook.compose.feature.player.SquigglySlider
import com.jabook.app.jabook.compose.feature.player.lyrics.LyricsView
import com.jabook.app.jabook.compose.util.rememberClickDebouncer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Logger for PlayerScreen Composable functions.
 */
private val playerScreenLogger by lazy { LoggerFactoryImpl().get("PlayerScreen") }

/**
 * EntryPoint to access AudioMetadataParser from Hilt in Composable.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
public interface AudioMetadataParserEntryPoint {
    public fun audioMetadataParser(): AudioMetadataParser
}

/**
 * Player screen - full screen audio player.
 *
 * Displays:
 * - Book cover
 * - Title and author
 * - Playback controls
 * - Progress bar
 * - Chapter information
 *
 * @param onNavigateBack Callback to navigate back
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun PlayerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val pitchCorrectionEnabled by viewModel.pitchCorrectionEnabled.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val normalizeEnabled by viewModel.normalizeChapterTitles.collectAsStateWithLifecycle()
    val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val visualizerWaveformData by viewModel.visualizerWaveformData.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    val navigationClickGuard = remember { NavigationClickGuard() }

    // Auto-initialize player when book data is ready
    // Only initialize once when we have Success state with actual chapters
    // Use specific keys to avoid unnecessary recomposition
    val shouldInitializePlayer =
        remember(uiState) {
            uiState is PlayerState.Active && (uiState as? PlayerState.Active)?.chapters?.isNotEmpty() == true
        }
    androidx.compose.runtime.LaunchedEffect(shouldInitializePlayer) {
        if (shouldInitializePlayer) {
            viewModel.dispatch(PlayerIntent.InitializePlayer)
        }
    }

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAudioSettingsSheet by remember { mutableStateOf(false) }
    // Legacy settings sheet (if unused, we might want to consolidate or remove)
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Vinyl Mode State
    var isVinylMode by remember { mutableStateOf(false) }

    // Navigator for SupportingPaneScaffold
    val scaffoldNavigator = rememberSupportingPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val openSettingsLabel = stringResource(R.string.openSettings)
    val notificationPermissionPlaybackHint = stringResource(R.string.notificationPermissionPlaybackHint)
    val audioVisualizerPermissionHint = stringResource(R.string.audioVisualizerPermissionHint)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PlayerEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is PlayerEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                PlayerEffect.NavigateBack -> navigationClickGuard.run(onNavigateBack)
            }
        }
    }

    // Check for Power Save Mode to disable expensive visual effects
    val isPowerSaveMode by remember(context) {
        val powerManager = context.getSystemService<PowerManager>()
        mutableStateOf(powerManager?.isPowerSaveMode == true)
    }

    var hasRecordAudioPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasRecordAudioPermission =
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionsLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestMultiplePermissions(),
            onResult = { result ->
                // Handle Notification permission result
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val notificationGranted = result[android.Manifest.permission.POST_NOTIFICATIONS] ?: false
                    if (!notificationGranted) {
                        // Show rationale via snackbar if denied
                        playerScreenLogger.w { "Notification permission denied" }
                        scope.launch {
                            val snackResult =
                                snackbarHostState.showSnackbar(
                                    message = notificationPermissionPlaybackHint,
                                    actionLabel = openSettingsLabel,
                                    duration = androidx.compose.material3.SnackbarDuration.Long,
                                )
                            if (snackResult == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                try {
                                    val intent =
                                        android.content
                                            .Intent(
                                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            ).apply {
                                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                            }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    playerScreenLogger.e(e) { "Failed to open settings" }
                                }
                            }
                        }
                    }
                }
            },
        )

    val recordAudioPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestPermission(),
            onResult = { granted ->
                hasRecordAudioPermission = granted
                if (granted) {
                    playerScreenLogger.d { "RECORD_AUDIO permission granted by user intent" }
                    viewModel.dispatch(PlayerIntent.InitializeVisualizer)
                } else {
                    playerScreenLogger.w { "RECORD_AUDIO permission denied by user intent" }
                    scope.launch {
                        val snackResult =
                            snackbarHostState.showSnackbar(
                                message = audioVisualizerPermissionHint,
                                actionLabel = openSettingsLabel,
                                duration = androidx.compose.material3.SnackbarDuration.Long,
                            )
                        if (snackResult == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            try {
                                val intent =
                                    android.content
                                        .Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        ).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                        }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                playerScreenLogger.e(e) { "Failed to open settings" }
                            }
                        }
                    }
                }
            },
        )

    val requestRecordAudioPermission: () -> Unit = {
        val alreadyGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            hasRecordAudioPermission = true
            viewModel.dispatch(PlayerIntent.InitializeVisualizer)
        } else {
            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val notificationPermissionGranted =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat
                    .checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        val permissionsToRequest =
            PlayerPermissionPolicy.entryPermissionsToRequest(
                sdkInt = android.os.Build.VERSION.SDK_INT,
                isNotificationPermissionGranted = notificationPermissionGranted,
            )
        val permissionsMissing =
            permissionsToRequest.filter {
                androidx.core.content.ContextCompat
                    .checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

        if (permissionsMissing.isNotEmpty()) {
            playerScreenLogger.d { "Requesting permissions: $permissionsMissing" }
            notificationPermissionsLauncher.launch(permissionsMissing.toTypedArray())
        }
    }

    // Handle back gesture - prioritize chapters pane, then screen exit
    // Always intercept to ensure proper navigation handling
    androidx.activity.compose.BackHandler {
        if (scaffoldNavigator.canNavigateBack()) {
            scope.launch {
                scaffoldNavigator.navigateBack()
            }
        } else {
            navigationClickGuard.run { onNavigateBack() }
        }
    }

    // Playback Speed Sheet
    if (showSpeedSheet) {
        val speedSheetState = rememberModalBottomSheetState()
        PlaybackSpeedSheet(
            currentSpeed = playbackSpeed,
            pitchCorrectionEnabled = pitchCorrectionEnabled,
            onSpeedSelected = { speed ->
                viewModel.dispatch(PlayerIntent.SetPlaybackSpeed(speed))
            },
            onPitchCorrectionChanged = { enabled ->
                viewModel.dispatch(PlayerIntent.SetPitchCorrectionEnabled(enabled))
            },
            onDismiss = { showSpeedSheet = false },
            sheetState = speedSheetState,
        )
    }

    // Sleep Timer Sheet
    if (showSleepTimerSheet) {
        SleepTimerSheet(
            currentState = sleepTimerState,
            onStartTimer = { minutes ->
                viewModel.dispatch(PlayerIntent.StartSleepTimer(minutes))
            },
            onStartTimerEndOfChapter = {
                viewModel.dispatch(PlayerIntent.StartSleepTimerEndOfChapter)
            },
            onStartTimerEndOfTrack = {
                viewModel.dispatch(PlayerIntent.StartSleepTimerEndOfTrack)
            },
            onCancelTimer = { viewModel.dispatch(PlayerIntent.CancelSleepTimer) },
            onDismiss = { showSleepTimerSheet = false },
        )
    }

    // Removed Chapter Selector Sheet - using adaptive pane instead

    // Player Settings Sheet (Book Specific)
    if (showSettingsSheet && uiState is PlayerState.Active) {
        val state = uiState as PlayerState.Active
        PlayerSettingsSheet(
            book = state.book,
            onUpdateSettings = { rewindSeconds, forwardSeconds ->
                viewModel.dispatch(
                    PlayerIntent.UpdateBookSeekSettings(
                        rewindSeconds = rewindSeconds,
                        forwardSeconds = forwardSeconds,
                    ),
                )
            },
            onResetSettings = { viewModel.dispatch(PlayerIntent.ResetBookSeekSettings) },
            onDismiss = { showSettingsSheet = false },
            isVinylMode = isVinylMode,
            onVinylModeChange = { isVinylMode = it },
        )
    }

    // Audio Enhancements Sheet
    if (showAudioSettingsSheet) {
        AudioSettingsSheet(
            state = audioSettings,
            onUpdateSettings = {
                volumeBoostLevel,
                skipSilence,
                skipSilenceThresholdDb,
                skipSilenceMinMs,
                skipSilenceMode,
                normalizeVolume,
                speechEnhancer,
                autoVolumeLeveling,
                ->
                viewModel.dispatch(
                    PlayerIntent.UpdateAudioSettings(
                        volumeBoostLevel = volumeBoostLevel,
                        skipSilence = skipSilence,
                        skipSilenceThresholdDb = skipSilenceThresholdDb,
                        skipSilenceMinMs = skipSilenceMinMs,
                        skipSilenceMode = skipSilenceMode,
                        normalizeVolume = normalizeVolume,
                        speechEnhancer = speechEnhancer,
                        autoVolumeLeveling = autoVolumeLeveling,
                    ),
                )
            },
            onDismiss = { showAudioSettingsSheet = false },
        )
    }

    // Stats for Nerds Overlay
    var showStatsOverlay by remember { mutableStateOf(false) }
    if (showStatsOverlay) {
        val stats by viewModel.playerStats.collectAsStateWithLifecycle()
        StatsOverlay(
            stats = stats,
            onDismiss = { showStatsOverlay = false },
        )
    }

    // SupportingPaneScaffold for adaptive chapter display
    SupportingPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        value = scaffoldNavigator.scaffoldValue,
        mainPane = {
            AnimatedPane(modifier = Modifier) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    Box(
                        modifier =
                            Modifier
                                .padding(padding)
                                .windowInsetsPadding(WindowInsets.systemBars),
                    ) {
                        when (val state = uiState) {
                            is PlayerState.Loading -> {
                                LoadingScreen(message = stringResource(R.string.loadingPlayer))
                            }

                            is PlayerState.Active -> {
                                // Click debouncer for preventing double clicks (inspired by Easybook)
                                val clickDebouncer = rememberClickDebouncer(debounceTimeMs = 300)

                                // Haze State for Glassmorphism
                                val hazeState = rememberHazeState()

                                // Removed GestureOverlay as per user request to disable brightness/volume/seek swipes
                                PremiumPlayerBackground(
                                    themeColors = state.themeColors,
                                    hazeState = hazeState,
                                    isPowerSaveMode = isPowerSaveMode,
                                ) {
                                    PlayerContent(
                                        state = state,
                                        playbackSpeed = playbackSpeed,
                                        hazeState = hazeState,
                                        isVinylMode = isVinylMode,
                                        sleepTimerState = sleepTimerState,
                                        normalizeEnabled = normalizeEnabled,
                                        chapterRepeatMode = state.chapterRepeatMode,
                                        visualizerWaveformData = visualizerWaveformData,
                                        onPlayPause = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clickDebouncer.debounce {
                                                viewModel.dispatch(PlayerIntent.TogglePlayPause)
                                            }
                                        },
                                        onSkipNext = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clickDebouncer.debounce { viewModel.dispatch(PlayerIntent.SkipNext) }
                                        },
                                        onSkipPrevious = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clickDebouncer.debounce { viewModel.dispatch(PlayerIntent.SkipPrevious) }
                                        },
                                        onSeek = { positionMs ->
                                            viewModel.dispatch(PlayerIntent.SeekTo(positionMs))
                                        },
                                        onSeekForward = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clickDebouncer.debounce { viewModel.dispatch(PlayerIntent.SeekForward) }
                                        },
                                        onSeekBackward = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clickDebouncer.debounce { viewModel.dispatch(PlayerIntent.SeekBackward) }
                                        },
                                        onSelectChapter = { chapterIndex ->
                                            viewModel.dispatch(PlayerIntent.SelectChapter(chapterIndex))
                                        },
                                        onChapterClick = {
                                            // Toggle chapters pane on medium/expanded screens
                                            clickDebouncer.debounce {
                                                scope.launch {
                                                    if (scaffoldNavigator.canNavigateBack()) {
                                                        scaffoldNavigator.navigateBack()
                                                    } else {
                                                        scaffoldNavigator.navigateTo(
                                                            SupportingPaneScaffoldRole.Supporting,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onSpeedClick = { showSpeedSheet = true },
                                        onAudioSettingsClick = { showAudioSettingsSheet = true },
                                        onSleepTimerClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showSleepTimerSheet = true
                                        },
                                        onChapterRepeatClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            clickDebouncer.debounce {
                                                viewModel.dispatch(PlayerIntent.ToggleChapterRepeat)
                                            }
                                        },
                                        onStatsClick = { showStatsOverlay = true },
                                        hasRecordAudioPermission = hasRecordAudioPermission,
                                        onRequestRecordAudioPermission = requestRecordAudioPermission,
                                        onInitializeVisualizer = {
                                            viewModel.dispatch(PlayerIntent.InitializeVisualizer)
                                        },
                                        onSetVisualizerEnabled = { enabled ->
                                            viewModel.dispatch(PlayerIntent.SetVisualizerEnabled(enabled))
                                        },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                }
                            }

                            is PlayerState.Error -> {
                                ErrorScreen(
                                    message = state.message,
                                    onRetry = { navigationClickGuard.run(onNavigateBack) },
                                )
                            }
                        }
                    }
                }
            }
        },
        supportingPane = {
            AnimatedPane(modifier = Modifier) {
                // Show chapter pane only when we have chapters
                if (uiState is PlayerState.Active) {
                    val state = uiState as PlayerState.Active
                    PlayerChapterPane(
                        chapters = state.chapters,
                        currentChapterIndex = state.currentChapterIndex,
                        normalizeEnabled = normalizeEnabled,
                        onChapterClick = { chapterIndex ->
                            // Start playback immediately (skipToChapter now includes play())
                            viewModel.dispatch(PlayerIntent.SelectChapter(chapterIndex))
                            // On compact screens, smoothly close the pane after selection
                            scope.launch {
                                // Small delay to ensure playback starts before closing
                                kotlinx.coroutines.delay(50L)
                                if (scaffoldNavigator.canNavigateBack()) {
                                    scaffoldNavigator.navigateBack()
                                }
                            }
                        },
                    )
                }
            }
        },
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    )
}

@OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
)
@Composable
private fun PlayerContent(
    state: PlayerState.Active,
    playbackSpeed: Float,
    hazeState: HazeState?,
    isVinylMode: Boolean,
    sleepTimerState: com.jabook.app.jabook.compose.domain.model.SleepTimerState,
    normalizeEnabled: Boolean,
    chapterRepeatMode: ChapterRepeatMode,
    visualizerWaveformData: FloatArray,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onChapterClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioSettingsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onChapterRepeatClick: () -> Unit,
    onStatsClick: () -> Unit,
    hasRecordAudioPermission: Boolean,
    onRequestRecordAudioPermission: () -> Unit,
    onInitializeVisualizer: () -> Unit,
    onSetVisualizerEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    // Get window size class for adaptive sizing
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
            ?: throw IllegalStateException("Cannot get Activity from context")
    val rawWindowSizeClass = calculateWindowSizeClass(activity)
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClass(rawWindowSizeClass, context)

    // Adaptive sizes for compact screens (phones)
    val isCompact =
        windowSizeClass.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
    val playPauseButtonSize = if (isCompact) 72.dp else 80.dp
    val skipButtonSize = if (isCompact) 56.dp else 64.dp
    val seekButtonSize = if (isCompact) 48.dp else 56.dp
    val playPauseIconSize = if (isCompact) 40.dp else 48.dp
    val skipIconSize = if (isCompact) 40.dp else 48.dp
    val seekIconSize = if (isCompact) 32.dp else 40.dp
    // Adaptive sizes for control buttons (Speed, Repeat, Timer) - increased for better ergonomics
    val controlButtonHeight = if (isCompact) 48.dp else 56.dp
    val controlButtonIconSize = if (isCompact) 22.dp else 24.dp
    val controlButtonTextSize =
        if (isCompact) {
            androidx.compose.ui.unit.TextUnit(
                14f,
                androidx.compose.ui.unit.TextUnitType.Sp,
            )
        } else {
            androidx.compose.ui.unit
                .TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
        }
    val controlButtonSpacing = if (isCompact) 8.dp else 12.dp
    // Optimized cover size: 70% for compact (phone optimization), 88% for larger screens
    val coverWidth = if (isCompact) 0.70f else 0.88f
    val contentPadding = AdaptiveUtils.getContentPadding(windowSizeClass)
    // Increased spacing for better ergonomics
    val itemSpacing = if (isCompact) 16.dp else AdaptiveUtils.getItemSpacing(windowSizeClass)
    // Spacing for compact screens between specific elements
    val smallItemSpacing = if (isCompact) 8.dp else 12.dp
    val playbackSpeedLabel by remember(playbackSpeed) {
        derivedStateOf { formatPlaybackSpeedLabel(playbackSpeed) }
    }
    // Large spacing for major sections
    val largeItemSpacing = if (isCompact) 24.dp else 32.dp

    // Get author from audio metadata if available
    var authorFromMetadata by remember { mutableStateOf<String?>(null) }
    val metadataParser =
        remember {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    AudioMetadataParserEntryPoint::class.java,
                ).audioMetadataParser()
        }

    LaunchedEffect(state.currentChapter?.fileUrl) {
        authorFromMetadata = null
        val fileUrl = state.currentChapter?.fileUrl
        if (!fileUrl.isNullOrBlank()) {
            val file = File(fileUrl)
            if (file.exists()) {
                val metadata =
                    withContext(Dispatchers.IO) {
                        metadataParser.parseMetadata(fileUrl)
                    }
                authorFromMetadata = metadata?.artist?.takeIf { it.isNotBlank() }
            }
        }
    }

    val displayAuthor = authorFromMetadata
    val sleepTimerAccessibilityDescription =
        when (sleepTimerState) {
            is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active ->
                "${stringResource(R.string.sleepTimer)}, ${formatSleepTimerRemaining(sleepTimerState.remainingSeconds)}"
            com.jabook.app.jabook.compose.domain.model.SleepTimerState.EndOfChapter ->
                "${stringResource(R.string.sleepTimer)}, ${stringResource(R.string.endOfChapterLabel)}"
            is com.jabook.app.jabook.compose.domain.model.SleepTimerState.EndOfTrack ->
                "${stringResource(R.string.sleepTimer)}, ${stringResource(R.string.endOfTrackLabel)}"
            com.jabook.app.jabook.compose.domain.model.SleepTimerState.Idle ->
                stringResource(R.string.sleepTimer)
        }

    // Lyrics visibility state
    var showLyrics by remember { mutableStateOf(false) }
    val hasLyrics by remember(state.lyrics) {
        derivedStateOf { !state.lyrics.isNullOrEmpty() }
    }
    val showingLyrics by remember(showLyrics, hasLyrics) {
        derivedStateOf { showLyrics && hasLyrics }
    }
    val seekScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    // Dynamic Theme Background with Glassmorphism Effect
    // Background is now handled by PremiumPlayerBackground wrapping this content
    val themeColors = state.themeColors

    // Main Content
    // We use a Box to contain the LazyColumn (and potential overlays like visualizer if moved, but visualizer is inside list)
    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(
                        start = contentPadding,
                        end = contentPadding,
                        top = if (isCompact) 0.dp else 8.dp,
                        bottom = if (isCompact) 56.dp else 96.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            // Author from metadata (above cover) - hidden on compact to save space
            if (displayAuthor != null && !isCompact) {
                item {
                    Text(
                        text = displayAuthor,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = smallItemSpacing),
                    )
                }
            }

            // Spacer before cover
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            }

            // Book cover
            item {
                val imageModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "cover_${state.book.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    }

                val context = LocalContext.current
                val imageRequest =
                    CoverUtils
                        .createCoverImageRequest(
                            book = state.book,
                            context = context,
                            placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
                            errorColor = MaterialTheme.colorScheme.error,
                            fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                            cornerRadius = 16f, // 16dp rounded corners for player
                        ).build()
                val canToggleLyrics = hasLyrics
                val toggleLyricsLabel = stringResource(R.string.toggleLyricsView)
                val toggleLyricsStateDescription =
                    if (showingLyrics) {
                        stringResource(R.string.lyricsVisibleState)
                    } else {
                        stringResource(R.string.lyricsHiddenState)
                    }

                // Animated "breathing" effect for the cover
                val infiniteTransition =
                    androidx.compose.animation.core
                        .rememberInfiniteTransition(label = "coverScale")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.03f,
                    animationSpec =
                        androidx.compose.animation.core.infiniteRepeatable(
                            animation =
                                androidx.compose.animation.core
                                    .tween(4000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        ),
                    label = "scale",
                )

                if (showingLyrics) {
                    Box(
                        modifier =
                            imageModifier
                                .fillMaxWidth(coverWidth)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    ) {
                        LyricsView(
                            lyrics = state.lyrics.orEmpty(),
                            currentPosition = state.currentPosition,
                            onSeek = onSeek,
                        )
                    }
                } else if (isVinylMode) {
                    VinylCover(
                        imageRequest = imageRequest,
                        isPlaying = state.isPlaying,
                        modifier =
                            imageModifier
                                .fillMaxWidth(coverWidth)
                                .semantics {
                                    if (canToggleLyrics) {
                                        role = androidx.compose.ui.semantics.Role.Button
                                        contentDescription = toggleLyricsLabel
                                        stateDescription = toggleLyricsStateDescription
                                    }
                                }.clickable(
                                    enabled = canToggleLyrics,
                                    onClickLabel = toggleLyricsLabel,
                                ) {
                                    if (canToggleLyrics) {
                                        showLyrics = !showLyrics
                                    }
                                },
                    )
                } else {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = state.book.title,
                        modifier =
                            imageModifier
                                .fillMaxWidth(coverWidth)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = if (state.isPlaying) scale else 1f
                                    scaleY = if (state.isPlaying) scale else 1f
                                }.clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .semantics {
                                    if (canToggleLyrics) {
                                        role = androidx.compose.ui.semantics.Role.Button
                                        stateDescription = toggleLyricsStateDescription
                                    }
                                }.clickable(
                                    enabled = canToggleLyrics,
                                    onClickLabel = toggleLyricsLabel,
                                ) {
                                    if (canToggleLyrics) {
                                        showLyrics = !showLyrics
                                    }
                                },
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Spacer after cover
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))
            }

            // Book info
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 8.dp else 0.dp),
                ) {
                    Text(
                        text = state.book.title,
                        style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Spacer after book title
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            }

            // Progress section
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 4.dp else 0.dp),
                ) {
                    val chapterTimeline by remember(state.chapters, state.currentChapterIndex, state.currentPosition) {
                        derivedStateOf {
                            ChapterSeekbarPolicy.buildTimeline(
                                chapters = state.chapters,
                                currentChapterIndex = state.currentChapterIndex,
                                currentChapterPositionMs = state.currentPosition.coerceAtLeast(0L),
                            )
                        }
                    }
                    val playerProgress by remember(chapterTimeline) {
                        derivedStateOf {
                            chapterTimeline.progress
                        }
                    }

                    // Slider state-machine v2:
                    // - livePosition = playerProgress (single source from player timeline)
                    // - dragPosition = transient local drag value
                    // - pendingSeekPosition = last user seek target until player converges
                    var dragPosition by remember { mutableStateOf<Float?>(null) }
                    var pendingSeekPosition by remember { mutableStateOf<Float?>(null) }
                    var coalescedPlayerProgress by remember { mutableStateOf(playerProgress) }
                    var lastSliderHapticProgress by remember { mutableStateOf<Float?>(null) }
                    val isDragging by remember(dragPosition) { derivedStateOf { dragPosition != null } }
                    val displayedProgress by remember(coalescedPlayerProgress, dragPosition, pendingSeekPosition) {
                        derivedStateOf {
                            PlayerSliderStateMachinePolicy.displayedProgress(
                                liveProgress = coalescedPlayerProgress,
                                dragProgress = dragPosition,
                                pendingSeekProgress = pendingSeekPosition,
                            )
                        }
                    }
                    val previewSeekTarget by remember(state.chapters, displayedProgress) {
                        derivedStateOf {
                            ChapterSeekbarPolicy.resolveSeekTarget(
                                chapters = state.chapters,
                                progress = displayedProgress,
                            )
                        }
                    }
                    val currentGlobalPositionMs by remember(
                        isDragging,
                        displayedProgress,
                        chapterTimeline.totalDurationMs,
                        chapterTimeline.globalPositionMs,
                    ) {
                        derivedStateOf {
                            if (isDragging && chapterTimeline.totalDurationMs > 0) {
                                (
                                    displayedProgress.coerceIn(
                                        0f,
                                        1f,
                                    ) * chapterTimeline.totalDurationMs.toFloat()
                                ).toLong()
                            } else {
                                chapterTimeline.globalPositionMs
                            }
                        }
                    }

                    // Coalesce rapid progress deltas to reduce jitter/recomposition pressure on slider.
                    LaunchedEffect(playerProgress, chapterTimeline.totalDurationMs) {
                        coalescedPlayerProgress =
                            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                                previousProgress = coalescedPlayerProgress,
                                incomingProgress = playerProgress,
                                totalDurationMs = chapterTimeline.totalDurationMs,
                            )
                    }

                    // Keep pending seek state until player progress converges near user target
                    // to avoid post-seek jump-back jitter.
                    LaunchedEffect(playerProgress, pendingSeekPosition, isDragging) {
                        if (!isDragging && pendingSeekPosition != null) {
                            val result =
                                SliderSeekSyncPolicy.resolveFromPlayerProgress(
                                    playerProgress = playerProgress,
                                    currentSliderPosition = pendingSeekPosition ?: playerProgress,
                                    isDragging = false,
                                    awaitingSeekSync = true,
                                )
                            if (!result.awaitingSeekSync) {
                                pendingSeekPosition = null
                            }
                        }
                    }

                    // Reset stale drag-seek state on chapter/duration changes to avoid jump-back race
                    // when player timeline is rebuilt after chapter switch.
                    LaunchedEffect(chapterTimeline.totalDurationMs, state.currentChapterIndex) {
                        if (!isDragging) {
                            coalescedPlayerProgress = playerProgress
                            pendingSeekPosition = null
                        }
                    }

                    // Guard against stale pending seek flag if player progress update is delayed.
                    LaunchedEffect(pendingSeekPosition) {
                        if (pendingSeekPosition != null) {
                            delay(1500L)
                            pendingSeekPosition = null
                        }
                    }

                    val playbackPositionLabel = stringResource(R.string.playbackPositionLabel)
                    val sliderHaptic = LocalHapticFeedback.current

                    SquigglySlider(
                        value = displayedProgress,
                        onValueChange = { newProgress ->
                            pendingSeekPosition = null
                            val constrainedProgress = newProgress.coerceIn(0f, 1f)
                            val shouldTriggerHaptic =
                                lastSliderHapticProgress == null ||
                                    kotlin.math.abs(constrainedProgress - (lastSliderHapticProgress ?: constrainedProgress)) >=
                                    0.05f
                            if (shouldTriggerHaptic) {
                                sliderHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastSliderHapticProgress = constrainedProgress
                            }
                            dragPosition = constrainedProgress
                        },
                        onValueChangeFinished = {
                            // Seek only when user finishes dragging
                            val targetProgress = dragPosition ?: displayedProgress
                            if (chapterTimeline.totalDurationMs > 0 && targetProgress.isFinite()) {
                                val target =
                                    ChapterSeekbarPolicy.resolveSeekTarget(
                                        chapters = state.chapters,
                                        progress = targetProgress,
                                    )
                                pendingSeekPosition = targetProgress
                                if (target.chapterIndex != state.currentChapterIndex) {
                                    onSelectChapter(target.chapterIndex)
                                    seekScope.launch {
                                        delay(80L)
                                        onSeek(target.chapterPositionMs)
                                    }
                                } else {
                                    onSeek(target.chapterPositionMs)
                                }
                            }
                            dragPosition = null
                            lastSliderHapticProgress = null
                        },
                        isPlaying = state.isPlaying,
                        chapterMarkersFractions = chapterTimeline.chapterMarkersFractions,
                        activeTrackColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                        inactiveTrackColor =
                            (themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary).copy(
                                alpha = 0.24f,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics {
                                    contentDescription = playbackPositionLabel
                                    val current = formatDuration(currentGlobalPositionMs)
                                    val total = formatDuration(chapterTimeline.totalDurationMs)
                                    stateDescription = "$current of $total"
                                },
                    )

                    if (isDragging) {
                        val previewTitle =
                            state.chapters
                                .getOrNull(previewSeekTarget.chapterIndex)
                                ?.title
                                .orEmpty()
                        Text(
                            text = "${previewSeekTarget.chapterIndex + 1}. $previewTitle",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(currentGlobalPositionMs),
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = formatDuration(chapterTimeline.totalDurationMs),
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Smart Info (Chapter index & Finish time)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val chapterText =
                            stringResource(
                                R.string.chapterOf,
                                (if (isDragging) previewSeekTarget.chapterIndex else state.currentChapterIndex) + 1,
                                state.chapters.size,
                            )

                        val formattedFinishTime by remember(
                            chapterTimeline.totalDurationMs,
                            currentGlobalPositionMs,
                            state.playbackSpeed,
                        ) {
                            derivedStateOf {
                                val remainingMs = (chapterTimeline.totalDurationMs - currentGlobalPositionMs).coerceAtLeast(0L)
                                val speed = state.playbackSpeed
                                val realRemainingMs = if (speed > 0f) (remainingMs / speed).toLong() else remainingMs
                                val finishTime =
                                    java.util.Calendar.getInstance().apply {
                                        add(java.util.Calendar.MILLISECOND, realRemainingMs.toInt())
                                    }
                                java.text
                                    .SimpleDateFormat(
                                        "HH:mm",
                                        java.util.Locale.getDefault(),
                                    ).format(finishTime.time)
                            }
                        }
                        val finishText = stringResource(R.string.finishAt, formattedFinishTime)

                        Text(
                            text = "$chapterText • $finishText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Audio Visualizer - hidden on compact screens to save space
            if (!isCompact) {
                item {
                    LaunchedEffect(hasRecordAudioPermission) {
                        if (!hasRecordAudioPermission) {
                            onSetVisualizerEnabled(false)
                        }
                    }

                    if (hasRecordAudioPermission) {
                        // Initialize visualizer only after explicit permission grant
                        LaunchedEffect(state.isPlaying, hasRecordAudioPermission) {
                            if (state.isPlaying) {
                                onInitializeVisualizer()
                                onSetVisualizerEnabled(true)
                            } else {
                                onSetVisualizerEnabled(false)
                            }
                        }

                        AudioVisualizer(
                            waveformData = visualizerWaveformData,
                            isPlaying = state.isPlaying,
                            style = VisualizerStyle.CIRCULAR,
                            height = 48.dp,
                            primaryColor = state.themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                            secondaryColor =
                                state.themeColors?.primaryColor?.copy(alpha = 0.5f)
                                    ?: MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FilledTonalButton(
                            onClick = onRequestRecordAudioPermission,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(text = stringResource(R.string.enableVisualizer))
                        }
                    }
                }
            }

            // Spacer before chapter button
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            }

            // Current Chapter Button
            item {
                state.currentChapter?.let { chapter ->
                    FilledTonalButton(
                        onClick = onChapterClick,
                        modifier =
                            Modifier
                                .fillMaxWidth(if (isCompact) 0.98f else 0.95f)
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .height(if (isCompact) 44.dp else 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(if (isCompact) 18.dp else 20.dp)
                                    .padding(end = if (isCompact) 6.dp else 8.dp),
                        )
                        Text(
                            text =
                                com.jabook.app.jabook.compose.core.util.ChapterUtils.formatChapterName(
                                    chapter,
                                    state.currentChapterIndex,
                                    stringResource(R.string.chapter_prefix),
                                    normalizeEnabled,
                                ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize =
                                if (isCompact) {
                                    androidx.compose.ui.unit.TextUnit(
                                        13f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    )
                                } else {
                                    androidx.compose.ui.unit
                                        .TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
                                },
                        )
                    }
                }
            }

            // Spacer before playback controls
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))
            }

            // Playback controls
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isCompact) smallItemSpacing else 0.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Skip previous
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(skipButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previousChapter),
                            modifier = Modifier.size(skipIconSize),
                        )
                    }

                    // Seek backward (10s)
                    IconButton(
                        onClick = onSeekBackward,
                        modifier = Modifier.size(seekButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay,
                            contentDescription = stringResource(R.string.seekBackwardDescription, state.rewindInterval),
                            modifier = Modifier.size(seekIconSize),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Play/Pause - Larger and more prominent
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(playPauseButtonSize * 1.2f),
                    ) {
                        FilledIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.fillMaxSize(),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                                    contentColor = themeColors?.onPrimaryColor ?: MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription =
                                    if (state.isPlaying) {
                                        stringResource(R.string.pauseButton)
                                    } else {
                                        stringResource(R.string.playButton)
                                    },
                                modifier = Modifier.size(playPauseIconSize * 1.2f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Seek forward (30s)
                    IconButton(
                        onClick = onSeekForward,
                        modifier = Modifier.size(seekButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = stringResource(R.string.seekForwardDescription, state.forwardInterval),
                            modifier = Modifier.size(seekIconSize),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    // Skip next
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(skipButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.nextChapter),
                            modifier = Modifier.size(skipIconSize),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Spacer before control buttons
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))
            }

            // Control Buttons - Split into 2 rows for compact screens
            item {
                if (isCompact) {
                    // Compact: Two rows for better ergonomics
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // First row: Speed, EQ & Repeat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    controlButtonSpacing,
                                    Alignment.CenterHorizontally,
                                ),
                        ) {
                            // Playback Speed Button
                            FilledTonalButton(
                                onClick = onSpeedClick,
                                modifier = Modifier.weight(1f).height(controlButtonHeight),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = stringResource(R.string.playbackSpeedTitle),
                                    modifier = Modifier.size(controlButtonIconSize).padding(end = 4.dp),
                                )
                                Text(
                                    text = playbackSpeedLabel,
                                    fontSize = controlButtonTextSize,
                                )
                            }

                            // Audio Settings (EQ) Button
                            FilledTonalButton(
                                onClick = onAudioSettingsClick,
                                modifier = Modifier.weight(1f).height(controlButtonHeight),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune, // Or Equalizer if available
                                    contentDescription = stringResource(R.string.audioSettingsTitle),
                                    modifier = Modifier.size(controlButtonIconSize),
                                )
                            }

                            // Chapter Repeat Button
                            FilledTonalButton(
                                onClick = onChapterRepeatClick,
                                modifier = Modifier.weight(1f).height(controlButtonHeight),
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor =
                                            when (chapterRepeatMode) {
                                                ChapterRepeatMode.OFF -> MaterialTheme.colorScheme.surfaceVariant
                                                ChapterRepeatMode.ONCE -> MaterialTheme.colorScheme.primaryContainer
                                                ChapterRepeatMode.INFINITE -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                    ),
                            ) {
                                when (chapterRepeatMode) {
                                    ChapterRepeatMode.INFINITE ->
                                        Text(
                                            "∞",
                                            fontSize = controlButtonTextSize,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    ChapterRepeatMode.OFF ->
                                        Icon(
                                            Icons.Outlined.Repeat,
                                            stringResource(R.string.noRepeat),
                                            Modifier.size(controlButtonIconSize),
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    ChapterRepeatMode.ONCE ->
                                        Icon(
                                            Icons.Filled.RepeatOne,
                                            stringResource(R.string.repeatTrack),
                                            Modifier.size(controlButtonIconSize),
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                }
                            }
                        }

                        // Second row: Timer & Lyrics (if available)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    controlButtonSpacing,
                                    Alignment.CenterHorizontally,
                                ),
                        ) {
                            // Sleep Timer Button
                            FilledTonalButton(
                                onClick = onSleepTimerClick,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(controlButtonHeight)
                                        .semantics {
                                            contentDescription = sleepTimerAccessibilityDescription
                                        },
                            ) {
                                Icon(
                                    Icons.Filled.Timer,
                                    stringResource(R.string.sleepTimer),
                                    Modifier.size(controlButtonIconSize),
                                )
                                if (sleepTimerState is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active) {
                                    val activeState = sleepTimerState
                                    Text(
                                        formatSleepTimerRemaining(activeState.remainingSeconds),
                                        fontSize = controlButtonTextSize,
                                    )
                                }
                            }

                            // Lyrics Toggle Button
                            if (hasLyrics) {
                                FilledTonalButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showLyrics = !showLyrics
                                    },
                                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                if (showingLyrics) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                            contentColor =
                                                if (showingLyrics) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        ),
                                ) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Filled.Description,
                                        stringResource(R.string.lyrics),
                                        Modifier.size(controlButtonIconSize),
                                    )
                                }
                            } else {
                                // Empty spacer to balance the row when no lyrics
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // Larger screens: Single row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                controlButtonSpacing,
                                Alignment.CenterHorizontally,
                            ),
                    ) {
                        // Playback Speed Button
                        FilledTonalButton(
                            onClick = onSpeedClick,
                            modifier = Modifier.weight(1f).height(controlButtonHeight),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = stringResource(R.string.playbackSpeedTitle),
                                modifier = Modifier.size(controlButtonIconSize).padding(end = 8.dp),
                            )
                            Text(
                                text = playbackSpeedLabel,
                                fontSize = controlButtonTextSize,
                            )
                        }

                        // Audio Settings (EQ) Button
                        FilledTonalButton(
                            onClick = onAudioSettingsClick,
                            modifier = Modifier.weight(1f).height(controlButtonHeight),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.audioSettingsTitle),
                                modifier = Modifier.size(controlButtonIconSize),
                            )
                        }

                        // Chapter Repeat Button
                        FilledTonalButton(
                            onClick = onChapterRepeatClick,
                            modifier = Modifier.weight(1f).height(controlButtonHeight),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor =
                                        when (chapterRepeatMode) {
                                            ChapterRepeatMode.OFF -> MaterialTheme.colorScheme.surfaceVariant
                                            ChapterRepeatMode.ONCE -> MaterialTheme.colorScheme.primaryContainer
                                            ChapterRepeatMode.INFINITE -> MaterialTheme.colorScheme.primaryContainer
                                        },
                                ),
                        ) {
                            when (chapterRepeatMode) {
                                ChapterRepeatMode.INFINITE ->
                                    Text(
                                        "∞",
                                        fontSize = controlButtonTextSize,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                ChapterRepeatMode.OFF ->
                                    Icon(
                                        Icons.Outlined.Repeat,
                                        stringResource(R.string.noRepeat),
                                        Modifier.size(controlButtonIconSize),
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                ChapterRepeatMode.ONCE ->
                                    Icon(
                                        Icons.Filled.RepeatOne,
                                        stringResource(R.string.repeatTrack),
                                        Modifier.size(controlButtonIconSize),
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                            }
                        }

                        // Sleep Timer Button
                        FilledTonalButton(
                            onClick = onSleepTimerClick,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(controlButtonHeight)
                                    .semantics {
                                        contentDescription = sleepTimerAccessibilityDescription
                                    },
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                stringResource(R.string.sleepTimer),
                                Modifier.size(controlButtonIconSize),
                            )
                            if (sleepTimerState is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active) {
                                val activeState = sleepTimerState
                                Text(
                                    formatSleepTimerRemaining(activeState.remainingSeconds),
                                    fontSize = controlButtonTextSize,
                                )
                            }
                        }

                        // Lyrics Toggle Button
                        if (hasLyrics) {
                            FilledTonalButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showLyrics = !showLyrics
                                },
                                modifier = Modifier.weight(1f).height(controlButtonHeight),
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor =
                                            if (showingLyrics) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        contentColor =
                                            if (showingLyrics) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                            ) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.Description,
                                    stringResource(R.string.lyrics),
                                    Modifier.size(controlButtonIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings sheet for player screen.
 *
 * Allows users to configure:
 * - Playback speed
 * - Sleep timer
 * - Vinyl mode
 *
 * @param book The book being played
 * @param onUpdateSettings Callback when settings are updated (speed, sleep timer)
 * @param onResetSettings Callback to reset settings to defaults
 * @param onDismiss Callback when sheet is dismissed
 * @param isVinylMode Current vinyl mode state
 * @param onVinylModeChange Callback when vinyl mode is toggled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PlayerSettingsSheet(
    book: com.jabook.app.jabook.compose.domain.model.Book,
    onUpdateSettings: (Int?, Int?) -> Unit,
    onResetSettings: () -> Unit,
    onDismiss: () -> Unit,
    isVinylMode: Boolean,
    onVinylModeChange: (Boolean) -> Unit,
) {
    JabookModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.overrideBookSettings),
                style = MaterialTheme.typography.headlineSmall,
            )

            // Switch: Use Global / Custom
            var useGlobal by remember {
                mutableStateOf(book.rewindDuration == null && book.forwardDuration == null)
            }

            // Local state for sliders (init from book or default 10/30 if null)
            var rewindSeconds by remember { mutableStateOf((book.rewindDuration ?: 10).toFloat()) }
            var forwardSeconds by remember { mutableStateOf((book.forwardDuration ?: 30).toFloat()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.useGlobalSettings),
                    style = MaterialTheme.typography.bodyLarge,
                )
                androidx.compose.material3.Switch(
                    checked = useGlobal,
                    onCheckedChange = {
                        useGlobal = it
                        if (it) {
                            onResetSettings()
                        } else {
                            // When switching to custom, save current slider values
                            onUpdateSettings(rewindSeconds.toInt(), forwardSeconds.toInt())
                        }
                    },
                )
            }

            if (!useGlobal) {
                HorizontalDivider()

                Text(
                    text = stringResource(R.string.customSettings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Rewind Slider
                Text(
                    text = stringResource(R.string.rewindDurationTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = rewindSeconds,
                        onValueChange = {
                            rewindSeconds = it
                            onUpdateSettings(rewindSeconds.toInt(), forwardSeconds.toInt())
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.durationSecondsFull,
                                rewindSeconds.toInt(),
                                rewindSeconds.toInt(),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End,
                    )
                }

                // Forward Slider
                Text(
                    text = stringResource(R.string.forwardDurationTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = forwardSeconds,
                        onValueChange = {
                            forwardSeconds = it
                            onUpdateSettings(rewindSeconds.toInt(), forwardSeconds.toInt())
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.durationSecondsFull,
                                forwardSeconds.toInt(),
                                forwardSeconds.toInt(),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            // Vinyl Mode Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.vinylMode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isVinylMode,
                    onCheckedChange = onVinylModeChange,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd,
            ) {
                DebugRecompositionCounter(
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
            }
        }
    }
}

/**
 * Format duration in milliseconds to elapsed-time format.
 */
internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    return DateUtils.formatElapsedTime(totalSeconds)
}

internal fun formatPlaybackSpeedLabel(playbackSpeed: Float): String {
    val formattedSpeed =
        if (playbackSpeed % 1.0f == 0.0f) {
            playbackSpeed.toInt().toString()
        } else {
            val locale = java.util.Locale.getDefault()
            val isRussian = locale.language == "ru"
            val symbols =
                java.text.DecimalFormatSymbols(
                    if (isRussian) locale else java.util.Locale.US,
                )
            java.text.DecimalFormat("#.##", symbols).format(playbackSpeed)
        }
    return "${formattedSpeed}x"
}

@Composable
private fun DebugRecompositionCounter(modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }
    SideEffect { count += 1 }
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
