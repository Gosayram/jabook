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

import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.PowerManager
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.jabook.app.jabook.compose.core.theme.GlassmorphismTokens
import com.jabook.app.jabook.compose.core.theme.MotionTokens
import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors
import com.jabook.app.jabook.compose.core.theme.SurfaceElevationTokens
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.ContrastPolicy
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.core.util.HapticManager
import com.jabook.app.jabook.compose.core.util.UiFormatters
import com.jabook.app.jabook.compose.core.util.rememberReduceMotion
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.designsystem.component.CircularIconButton
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.feature.player.SquigglySlider
import com.jabook.app.jabook.compose.feature.player.lyrics.LyricsView
import com.jabook.app.jabook.compose.util.rememberClickDebouncer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
)
@Composable
public fun PlayerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val pitchCorrectionEnabled by viewModel.pitchCorrectionEnabled.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val lastSleepTimerDurationMinutes by viewModel.lastSleepTimerDurationMinutes.collectAsStateWithLifecycle()
    val normalizeEnabled by viewModel.normalizeChapterTitles.collectAsStateWithLifecycle()
    val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val visualizerWaveformDataRaw by viewModel.visualizerWaveformData.collectAsStateWithLifecycle()
    val seekbarWaveformDataRaw by viewModel.seekbarWaveformData.collectAsStateWithLifecycle()
    val nextBookAutoplayState by viewModel.nextBookAutoplayState.collectAsStateWithLifecycle()
    val visualizerMode by viewModel.visualizerMode.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    val abRepeatState by viewModel.abRepeatState.collectAsStateWithLifecycle()

    // Phased init (#59): defer heavy waveform data to avoid janky first frame
    var waveformReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameMillis { } // wait for the first frame to complete
        waveformReady = true
    }
    val emptyWaveform = remember { FloatArray(0) }
    val visualizerWaveformData = if (waveformReady) visualizerWaveformDataRaw else emptyWaveform
    val seekbarWaveformData = if (waveformReady) seekbarWaveformDataRaw else emptyWaveform
    val reduceMotion = rememberReduceMotion()
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
    var showChapterSheet by remember { mutableStateOf(false) }
    // Legacy settings sheet (if unused, we might want to consolidate or remove)
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var selectedRating by remember { mutableIntStateOf(0) }
    var ratedBookId by remember { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showStatsOverlay by remember { mutableStateOf(false) }
    var isBookmarkNoteSheetVisible by remember { mutableStateOf(false) }

    // Vinyl Mode State
    var isVinylMode by rememberSaveable { mutableStateOf(false) }
    var showBookmarkSheet by remember { mutableStateOf(false) }

    // Navigator for SupportingPaneScaffold
    val scaffoldNavigator = rememberSupportingPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
    val isCompactScreen =
        activity?.let {
            val rawWindowSizeClass = calculateWindowSizeClass(it)
            val windowSizeClass = AdaptiveUtils.resolveWindowSizeClass(rawWindowSizeClass, context)
            windowSizeClass.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
        } ?: true
    val openSettingsLabel = stringResource(R.string.openSettings)
    val notificationPermissionPlaybackHint = stringResource(R.string.notificationPermissionPlaybackHint)
    val audioVisualizerPermissionHint = stringResource(R.string.audioVisualizerPermissionHint)

    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnNavigateToBook by rememberUpdatedState(onNavigateToBook)

    // Sleep timer expiry haptic — double vibration when timer transitions from active to idle
    val sleepTimerMode = (uiState as? PlayerState.Active)?.sleepTimerMode
    var previousSleepTimerMode by remember { mutableStateOf(sleepTimerMode) }
    LaunchedEffect(sleepTimerMode) {
        val prev = previousSleepTimerMode
        val curr = sleepTimerMode
        previousSleepTimerMode = curr
        if (prev != null && prev != PlayerSleepTimerMode.IDLE && curr == PlayerSleepTimerMode.IDLE) {
            HapticManager.performLongPress(hapticFeedback)
            delay(100)
            HapticManager.performLongPress(hapticFeedback)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PlayerEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is PlayerEffect.ShowSnackbar -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = effect.actionLabel,
                        )
                    if (
                        result == androidx.compose.material3.SnackbarResult.ActionPerformed &&
                        effect.actionIntent != null
                    ) {
                        viewModel.dispatch(effect.actionIntent)
                    }
                }
                PlayerEffect.NavigateBack -> navigationClickGuard.run(currentOnNavigateBack)
                is PlayerEffect.NavigateToBook -> navigationClickGuard.run { currentOnNavigateToBook(effect.bookId) }
            }
        }
    }

    val completedBookId = (uiState as? PlayerState.Active)?.book?.takeIf { it.isCompleted }?.id
    LaunchedEffect(completedBookId) {
        if (completedBookId != null && ratedBookId != completedBookId && !showRatingDialog) {
            selectedRating = 0
            showRatingDialog = true
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

    // FLAG_SECURE: Prevent screenshots and screen recording on PlayerScreen
    // Protects copyrighted audiobook content
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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

    val requestNotificationPermissionForPlayback: () -> Unit = {
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
            PlayerPermissionPolicy.playbackPermissionsToRequest(
                sdkInt = android.os.Build.VERSION.SDK_INT,
                isNotificationPermissionGranted = notificationPermissionGranted,
            )
        val permissionsMissing =
            permissionsToRequest.filter {
                androidx.core.content.ContextCompat
                    .checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

        if (permissionsMissing.isNotEmpty()) {
            playerScreenLogger.d { "Requesting playback permissions: $permissionsMissing" }
            notificationPermissionsLauncher.launch(permissionsMissing.toTypedArray())
        }
    }

    // Back priority chain. ModalBottomSheets and the rating Dialog register their
    // own back handlers and win while shown. StatsOverlay is a plain
    // AnimatedVisibility with no handler, so it must be dismissed here first.
    // Then collapse the supporting chapters pane, then navigate back to library.
    // androidx.activity.compose.BackHandler already participates in Android 14+
    // predictive back; a finger-following pane animation would need
    // PredictiveBackHandler + a progress-driven partial expand (skipped — ceiling).
    androidx.activity.compose.BackHandler {
        when {
            showStatsOverlay -> showStatsOverlay = false
            scaffoldNavigator.canNavigateBack() -> {
                scope.launch {
                    scaffoldNavigator.navigateBack()
                }
            }
            else -> navigationClickGuard.run { currentOnNavigateBack() }
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
            lastUsedDurationMinutes = lastSleepTimerDurationMinutes,
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

    // Resume after long pause sheet (TASK-PLAYER-38)
    val resumeState by viewModel.resumeAfterLongPauseState.collectAsStateWithLifecycle()
    if (resumeState != null) {
        resumeState?.let { data ->
            JabookModalBottomSheet(
                onDismissRequest = { viewModel.dismissResumeAfterLongPause() },
            ) {
                ResumeAfterLongPauseSheet(
                    chapterName = data.chapterName,
                    chapterPosition = data.chapterPosition,
                    daysAgo = data.daysAgo,
                    onContinue = { viewModel.resumeAfterLongPauseContinue() },
                    onRestartChapter = { viewModel.resumeAfterLongPauseRestartChapter() },
                    onSelectChapter = {
                        viewModel.resumeAfterLongPauseSelectChapter()
                        showChapterSheet = true
                    },
                    onDismiss = { viewModel.dismissResumeAfterLongPause() },
                )
            }
        }
    }

    // Removed Chapter Selector Sheet - using adaptive pane instead
    if (showChapterSheet && uiState is PlayerState.Active) {
        val state = uiState as PlayerState.Active
        JabookModalBottomSheet(
            onDismissRequest = { showChapterSheet = false },
        ) {
            PlayerChapterPane(
                chapters = state.chapters,
                currentChapterIndex = state.currentChapterIndex,
                normalizeEnabled = normalizeEnabled,
                onChapterClick = { chapterIndex ->
                    viewModel.dispatch(PlayerIntent.SelectChapter(chapterIndex))
                    showChapterSheet = false
                },
            )
        }
    }

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

    // Player Overflow Menu Sheet
    if (showOverflowMenu && uiState is PlayerState.Active) {
        val state = uiState as PlayerState.Active
        JabookModalBottomSheet(
            onDismissRequest = { showOverflowMenu = false },
        ) {
            PlayerOverflowMenuSheet(
                isFavorite = state.book.isFavorite,
                onShareClick = {
                    val shareIntent =
                        android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                state.book.title + " by " + state.book.author,
                            )
                            type = "text/plain"
                        }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, null),
                    )
                },
                onToggleFavorite = {
                    viewModel.toggleFavorite()
                },
                onGoToBookClick = {
                    onNavigateToBook(state.book.id)
                },
                onBookmarksClick = {
                    HapticManager.performLongPress(hapticFeedback)
                    showBookmarkSheet = true
                },
                onStatsClick = { showStatsOverlay = true },
                onDismiss = { showOverflowMenu = false },
            )
        }
    }

    // Bookmarks Sheet
    if (showBookmarkSheet && uiState is PlayerState.Active) {
        val state = uiState as PlayerState.Active
        JabookModalBottomSheet(
            onDismissRequest = { showBookmarkSheet = false },
        ) {
            BookmarksSheet(
                bookmarks = state.bookmarks,
                currentPositionMs = state.currentPosition,
                chapters = state.chapters,
                currentChapterIndex = state.currentChapterIndex,
                onJumpToBookmark = { bookmark ->
                    viewModel.seekToBookmark(bookmark)
                },
                onDeleteBookmark = { bookmarkId ->
                    viewModel.deleteBookmark(bookmarkId)
                },
                onDismiss = { showBookmarkSheet = false },
            )
        }
    }

    // Player content

    // SupportingPaneScaffold for adaptive chapter display
    SupportingPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        value = scaffoldNavigator.scaffoldValue,
        mainPane = {
            AnimatedPane(modifier = Modifier) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        androidx.compose.material3.TopAppBar(
                            title = {
                                androidx.compose.material3.Text(
                                    text = stringResource(R.string.nowListening),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            },
                            navigationIcon = {
                                androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.backAction),
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { showStatsOverlay = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = stringResource(R.string.statsForNerds),
                                    )
                                }
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.playerOverflowMenu),
                                    )
                                }
                            },
                            colors =
                                androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                ),
                        )
                    },
                ) { padding ->
                    Box(
                        modifier =
                            Modifier
                                .padding(padding)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val shouldIgnoreShortcuts =
                                        showStatsOverlay ||
                                            showSpeedSheet ||
                                            showSleepTimerSheet ||
                                            showAudioSettingsSheet ||
                                            showChapterSheet ||
                                            showSettingsSheet ||
                                            showRatingDialog ||
                                            isBookmarkNoteSheetVisible ||
                                            showOverflowMenu
                                    if (shouldIgnoreShortcuts) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            val audioManager =
                                                context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                                            audioManager?.adjustMusicVolumeIfMutable(
                                                direction = AudioManager.ADJUST_RAISE,
                                                flags = AudioManager.FLAG_SHOW_UI,
                                            )
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            val audioManager =
                                                context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                                            audioManager?.adjustMusicVolumeIfMutable(
                                                direction = AudioManager.ADJUST_LOWER,
                                                flags = AudioManager.FLAG_SHOW_UI,
                                            )
                                            true
                                        }
                                        Key.B -> {
                                            val activeState = uiState as? PlayerState.Active ?: return@onPreviewKeyEvent false
                                            viewModel.addBookmarkAtPosition(
                                                chapterIndex = activeState.currentChapterIndex,
                                                positionMs = activeState.currentPosition,
                                            )
                                            true
                                        }
                                        Key.Escape -> {
                                            if (scaffoldNavigator.canNavigateBack()) {
                                                scope.launch { scaffoldNavigator.navigateBack() }
                                            } else {
                                                navigationClickGuard.run { onNavigateBack() }
                                            }
                                            true
                                        }
                                        else -> {
                                            val intent = mapKeyEventToPlayerIntent(keyEvent) ?: return@onPreviewKeyEvent false
                                            viewModel.dispatch(intent)
                                            true
                                        }
                                    }
                                },
                    ) {
                        val overlayHazeState = rememberHazeState()
                        AnimatedContent(
                            targetState = uiState,
                            transitionSpec = {
                                if (reduceMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (
                                        fadeIn(
                                            animationSpec =
                                                tween(
                                                    durationMillis = MotionTokens.MEDIUM1,
                                                    easing = MotionTokens.Emphasized,
                                                ),
                                        ) +
                                            scaleIn(
                                                initialScale = 0.98f,
                                                animationSpec =
                                                    tween(
                                                        durationMillis = MotionTokens.MEDIUM1,
                                                        easing = MotionTokens.Emphasized,
                                                    ),
                                            )
                                    ).togetherWith(
                                        fadeOut(
                                            animationSpec =
                                                tween(
                                                    durationMillis = MotionTokens.SHORT2,
                                                    easing = MotionTokens.EmphasizedDecelerate,
                                                ),
                                        ) +
                                            scaleOut(
                                                targetScale = 1.02f,
                                                animationSpec =
                                                    tween(
                                                        durationMillis = MotionTokens.SHORT2,
                                                        easing = MotionTokens.EmphasizedDecelerate,
                                                    ),
                                            ),
                                    )
                                }
                            },
                            contentKey = { state -> playerStateContentKey(state) },
                            label = "player_state_transition",
                        ) { animatedState ->
                            when (animatedState) {
                                is PlayerState.Loading -> {
                                    PlayerLoadingSkeleton(
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is PlayerState.Active -> {
                                    val state = animatedState

                                    // Click debouncer for preventing double clicks (inspired by Easybook)
                                    val clickDebouncer = rememberClickDebouncer(debounceTimeMs = 300)

                                    // Removed GestureOverlay as per user request to disable brightness/volume/seek swipes
                                    PremiumPlayerBackground(
                                        themeColors = state.themeColors,
                                        coverImageModel = CoverUtils.getCoverModel(state.book, context),
                                        hazeState = overlayHazeState,
                                        isPowerSaveMode = isPowerSaveMode,
                                    ) {
                                        PlayerContent(
                                            state = state,
                                            playbackSpeed = playbackSpeed,
                                            hazeState = overlayHazeState,
                                            isVinylMode = isVinylMode,
                                            sleepTimerState = sleepTimerState,
                                            normalizeEnabled = normalizeEnabled,
                                            chapterRepeatMode = state.chapterRepeatMode,
                                            visualizerWaveformData = visualizerWaveformData,
                                            seekbarWaveformData = seekbarWaveformData,
                                            abRepeatState = abRepeatState,
                                            onABRepeatClick = {
                                                HapticManager.performTap(hapticFeedback)
                                                viewModel.dispatch(PlayerIntent.ToggleABRepeat)
                                            },
                                            onPlayPause = {
                                                HapticManager.performLongPress(hapticFeedback)
                                                requestNotificationPermissionForPlayback()
                                                clickDebouncer.debounce {
                                                    viewModel.dispatch(PlayerIntent.TogglePlayPause)
                                                }
                                            },
                                            onSkipNext = {
                                                HapticManager.performGesture(hapticFeedback)
                                                clickDebouncer.debounce {
                                                    val activeState = uiState as? PlayerState.Active ?: return@debounce
                                                    when (
                                                        val action =
                                                            ChapterNavigationPolicy.resolveNextAction(
                                                                activeState.chapters,
                                                                activeState.currentChapterIndex,
                                                            )
                                                    ) {
                                                        is ChapterNavigationAction.JumpToChapter ->
                                                            viewModel.skipToChapter(
                                                                action.chapterIndex,
                                                            )
                                                        is ChapterNavigationAction.EndOfBook -> viewModel.dispatch(PlayerIntent.SkipNext)
                                                        is ChapterNavigationAction.RestartCurrentChapter ->
                                                            viewModel.skipToChapter(
                                                                action.chapterIndex,
                                                            )
                                                    }
                                                }
                                            },
                                            onSkipPrevious = {
                                                HapticManager.performGesture(hapticFeedback)
                                                clickDebouncer.debounce {
                                                    val activeState = uiState as? PlayerState.Active ?: return@debounce
                                                    when (
                                                        val action =
                                                            ChapterNavigationPolicy.resolvePreviousAction(
                                                                activeState.chapters,
                                                                activeState.currentChapterIndex,
                                                                activeState.currentPosition,
                                                            )
                                                    ) {
                                                        is ChapterNavigationAction.RestartCurrentChapter -> {
                                                            viewModel.skipToChapter(action.chapterIndex)
                                                            viewModel.seekTo(0L)
                                                        }
                                                        is ChapterNavigationAction.JumpToChapter ->
                                                            viewModel.skipToChapter(
                                                                action.chapterIndex,
                                                            )
                                                        is ChapterNavigationAction.EndOfBook ->
                                                            viewModel.dispatch(
                                                                PlayerIntent.SkipPrevious,
                                                            )
                                                    }
                                                }
                                            },
                                            onSeek = { positionMs ->
                                                viewModel.dispatch(PlayerIntent.SeekTo(positionMs))
                                            },
                                            onSeekForward = {
                                                HapticManager.performTap(hapticFeedback)
                                                clickDebouncer.debounce { viewModel.dispatch(PlayerIntent.SeekForward) }
                                            },
                                            onSeekBackward = {
                                                HapticManager.performTap(hapticFeedback)
                                                clickDebouncer.debounce { viewModel.dispatch(PlayerIntent.SeekBackward) }
                                            },
                                            onSelectChapter = { chapterIndex ->
                                                viewModel.dispatch(PlayerIntent.SelectChapter(chapterIndex))
                                            },
                                            onChapterClick = {
                                                // Phone: bottom sheet, larger screens: supporting pane.
                                                clickDebouncer.debounce {
                                                    if (isCompactScreen) {
                                                        showChapterSheet = true
                                                    } else {
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
                                                }
                                            },
                                            onSpeedClick = {
                                                HapticManager.performLongPress(hapticFeedback)
                                                showSpeedSheet = true
                                            },
                                            onHoldToBoostStart = {
                                                HapticManager.performLongPress(hapticFeedback)
                                                viewModel.startHoldToBoost(playbackSpeed)
                                            },
                                            onHoldToBoostEnd = {
                                                viewModel.endHoldToBoost()
                                            },
                                            onAudioSettingsClick = { showAudioSettingsSheet = true },
                                            onSleepTimerClick = {
                                                HapticManager.performTap(hapticFeedback)
                                                showSleepTimerSheet = true
                                            },
                                            onChapterRepeatClick = {
                                                HapticManager.performTap(hapticFeedback)
                                                clickDebouncer.debounce {
                                                    viewModel.dispatch(PlayerIntent.ToggleChapterRepeat)
                                                }
                                            },
                                            onBookmarksClick = {
                                                HapticManager.performLongPress(hapticFeedback)
                                                showBookmarkSheet = true
                                            },
                                            onStatsClick = { showStatsOverlay = true },
                                            onAddBookmarkAtPosition = { chapterIndex, positionMs, onCreated ->
                                                viewModel.addBookmarkAtPosition(
                                                    chapterIndex = chapterIndex,
                                                    positionMs = positionMs,
                                                    onCreated = onCreated,
                                                )
                                            },
                                            onUpdateBookmark = { bookmarkId, noteText, noteAudioPath ->
                                                viewModel.updateBookmarkContent(
                                                    bookmarkId = bookmarkId,
                                                    noteText = noteText,
                                                    noteAudioPath = noteAudioPath,
                                                )
                                            },
                                            onDeleteBookmark = { bookmarkId ->
                                                viewModel.deleteBookmark(bookmarkId)
                                            },
                                            hasRecordAudioPermission = hasRecordAudioPermission,
                                            onRequestRecordAudioPermission = requestRecordAudioPermission,
                                            onInitializeVisualizer = {
                                                viewModel.dispatch(PlayerIntent.InitializeVisualizer)
                                            },
                                            onSetVisualizerEnabled = { enabled ->
                                                viewModel.dispatch(PlayerIntent.SetVisualizerEnabled(enabled))
                                            },
                                            visualizerMode = visualizerMode,
                                            onVisualizerModeCycle = {
                                                viewModel.dispatch(PlayerIntent.CycleVisualizerMode)
                                            },
                                            onBookmarkNoteSheetVisibilityChanged = { isBookmarkNoteSheetVisible = it },
                                            snackbarHostState = snackbarHostState,
                                            modifier = Modifier.hazeEffect(state = overlayHazeState),
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                }
                                is PlayerState.Error -> {
                                    val state = animatedState
                                    ErrorScreen(
                                        message = state.message,
                                    )
                                }
                            }
                        }

                        nextBookAutoplayState?.let { autoplayState ->
                            NextBookCountdownCard(
                                book = autoplayState.nextBook,
                                secondsLeft = autoplayState.secondsLeft,
                                totalSeconds = autoplayState.totalSeconds,
                                onContinue = viewModel::continueSeriesNow,
                                onDismiss = viewModel::dismissSeriesAutoplay,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .hazeEffect(state = overlayHazeState),
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showStatsOverlay,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            StatsOverlay(
                                stats = playerStats,
                                onDismiss = { showStatsOverlay = false },
                            )
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

    if (showRatingDialog) {
        RatingDialog(
            selectedRating = selectedRating,
            onDismiss = {
                ratedBookId = (uiState as? PlayerState.Active)?.book?.id
                selectedRating = 0
                showRatingDialog = false
            },
            onRate = { rating ->
                selectedRating = rating
                ratedBookId = (uiState as? PlayerState.Active)?.book?.id
                showRatingDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.rateCompletedBookThanks, rating),
                    )
                }
            },
        )
    }
}

@Composable
internal fun StarRatingRow(
    selected: Int,
    onRate: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { star ->
            IconButton(
                onClick = { onRate(star) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (star <= selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(R.string.rateCompletedBookStar, star),
                    tint =
                        if (star <= selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(if (star <= selected) 32.dp else 28.dp),
                )
            }
        }
    }
}

@Composable
internal fun RatingDialog(
    selectedRating: Int,
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.rateCompletedBookTitle)) },
        text = {
            StarRatingRow(
                selected = selectedRating,
                onRate = onRate,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.laterAction))
            }
        },
    )
}

@Composable
private fun NextBookCountdownCard(
    book: com.jabook.app.jabook.compose.domain.model.Book,
    secondsLeft: Int,
    totalSeconds: Int,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = GlassmorphismTokens.PLAYER_CONTROLS_TINT_ALPHA,
                    ),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = SurfaceElevationTokens.Level2),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = CoverUtils.getCoverModel(book, LocalContext.current),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.continueSeriesLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = {
                        val total = totalSeconds.coerceAtLeast(1)
                        secondsLeft.coerceIn(0, total) / total.toFloat()
                    },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                )
                Text(text = secondsLeft.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismissAction))
            }
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.playNowAction))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerLandscapeLayout(
    state: PlayerState.Active,
    isCompact: Boolean,
    showingLyrics: Boolean,
    showLyrics: (Boolean) -> Unit,
    isVinylMode: Boolean,
    swipeOffsetX: Float,
    isSwiping: Boolean,
    displayAuthor: String,
    adaptiveOnSurface: androidx.compose.ui.graphics.Color,
    adaptiveOnSurfaceVariant: androidx.compose.ui.graphics.Color,
    themeColors: PlayerThemeColors?,
    hasLyrics: Boolean,
    abRepeatState: ABRepeatState,
    onABRepeatClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onChapterClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onHoldToBoostStart: () -> Unit,
    onHoldToBoostEnd: () -> Unit,
    onAudioSettingsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onChapterRepeatClick: () -> Unit,
    onStatsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onAddBookmarkAtPosition: (Int, Long, (com.jabook.app.jabook.compose.domain.model.BookmarkItem?) -> Unit) -> Unit,
    onUpdateBookmark: (String, String?, String?) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    hasRecordAudioPermission: Boolean,
    onRequestRecordAudioPermission: () -> Unit,
    onInitializeVisualizer: () -> Unit,
    onSetVisualizerEnabled: (Boolean) -> Unit,
    visualizerMode: Int,
    onVisualizerModeCycle: () -> Unit,
    onBookmarkNoteSheetVisibilityChanged: (Boolean) -> Unit,
    onSwipeOpenChapterList: () -> Unit,
    onSwipeNavigateBack: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope?,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope?,
) {
    val seekScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val chapterTimeline by remember(state.chapters, state.currentChapterIndex, state.currentPosition) {
        derivedStateOf {
            ChapterSeekbarPolicy.buildTimeline(
                chapters = state.chapters,
                currentChapterIndex = state.currentChapterIndex,
                currentChapterPositionMs = state.currentPosition.coerceAtLeast(0L),
            )
        }
    }
    val bookmarkMarkersFractions by remember(state.bookmarks, state.chapters) {
        derivedStateOf {
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = state.bookmarks,
                chapters = state.chapters,
            )
        }
    }
    val playerProgress by remember(chapterTimeline) { derivedStateOf { chapterTimeline.progress } }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var pendingSeekPosition by remember { mutableStateOf<Float?>(null) }
    var delayedSeekGeneration by remember { mutableStateOf(0L) }
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
    val abRepeatFractions by remember(abRepeatState, chapterTimeline.totalDurationMs) {
        derivedStateOf {
            if (abRepeatState.phase == ABRepeatPhase.ACTIVE && chapterTimeline.totalDurationMs > 0L) {
                Pair(
                    (abRepeatState.pointA.toFloat() / chapterTimeline.totalDurationMs.toFloat()).coerceIn(0f, 1f),
                    (abRepeatState.pointB.toFloat() / chapterTimeline.totalDurationMs.toFloat()).coerceIn(0f, 1f),
                )
            } else {
                null
            }
        }
    }
    val previewSeekTarget by remember(state.chapters, displayedProgress) {
        derivedStateOf {
            ChapterSeekbarPolicy.resolveSeekTarget(chapters = state.chapters, progress = displayedProgress)
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
                (displayedProgress.coerceIn(0f, 1f) * chapterTimeline.totalDurationMs.toFloat()).toLong()
            } else {
                chapterTimeline.globalPositionMs
            }
        }
    }

    LaunchedEffect(playerProgress, chapterTimeline.totalDurationMs) {
        coalescedPlayerProgress =
            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                previousProgress = coalescedPlayerProgress,
                incomingProgress = playerProgress,
                totalDurationMs = chapterTimeline.totalDurationMs,
            )
    }

    LaunchedEffect(playerProgress, pendingSeekPosition, isDragging) {
        if (!isDragging && pendingSeekPosition != null) {
            val result =
                SliderSeekSyncPolicy.resolveFromPlayerProgress(
                    playerProgress = playerProgress,
                    currentSliderPosition = pendingSeekPosition ?: playerProgress,
                    isDragging = false,
                    awaitingSeekSync = true,
                )
            if (!result.awaitingSeekSync) pendingSeekPosition = null
        }
    }

    LaunchedEffect(chapterTimeline.totalDurationMs, state.currentChapterIndex) {
        if (!isDragging) {
            coalescedPlayerProgress = playerProgress
            pendingSeekPosition = null
        }
    }

    LaunchedEffect(pendingSeekPosition) {
        if (pendingSeekPosition != null) {
            delay(1500L)
            pendingSeekPosition = null
        }
    }

    val playbackPositionLabel = stringResource(R.string.playbackPositionLabel)
    val sliderValueFormatter =
        remember(chapterTimeline.totalDurationMs) {
            ValueFormatter { progressValue: Float ->
                val clamped = progressValue.coerceIn(0f, 1f)
                formatDuration((chapterTimeline.totalDurationMs * clamped).toLong())
            }
        }
    val seekBackwardActionLabel = stringResource(R.string.seekBackwardDescription, state.rewindInterval)
    val seekForwardActionLabel = stringResource(R.string.seekForwardDescription, state.forwardInterval)

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(0.4f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
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
            val imageRequest =
                CoverUtils
                    .createCoverImageRequest(
                        book = state.book,
                        context = context,
                        placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
                        errorColor = MaterialTheme.colorScheme.error,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                        cornerRadius = 16f,
                    ).build()
            val coverScale = 0.85f
            AsyncImage(
                model = imageRequest,
                contentDescription = stringResource(R.string.playerCoverAccessibilityDescription, state.book.title, state.book.author),
                contentScale = ContentScale.Crop,
                modifier =
                    imageModifier
                        .fillMaxWidth(coverScale)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .combinedClickable(
                            onClick = { },
                            onDoubleClick = onStatsClick,
                        ),
            )
        }

        Column(
            modifier = Modifier.weight(0.6f).fillMaxHeight().padding(end = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.book.title,
                style = MaterialTheme.typography.titleLarge,
                color = adaptiveOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            AssistChip(
                onClick = onChapterClick,
                label = { Text(text = stringResource(R.string.chapterOf, state.currentChapterIndex + 1, state.chapters.size)) },
                modifier = Modifier.semantics { role = androidx.compose.ui.semantics.Role.Button },
            )

            Spacer(modifier = Modifier.height(16.dp))

            SquigglySlider(
                value = displayedProgress,
                onValueChange = { newProgress ->
                    delayedSeekGeneration++
                    pendingSeekPosition = null
                    val constrainedProgress = newProgress.coerceIn(0f, 1f)
                    val shouldTriggerHaptic =
                        lastSliderHapticProgress == null ||
                            kotlin.math.abs(constrainedProgress - (lastSliderHapticProgress ?: constrainedProgress)) >= 0.05f
                    if (shouldTriggerHaptic) {
                        HapticManager.performTap(hapticFeedback)
                        lastSliderHapticProgress = constrainedProgress
                    }
                    dragPosition = constrainedProgress
                },
                onValueChangeFinished = {
                    val seekGeneration = ++delayedSeekGeneration
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
                                if (
                                    DelayedSliderSeekPolicy.shouldDispatch(
                                        seekGeneration,
                                        delayedSeekGeneration,
                                    )
                                ) {
                                    onSeek(target.chapterPositionMs)
                                }
                            }
                        } else {
                            onSeek(target.chapterPositionMs)
                        }
                    }
                    dragPosition = null
                    lastSliderHapticProgress = null
                },
                onLongPress = { pressedProgress ->
                    if (chapterTimeline.totalDurationMs <= 0) return@SquigglySlider
                    val target =
                        ChapterSeekbarPolicy.resolveSeekTarget(
                            chapters = state.chapters,
                            progress = pressedProgress.coerceIn(0f, 1f),
                        )
                    HapticManager.performTap(hapticFeedback)
                    onAddBookmarkAtPosition(target.chapterIndex, target.chapterPositionMs) { }
                },
                isPlaying = state.isPlaying,
                chapterMarkersFractions = chapterTimeline.chapterMarkersFractions,
                bookmarkMarkersFractions = bookmarkMarkersFractions,
                abRepeatRange = abRepeatFractions,
                waveformData = FloatArray(0),
                activeTrackColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                inactiveTrackColor = (themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.24f),
                valueFormatter = sliderValueFormatter,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics {
                            contentDescription = playbackPositionLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(displayedProgress, 0f..1f)
                        },
            )

            val elapsedFormatted = formatDuration(currentGlobalPositionMs)
            val totalFormatted = formatDuration(chapterTimeline.totalDurationMs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = elapsedFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = adaptiveOnSurfaceVariant,
                )
                Text(
                    text = totalFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = adaptiveOnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.previousChapter),
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(if (isCompact) 56.dp else 64.dp),
                    size = if (isCompact) 40.dp else 48.dp,
                )
                CircularIconButton(
                    icon = Icons.Filled.Replay,
                    contentDescription = stringResource(R.string.seekBackwardDescription, state.rewindInterval),
                    onClick = onSeekBackward,
                    modifier = Modifier.size(if (isCompact) 48.dp else 56.dp),
                    size = if (isCompact) 32.dp else 40.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                val playPauseSize = if (isCompact) 72.dp else 80.dp
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(playPauseSize * 1.2f)) {
                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
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
                                    stringResource(
                                        R.string.pauseButton,
                                    )
                                } else {
                                    stringResource(R.string.playButton)
                                },
                            modifier = Modifier.size(if (isCompact) 40.dp else 48.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                CircularIconButton(
                    icon = Icons.Filled.FastForward,
                    contentDescription = stringResource(R.string.seekForwardDescription, state.forwardInterval),
                    onClick = onSeekForward,
                    modifier = Modifier.size(if (isCompact) 48.dp else 56.dp),
                    size = if (isCompact) 32.dp else 40.dp,
                )
                CircularIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.nextChapter),
                    onClick = onSkipNext,
                    modifier = Modifier.size(if (isCompact) 56.dp else 64.dp),
                    size = if (isCompact) 40.dp else 48.dp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val controlButtonHeight = if (isCompact) 44.dp else 52.dp
            val controlButtonIconSize = if (isCompact) 20.dp else 22.dp
            val controlButtonTextSize = if (isCompact) 14.sp else 16.sp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FilledTonalButton(
                    onClick = onSpeedClick,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                ) {
                    Icon(Icons.Filled.Speed, stringResource(R.string.playbackSpeedTitle), Modifier.size(controlButtonIconSize))
                }
                FilledTonalButton(
                    onClick = onAudioSettingsClick,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                ) {
                    Icon(Icons.Filled.Tune, stringResource(R.string.audioSettingsTitle), Modifier.size(controlButtonIconSize))
                }
                FilledTonalButton(
                    onClick = onChapterRepeatClick,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                ) {
                    when (state.chapterRepeatMode) {
                        ChapterRepeatMode.INFINITE -> Text("∞", fontSize = 14.sp)
                        ChapterRepeatMode.OFF ->
                            Icon(
                                Icons.Outlined.Repeat,
                                stringResource(R.string.noRepeat),
                                Modifier.size(controlButtonIconSize),
                            )
                        ChapterRepeatMode.ONCE ->
                            Icon(
                                Icons.Filled.RepeatOne,
                                stringResource(R.string.repeatTrack),
                                Modifier.size(controlButtonIconSize),
                            )
                    }
                }
                FilledTonalButton(
                    onClick = onABRepeatClick,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor =
                                when (abRepeatState.phase) {
                                    ABRepeatPhase.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                                    ABRepeatPhase.A_SET -> MaterialTheme.colorScheme.tertiaryContainer
                                    ABRepeatPhase.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                                },
                        ),
                ) {
                    when (abRepeatState.phase) {
                        ABRepeatPhase.INACTIVE ->
                            Text(
                                "A B",
                                fontSize = controlButtonTextSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        ABRepeatPhase.A_SET ->
                            Text(
                                "A",
                                fontSize = controlButtonTextSize,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        ABRepeatPhase.ACTIVE ->
                            Text(
                                "A→B",
                                fontSize = controlButtonTextSize,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                    }
                }
                FilledTonalButton(
                    onClick = onSleepTimerClick,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                ) {
                    Icon(Icons.Outlined.Timer, stringResource(R.string.sleepTimer), Modifier.size(controlButtonIconSize))
                }
                FilledTonalButton(
                    onClick = onBookmarksClick,
                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                ) {
                    Icon(
                        if (state.bookmarks.isNotEmpty()) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                        stringResource(R.string.bookmarks),
                        Modifier.size(controlButtonIconSize),
                    )
                }
                if (hasLyrics) {
                    FilledTonalButton(
                        onClick = { showLyrics(!showingLyrics) },
                        modifier = Modifier.weight(1f).height(controlButtonHeight),
                    ) {
                        Icon(
                            if (showingLyrics) Icons.Filled.Description else Icons.Outlined.Description,
                            stringResource(R.string.lyrics),
                            Modifier.size(controlButtonIconSize),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
)
@RequiresApi(android.os.Build.VERSION_CODES.S)
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
    seekbarWaveformData: FloatArray,
    abRepeatState: ABRepeatState = ABRepeatState(),
    onABRepeatClick: () -> Unit = {},
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onChapterClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onHoldToBoostStart: () -> Unit,
    onHoldToBoostEnd: () -> Unit,
    onAudioSettingsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onChapterRepeatClick: () -> Unit,
    onStatsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onAddBookmarkAtPosition: (Int, Long, (com.jabook.app.jabook.compose.domain.model.BookmarkItem?) -> Unit) -> Unit,
    onUpdateBookmark: (String, String?, String?) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    hasRecordAudioPermission: Boolean,
    onRequestRecordAudioPermission: () -> Unit,
    onInitializeVisualizer: () -> Unit,
    onSetVisualizerEnabled: (Boolean) -> Unit,
    visualizerMode: Int,
    onVisualizerModeCycle: () -> Unit,
    onBookmarkNoteSheetVisibilityChanged: (Boolean) -> Unit = {},
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    modifier: Modifier = Modifier,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val currentOnHoldToBoostStart by rememberUpdatedState(onHoldToBoostStart)
    val currentOnHoldToBoostEnd by rememberUpdatedState(onHoldToBoostEnd)
    val currentOnInitializeVisualizer by rememberUpdatedState(onInitializeVisualizer)
    val currentOnSetVisualizerEnabled by rememberUpdatedState(onSetVisualizerEnabled)
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
    val playPauseButtonScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1.0f else 0.94f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "play_pause_button_scale",
    )
    val playPauseIconScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1.0f else 0.92f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "play_pause_icon_scale",
    )
    // Adaptive sizes for control buttons (Speed, Repeat, Timer) - increased for better ergonomics
    val controlButtonHeight = if (isCompact) 48.dp else 56.dp
    val controlButtonIconSize = if (isCompact) 22.dp else 24.dp
    val controlButtonTextSize =
        if (isCompact) 14.sp else 16.sp
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
    val speedButtonInteractionSource = remember { MutableInteractionSource() }
    val speedButtonPressed by speedButtonInteractionSource.collectIsPressedAsState()
    var holdToBoostActivated by remember { mutableStateOf(false) }
    var suppressNextSpeedClick by remember { mutableStateOf(false) }

    LaunchedEffect(speedButtonPressed) {
        if (speedButtonPressed) {
            delay(HOLD_TO_BOOST_ACTIVATION_DELAY_MS)
            if (speedButtonPressed && !holdToBoostActivated) {
                holdToBoostActivated = true
                suppressNextSpeedClick = true
                currentOnHoldToBoostStart()
            }
        } else if (holdToBoostActivated) {
            holdToBoostActivated = false
            currentOnHoldToBoostEnd()
        }
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

    val displayAuthor = authorFromMetadata ?: state.book.author
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
    var showBookmarkNoteSheet by remember { mutableStateOf(false) }
    var showBookmarkMomentStamp by remember { mutableStateOf(false) }
    var pendingBookmarkId by remember { mutableStateOf<String?>(null) }
    var pendingBookmarkNote by remember { mutableStateOf("") }
    var pendingBookmarkAudioPath by remember { mutableStateOf<String?>(null) }
    var isRecordingBookmark by remember { mutableStateOf(false) }
    var isPlayingBookmarkAudio by remember { mutableStateOf(false) }
    val bookmarkRecorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val bookmarkPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val bookmarkRecordTimeoutJob = remember { mutableStateOf<Job?>(null) }

    // Release MediaRecorder and MediaPlayer when composable leaves composition (#40)
    DisposableEffect(Unit) {
        onDispose {
            bookmarkRecordTimeoutJob.value?.cancel()
            bookmarkRecordTimeoutJob.value = null
            bookmarkRecorder.value?.runCatching {
                stop()
                release()
            }
            bookmarkRecorder.value = null
            bookmarkPlayer.value?.runCatching {
                stop()
                release()
            }
            bookmarkPlayer.value = null
        }
    }

    SideEffect { onBookmarkNoteSheetVisibilityChanged(showBookmarkNoteSheet) }

    var lastChapterBoundaryIndex by remember(state.book.id) { mutableIntStateOf(state.currentChapterIndex) }
    var skipTriggeredHaptic by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentChapterIndex) {
        resolveChapterBoundaryHapticDecision(
            previousChapterIndex = lastChapterBoundaryIndex,
            newChapterIndex = state.currentChapterIndex,
            skipTriggeredHaptic = skipTriggeredHaptic,
        )?.let { decision ->
            if (decision.shouldPerformHaptic) {
                HapticManager.performGesture(hapticFeedback)
            }
            skipTriggeredHaptic = decision.nextSkipTriggeredHaptic
            lastChapterBoundaryIndex = decision.nextLastChapterBoundaryIndex
        }
    }

    // Dynamic Theme Background with Glassmorphism Effect
    // Background is now handled by PremiumPlayerBackground wrapping this content
    val themeColors = state.themeColors
    val contrastBackground = themeColors?.surfaceColor ?: MaterialTheme.colorScheme.surface
    val adaptiveOnSurface =
        remember(contrastBackground) {
            ContrastPolicy.preferredOnColor(contrastBackground)
        }
    val adaptiveOnSurfaceVariant =
        remember(adaptiveOnSurface) {
            adaptiveOnSurface.copy(alpha = 0.82f)
        }

    // Swipe gesture state
    var swipeOffsetX by remember { mutableStateOf(0f) }
    var isSwiping by remember { mutableStateOf(false) }

    // Orientation-aware layout: landscape = Row (cover left, controls right)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Main Content
    // We use a Box to contain the LazyColumn (and potential overlays like visualizer if moved, but visualizer is inside list)
    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            PlayerLandscapeLayout(
                state = state,
                isCompact = isCompact,
                showingLyrics = showingLyrics,
                showLyrics = { showLyrics = it },
                isVinylMode = isVinylMode,
                swipeOffsetX = swipeOffsetX,
                isSwiping = isSwiping,
                displayAuthor = displayAuthor,
                adaptiveOnSurface = adaptiveOnSurface,
                adaptiveOnSurfaceVariant = adaptiveOnSurfaceVariant,
                themeColors = themeColors,
                hasLyrics = hasLyrics,
                abRepeatState = abRepeatState,
                onABRepeatClick = onABRepeatClick,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeek = onSeek,
                onSeekForward = onSeekForward,
                onSeekBackward = onSeekBackward,
                onSelectChapter = onSelectChapter,
                onChapterClick = onChapterClick,
                onSpeedClick = onSpeedClick,
                onHoldToBoostStart = onHoldToBoostStart,
                onHoldToBoostEnd = onHoldToBoostEnd,
                onAudioSettingsClick = onAudioSettingsClick,
                onSleepTimerClick = onSleepTimerClick,
                onChapterRepeatClick = onChapterRepeatClick,
                onStatsClick = onStatsClick,
                onBookmarksClick = onBookmarksClick,
                onAddBookmarkAtPosition = onAddBookmarkAtPosition,
                onUpdateBookmark = onUpdateBookmark,
                onDeleteBookmark = onDeleteBookmark,
                hasRecordAudioPermission = hasRecordAudioPermission,
                onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                onInitializeVisualizer = onInitializeVisualizer,
                onSetVisualizerEnabled = onSetVisualizerEnabled,
                visualizerMode = visualizerMode,
                onVisualizerModeCycle = onVisualizerModeCycle,
                onBookmarkNoteSheetVisibilityChanged = onBookmarkNoteSheetVisibilityChanged,
                onSwipeOpenChapterList = {},
                onSwipeNavigateBack = {},
                snackbarHostState = snackbarHostState,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        } else {
            // Existing portrait layout
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
                if (!isCompact) {
                    item {
                        Text(
                            text = displayAuthor,
                            style = MaterialTheme.typography.bodyLarge,
                            color = adaptiveOnSurfaceVariant,
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
                            contentDescription =
                                stringResource(
                                    R.string.playerCoverAccessibilityDescription,
                                    state.book.title,
                                    state.book.author,
                                ),
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
                                    }.combinedClickable(
                                        onClick = { if (canToggleLyrics) showLyrics = !showLyrics },
                                        onDoubleClick = onStatsClick,
                                        onClickLabel = toggleLyricsLabel,
                                    ),
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
                            color = adaptiveOnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Chapter chip (clickable, opens chapter sheet)
                item {
                    androidx.compose.material3.AssistChip(
                        onClick = onChapterClick,
                        label = {
                            Text(
                                text =
                                    stringResource(
                                        R.string.chapterOf,
                                        state.currentChapterIndex + 1,
                                        state.chapters.size,
                                    ),
                            )
                        },
                        modifier = Modifier.semantics { role = androidx.compose.ui.semantics.Role.Button },
                    )
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

                        // Calculate bookmark marker fractions from bookmarks
                        val bookmarkMarkersFractions by remember(
                            state.bookmarks,
                            state.chapters,
                        ) {
                            derivedStateOf {
                                BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                                    bookmarks = state.bookmarks,
                                    chapters = state.chapters,
                                )
                            }
                        }
                        val abRepeatFractions by remember(abRepeatState, chapterTimeline.totalDurationMs) {
                            derivedStateOf {
                                if (abRepeatState.phase == ABRepeatPhase.ACTIVE && chapterTimeline.totalDurationMs > 0L) {
                                    Pair(
                                        (abRepeatState.pointA.toFloat() / chapterTimeline.totalDurationMs.toFloat()).coerceIn(0f, 1f),
                                        (abRepeatState.pointB.toFloat() / chapterTimeline.totalDurationMs.toFloat()).coerceIn(0f, 1f),
                                    )
                                } else {
                                    null
                                }
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
                        var delayedSeekGeneration by remember { mutableStateOf(0L) }
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
                        val sliderValueFormatter =
                            remember(chapterTimeline.totalDurationMs) {
                                ValueFormatter { progressValue: Float ->
                                    val clamped = progressValue.coerceIn(0f, 1f)
                                    formatDuration((chapterTimeline.totalDurationMs * clamped).toLong())
                                }
                            }
                        val seekBackwardActionLabel =
                            stringResource(R.string.seekBackwardDescription, state.rewindInterval)
                        val seekForwardActionLabel =
                            stringResource(R.string.seekForwardDescription, state.forwardInterval)

                        SquigglySlider(
                            value = displayedProgress,
                            onValueChange = { newProgress ->
                                delayedSeekGeneration++
                                pendingSeekPosition = null
                                val constrainedProgress = newProgress.coerceIn(0f, 1f)
                                val shouldTriggerHaptic =
                                    lastSliderHapticProgress == null ||
                                        kotlin.math.abs(constrainedProgress - (lastSliderHapticProgress ?: constrainedProgress)) >=
                                        0.05f
                                if (shouldTriggerHaptic) {
                                    HapticManager.performTap(sliderHaptic)
                                    lastSliderHapticProgress = constrainedProgress
                                }
                                dragPosition = constrainedProgress
                            },
                            onValueChangeFinished = {
                                // Seek only when user finishes dragging
                                val seekGeneration = ++delayedSeekGeneration
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
                                            if (
                                                DelayedSliderSeekPolicy.shouldDispatch(
                                                    seekGeneration,
                                                    delayedSeekGeneration,
                                                )
                                            ) {
                                                onSeek(target.chapterPositionMs)
                                            }
                                        }
                                    } else {
                                        onSeek(target.chapterPositionMs)
                                    }
                                }
                                dragPosition = null
                                lastSliderHapticProgress = null
                            },
                            onLongPress = { pressedProgress ->
                                if (chapterTimeline.totalDurationMs <= 0) return@SquigglySlider
                                val target =
                                    ChapterSeekbarPolicy.resolveSeekTarget(
                                        chapters = state.chapters,
                                        progress = pressedProgress.coerceIn(0f, 1f),
                                    )
                                HapticManager.performTap(hapticFeedback)
                                onAddBookmarkAtPosition(target.chapterIndex, target.chapterPositionMs) { createdBookmark ->
                                    if (createdBookmark != null) {
                                        showBookmarkMomentStamp = true
                                        seekScope.launch {
                                            delay(700L)
                                            showBookmarkMomentStamp = false
                                        }
                                        seekScope.launch {
                                            val result =
                                                snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.bookmarkAddedMessage),
                                                    actionLabel = context.getString(R.string.undoAction),
                                                    duration = androidx.compose.material3.SnackbarDuration.Short,
                                                )
                                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                onDeleteBookmark(createdBookmark.id)
                                                pendingBookmarkId = null
                                                pendingBookmarkNote = ""
                                                pendingBookmarkAudioPath = null
                                                showBookmarkNoteSheet = false
                                            }
                                        }
                                        pendingBookmarkId = createdBookmark.id
                                        pendingBookmarkNote = createdBookmark.noteText.orEmpty()
                                        pendingBookmarkAudioPath = createdBookmark.noteAudioPath
                                        showBookmarkNoteSheet = true
                                    }
                                }
                            },
                            isPlaying = state.isPlaying,
                            chapterMarkersFractions = chapterTimeline.chapterMarkersFractions,
                            bookmarkMarkersFractions = bookmarkMarkersFractions,
                            abRepeatRange = abRepeatFractions,
                            waveformData = seekbarWaveformData,
                            activeTrackColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                            inactiveTrackColor =
                                (themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary).copy(
                                    alpha = 0.24f,
                                ),
                            valueFormatter = sliderValueFormatter,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .semantics {
                                        contentDescription = playbackPositionLabel
                                        val current = formatDuration(currentGlobalPositionMs)
                                        val total = formatDuration(chapterTimeline.totalDurationMs)
                                        stateDescription = "$current of $total"
                                        progressBarRangeInfo = ProgressBarRangeInfo(displayedProgress, 0f..1f)
                                        setProgress { targetProgress ->
                                            if (chapterTimeline.totalDurationMs <= 0) return@setProgress false
                                            val seekGeneration = ++delayedSeekGeneration
                                            val target =
                                                ChapterSeekbarPolicy.resolveSeekTarget(
                                                    chapters = state.chapters,
                                                    progress = targetProgress.coerceIn(0f, 1f),
                                                )
                                            if (target.chapterIndex != state.currentChapterIndex) {
                                                onSelectChapter(target.chapterIndex)
                                                seekScope.launch {
                                                    delay(80L)
                                                    if (
                                                        DelayedSliderSeekPolicy.shouldDispatch(
                                                            seekGeneration,
                                                            delayedSeekGeneration,
                                                        )
                                                    ) {
                                                        onSeek(target.chapterPositionMs)
                                                    }
                                                }
                                            } else {
                                                onSeek(target.chapterPositionMs)
                                            }
                                            true
                                        }
                                        customActions =
                                            listOf(
                                                CustomAccessibilityAction(
                                                    label = seekBackwardActionLabel,
                                                ) {
                                                    onSeekBackward()
                                                    true
                                                },
                                                CustomAccessibilityAction(
                                                    label = seekForwardActionLabel,
                                                ) {
                                                    onSeekForward()
                                                    true
                                                },
                                            )
                                    },
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showBookmarkMomentStamp,
                            enter = fadeIn() + scaleIn(initialScale = 0.6f),
                            exit = fadeOut() + scaleOut(targetScale = 1.3f),
                            modifier =
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (isDragging) {
                            val previewTitle =
                                state.chapters
                                    .getOrNull(previewSeekTarget.chapterIndex)
                                    ?.title
                                    .orEmpty()
                            Text(
                                text = "${previewSeekTarget.chapterIndex + 1}. $previewTitle",
                                style = MaterialTheme.typography.labelMedium,
                                color = adaptiveOnSurfaceVariant,
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Time labels (tabular figures so digits don't jump)
                        val elapsedFormatted = formatDuration(currentGlobalPositionMs)
                        val totalFormatted = formatDuration(chapterTimeline.totalDurationMs)
                        val elapsedAccessibility = stringResource(R.string.elapsedTimeDescription, elapsedFormatted)
                        val totalAccessibility = stringResource(R.string.totalDurationDescription, totalFormatted)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = elapsedFormatted,
                                style =
                                    (if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)
                                        .copy(fontFeatureSettings = "tnum"),
                                color = adaptiveOnSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = elapsedAccessibility },
                            )

                            Text(
                                text = totalFormatted,
                                style =
                                    (if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)
                                        .copy(fontFeatureSettings = "tnum"),
                                color = adaptiveOnSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = totalAccessibility },
                            )
                        }

// Smart Info (Chapter index & Time remaining)
                        val chapterText =
                            stringResource(
                                R.string.chapterOf,
                                state.currentChapterIndex + 1,
                                state.chapters.size,
                            )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            val remainingText by remember(
                                chapterTimeline.totalDurationMs,
                                currentGlobalPositionMs,
                                state.playbackSpeed,
                            ) {
                                derivedStateOf {
                                    val remainingMs = (chapterTimeline.totalDurationMs - currentGlobalPositionMs).coerceAtLeast(0L)
                                    val speed = state.playbackSpeed
                                    val realRemainingMs = if (speed > 0f) (remainingMs / speed).toLong() else remainingMs
                                    if (realRemainingMs > 0L) {
                                        "-${UiFormatters.formatDurationCompact(realRemainingMs)}"
                                    } else {
                                        ""
                                    }
                                }
                            }

                            Text(
                                text = if (remainingText.isNotEmpty()) "$chapterText • $remainingText" else chapterText,
                                style = MaterialTheme.typography.labelSmall,
                                color = adaptiveOnSurfaceVariant.copy(alpha = 0.86f),
                            )
                        }
                    }
                }

                // Audio Visualizer - hidden on compact screens to save space
                if (!isCompact) {
                    item {
                        LaunchedEffect(hasRecordAudioPermission) {
                            if (!hasRecordAudioPermission) {
                                currentOnSetVisualizerEnabled(false)
                            }
                        }

                        if (hasRecordAudioPermission) {
                            // Initialize visualizer only after explicit permission grant
                            LaunchedEffect(state.isPlaying, hasRecordAudioPermission) {
                                if (state.isPlaying) {
                                    currentOnInitializeVisualizer()
                                    currentOnSetVisualizerEnabled(true)
                                } else {
                                    currentOnSetVisualizerEnabled(false)
                                }
                            }

                            val style =
                                when (visualizerMode) {
                                    1 -> VisualizerStyle.BARS
                                    2 -> VisualizerStyle.CIRCULAR
                                    3 -> VisualizerStyle.MINIMAL
                                    else -> VisualizerStyle.WAVEFORM
                                }
                            AudioVisualizer(
                                waveformData = visualizerWaveformData,
                                isPlaying = state.isPlaying,
                                style = style,
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
                                fontSize = if (isCompact) 13.sp else 14.sp,
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
                        CircularIconButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previousChapter),
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(skipButtonSize),
                            size = skipIconSize,
                        )

                        // Seek backward (10s)
                        CircularIconButton(
                            icon = Icons.Filled.Replay,
                            contentDescription = stringResource(R.string.seekBackwardDescription, state.rewindInterval),
                            onClick = onSeekBackward,
                            modifier = Modifier.size(seekButtonSize),
                            size = seekIconSize,
                        )

                        Spacer(modifier = Modifier.width(16.dp))
                        val playbackStateDescription =
                            if (state.isPlaying) {
                                stringResource(R.string.playbackStatePlaying)
                            } else {
                                stringResource(R.string.playbackStatePaused)
                            }

                        // Play/Pause - Larger and more prominent
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(playPauseButtonSize * 1.2f)
                                    .graphicsLayer {
                                        scaleX = playPauseButtonScale
                                        scaleY = playPauseButtonScale
                                    },
                        ) {
                            FilledIconButton(
                                onClick = onPlayPause,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .semantics {
                                            stateDescription = playbackStateDescription
                                        },
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
                                    modifier =
                                        Modifier
                                            .size(playPauseIconSize * 1.2f)
                                            .graphicsLayer {
                                                scaleX = playPauseIconScale
                                                scaleY = playPauseIconScale
                                            },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Seek forward (30s)
                        CircularIconButton(
                            icon = Icons.Filled.FastForward,
                            contentDescription = stringResource(R.string.seekForwardDescription, state.forwardInterval),
                            onClick = onSeekForward,
                            modifier = Modifier.size(seekButtonSize),
                            size = seekIconSize,
                        )

                        // Skip next
                        CircularIconButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.nextChapter),
                            onClick = onSkipNext,
                            modifier = Modifier.size(skipButtonSize),
                            size = skipIconSize,
                        )
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
                                    onClick = {
                                        if (suppressNextSpeedClick) {
                                            suppressNextSpeedClick = false
                                        } else {
                                            onSpeedClick()
                                        }
                                    },
                                    interactionSource = speedButtonInteractionSource,
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

                                // Visualizer Mode Toggle
                                FilledTonalButton(
                                    onClick = onVisualizerModeCycle,
                                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.enableVisualizer),
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

                            // Second row: Timer, AB Repeat, Bookmarks & Lyrics (if available)
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
                                        if (sleepTimerState is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Idle) {
                                            Icons.Outlined.Timer
                                        } else {
                                            Icons.Filled.Timer
                                        },
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

                                // AB Repeat Button
                                FilledTonalButton(
                                    onClick = onABRepeatClick,
                                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                when (abRepeatState.phase) {
                                                    ABRepeatPhase.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                                                    ABRepeatPhase.A_SET -> MaterialTheme.colorScheme.tertiaryContainer
                                                    ABRepeatPhase.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                        ),
                                ) {
                                    when (abRepeatState.phase) {
                                        ABRepeatPhase.INACTIVE ->
                                            Text(
                                                "A B",
                                                fontSize = controlButtonTextSize,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        ABRepeatPhase.A_SET ->
                                            Text(
                                                "A",
                                                fontSize = controlButtonTextSize,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            )
                                        ABRepeatPhase.ACTIVE ->
                                            Text(
                                                "A→B",
                                                fontSize = controlButtonTextSize,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            )
                                    }
                                }

                                // Bookmarks Button
                                FilledTonalButton(
                                    onClick = onBookmarksClick,
                                    modifier = Modifier.weight(1f).height(controlButtonHeight),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                if (state.bookmarks.isNotEmpty()) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                            contentColor =
                                                if (state.bookmarks.isNotEmpty()) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        ),
                                ) {
                                    Icon(
                                        if (state.bookmarks.isNotEmpty()) {
                                            Icons.Filled.Bookmark
                                        } else {
                                            Icons.Outlined.Bookmark
                                        },
                                        stringResource(R.string.bookmarks),
                                        Modifier.size(controlButtonIconSize),
                                    )
                                    if (state.bookmarks.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.bookmarkCount, state.bookmarks.size),
                                            fontSize = controlButtonTextSize,
                                        )
                                    }
                                }

                                // Lyrics Toggle Button
                                if (hasLyrics) {
                                    FilledTonalButton(
                                        onClick = {
                                            HapticManager.performTap(hapticFeedback)
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
                                            if (showingLyrics) {
                                                Icons.Filled.Description
                                            } else {
                                                Icons.Outlined.Description
                                            },
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
                                onClick = {
                                    if (suppressNextSpeedClick) {
                                        suppressNextSpeedClick = false
                                    } else {
                                        onSpeedClick()
                                    }
                                },
                                interactionSource = speedButtonInteractionSource,
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

                            // Visualizer Mode Toggle
                            FilledTonalButton(
                                onClick = onVisualizerModeCycle,
                                modifier = Modifier.weight(1f).height(controlButtonHeight),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.enableVisualizer),
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

                            // AB Repeat Button
                            FilledTonalButton(
                                onClick = onABRepeatClick,
                                modifier = Modifier.weight(1f).height(controlButtonHeight),
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor =
                                            when (abRepeatState.phase) {
                                                ABRepeatPhase.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                                                ABRepeatPhase.A_SET -> MaterialTheme.colorScheme.tertiaryContainer
                                                ABRepeatPhase.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                    ),
                            ) {
                                when (abRepeatState.phase) {
                                    ABRepeatPhase.INACTIVE ->
                                        Text(
                                            "A B",
                                            fontSize = controlButtonTextSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    ABRepeatPhase.A_SET ->
                                        Text(
                                            "A",
                                            fontSize = controlButtonTextSize,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        )
                                    ABRepeatPhase.ACTIVE ->
                                        Text(
                                            "A→B",
                                            fontSize = controlButtonTextSize,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
                                    if (sleepTimerState is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Idle) {
                                        Icons.Outlined.Timer
                                    } else {
                                        Icons.Filled.Timer
                                    },
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
                                        HapticManager.performTap(hapticFeedback)
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
                                        if (showingLyrics) {
                                            Icons.Filled.Description
                                        } else {
                                            Icons.Outlined.Description
                                        },
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

    if (showBookmarkNoteSheet && pendingBookmarkId != null) {
        JabookModalBottomSheet(
            onDismissRequest = {
                bookmarkRecordTimeoutJob.value?.cancel()
                bookmarkRecordTimeoutJob.value = null
                bookmarkRecorder.value?.runCatching {
                    stop()
                    reset()
                    release()
                }
                bookmarkRecorder.value = null
                bookmarkPlayer.value?.runCatching {
                    stop()
                    reset()
                    release()
                }
                bookmarkPlayer.value = null
                isRecordingBookmark = false
                isPlayingBookmarkAudio = false
                showBookmarkNoteSheet = false
                pendingBookmarkId = null
                pendingBookmarkNote = ""
                pendingBookmarkAudioPath = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.bookmarkNoteSheetTitle),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = pendingBookmarkNote,
                    onValueChange = { pendingBookmarkNote = it },
                    label = { Text(stringResource(R.string.bookmarkNoteSheetLabel)) },
                    placeholder = { Text(stringResource(R.string.bookmarkNoteSheetPlaceholder)) },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (!hasRecordAudioPermission) {
                                onRequestRecordAudioPermission()
                                return@FilledTonalButton
                            }
                            if (isRecordingBookmark) {
                                bookmarkRecordTimeoutJob.value?.cancel()
                                bookmarkRecordTimeoutJob.value = null
                                bookmarkRecorder.value?.runCatching {
                                    stop()
                                    reset()
                                    release()
                                }
                                bookmarkRecorder.value = null
                                isRecordingBookmark = false
                                return@FilledTonalButton
                            }

                            val bookmarkId = pendingBookmarkId ?: return@FilledTonalButton
                            val outputDir = File(context.cacheDir, "bookmark_notes")
                            outputDir.mkdirs()
                            val outputFile = File(outputDir, "bookmark_$bookmarkId.m4a")
                            val recorder =
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    MediaRecorder(context)
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaRecorder()
                                }
                            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            recorder.setAudioEncodingBitRate(96_000)
                            recorder.setAudioSamplingRate(44_100)
                            recorder.setOutputFile(outputFile.absolutePath)
                            recorder.prepare()
                            recorder.start()
                            bookmarkRecorder.value = recorder
                            pendingBookmarkAudioPath = outputFile.absolutePath
                            isRecordingBookmark = true
                            bookmarkRecordTimeoutJob.value?.cancel()
                            bookmarkRecordTimeoutJob.value =
                                seekScope.launch {
                                    delay(30_000L)
                                    if (isRecordingBookmark) {
                                        bookmarkRecorder.value?.runCatching {
                                            stop()
                                            reset()
                                            release()
                                        }
                                        bookmarkRecorder.value = null
                                        isRecordingBookmark = false
                                    }
                                }
                        },
                    ) {
                        Icon(
                            imageVector = if (isRecordingBookmark) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                if (isRecordingBookmark) {
                                    stringResource(R.string.stopRecording)
                                } else {
                                    stringResource(R.string.recordVoiceNote)
                                },
                        )
                    }

                    FilledTonalButton(
                        enabled = !pendingBookmarkAudioPath.isNullOrBlank(),
                        onClick = {
                            val path = pendingBookmarkAudioPath ?: return@FilledTonalButton
                            if (bookmarkPlayer.value != null) {
                                bookmarkPlayer.value?.runCatching {
                                    stop()
                                    reset()
                                    release()
                                }
                                bookmarkPlayer.value = null
                                isPlayingBookmarkAudio = false
                                return@FilledTonalButton
                            }
                            val player = MediaPlayer()
                            bookmarkPlayer.value = player
                            try {
                                player.setDataSource(path)
                                player.setOnCompletionListener {
                                    bookmarkPlayer.value?.runCatching {
                                        reset()
                                        release()
                                    }
                                    bookmarkPlayer.value = null
                                    isPlayingBookmarkAudio = false
                                }
                                player.setOnPreparedListener {
                                    if (bookmarkPlayer.value !== it) return@setOnPreparedListener
                                    it.start()
                                    isPlayingBookmarkAudio = true
                                }
                                player.setOnErrorListener { _, what, extra ->
                                    playerScreenLogger.e {
                                        "Bookmark voice-note playback failed in MediaPlayer listener: what=$what extra=$extra"
                                    }
                                    bookmarkPlayer.value = null
                                    isPlayingBookmarkAudio = false
                                    seekScope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.errorPlayingVoiceNote),
                                        )
                                    }
                                    player.runCatching {
                                        reset()
                                        release()
                                    }
                                    true
                                }
                                player.prepareAsync()
                            } catch (e: java.io.IOException) {
                                playerScreenLogger.e(e) { "Failed to prepare bookmark voice-note (I/O)" }
                                seekScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.errorPlayingVoiceNote),
                                    )
                                }
                                player.runCatching {
                                    reset()
                                    release()
                                }
                                bookmarkPlayer.value = null
                                isPlayingBookmarkAudio = false
                            } catch (e: IllegalStateException) {
                                playerScreenLogger.e(e) { "Failed to prepare bookmark voice-note (illegal state)" }
                                seekScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.errorPlayingVoiceNote),
                                    )
                                }
                                player.runCatching {
                                    reset()
                                    release()
                                }
                                bookmarkPlayer.value = null
                                isPlayingBookmarkAudio = false
                            } catch (e: SecurityException) {
                                playerScreenLogger.e(e) { "Failed to prepare bookmark voice-note (security)" }
                                seekScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.errorPlayingVoiceNote),
                                    )
                                }
                                player.runCatching {
                                    reset()
                                    release()
                                }
                                bookmarkPlayer.value = null
                                isPlayingBookmarkAudio = false
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isPlayingBookmarkAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription =
                                if (isPlayingBookmarkAudio) {
                                    stringResource(R.string.stopPlayback)
                                } else {
                                    stringResource(R.string.playVoiceNote)
                                },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                if (isPlayingBookmarkAudio) {
                                    stringResource(R.string.stopPlayback)
                                } else {
                                    stringResource(R.string.playVoiceNote)
                                },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    FilledTonalButton(
                        onClick = {
                            bookmarkRecordTimeoutJob.value?.cancel()
                            bookmarkRecordTimeoutJob.value = null
                            bookmarkRecorder.value?.runCatching {
                                stop()
                                reset()
                                release()
                            }
                            bookmarkRecorder.value = null
                            bookmarkPlayer.value?.runCatching {
                                stop()
                                reset()
                                release()
                            }
                            bookmarkPlayer.value = null
                            isRecordingBookmark = false
                            isPlayingBookmarkAudio = false
                            showBookmarkNoteSheet = false
                            pendingBookmarkId = null
                            pendingBookmarkNote = ""
                            pendingBookmarkAudioPath = null
                        },
                    ) {
                        Text(text = stringResource(R.string.skip))
                    }
                    FilledTonalButton(
                        onClick = {
                            val bookmarkId = pendingBookmarkId ?: return@FilledTonalButton
                            bookmarkRecordTimeoutJob.value?.cancel()
                            bookmarkRecordTimeoutJob.value = null
                            bookmarkRecorder.value?.runCatching {
                                stop()
                                reset()
                                release()
                            }
                            bookmarkRecorder.value = null
                            bookmarkPlayer.value?.runCatching {
                                stop()
                                reset()
                                release()
                            }
                            bookmarkPlayer.value = null
                            isRecordingBookmark = false
                            isPlayingBookmarkAudio = false
                            onUpdateBookmark(bookmarkId, pendingBookmarkNote, pendingBookmarkAudioPath)
                            showBookmarkNoteSheet = false
                            pendingBookmarkId = null
                            pendingBookmarkNote = ""
                            pendingBookmarkAudioPath = null
                        },
                    ) {
                        Text(text = stringResource(R.string.save))
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Switch }
                        .toggleable(
                            value = useGlobal,
                            onValueChange = {
                                useGlobal = it
                                if (it) {
                                    onResetSettings()
                                } else {
                                    onUpdateSettings(rewindSeconds.toInt(), forwardSeconds.toInt())
                                }
                            },
                            role = Role.Switch,
                        ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.useGlobalSettings),
                    style = MaterialTheme.typography.bodyLarge,
                )
                androidx.compose.material3.Switch(
                    checked = useGlobal,
                    onCheckedChange = null,
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
                        .padding(vertical = 12.dp)
                        .semantics { role = Role.Switch }
                        .toggleable(
                            value = isVinylMode,
                            onValueChange = onVinylModeChange,
                            role = Role.Switch,
                        ),
            ) {
                Text(
                    text = stringResource(R.string.vinylMode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isVinylMode,
                    onCheckedChange = null,
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

// P-91: Time formatting delegated to PlayerTimeFormatter
internal fun formatDuration(durationMs: Long): String = PlayerTimeFormatter.formatDuration(durationMs)

internal fun formatPlaybackSpeedLabel(playbackSpeed: Float): String = PlayerTimeFormatter.formatPlaybackSpeedLabel(playbackSpeed)

private const val HOLD_TO_BOOST_ACTIVATION_DELAY_MS: Long = 300L

internal fun playerStateContentKey(state: PlayerState): String =
    when (state) {
        is PlayerState.Loading -> "loading"
        is PlayerState.Active -> "active"
        is PlayerState.Error -> "error"
    }

internal data class ChapterBoundaryHapticDecision(
    val shouldPerformHaptic: Boolean,
    val nextSkipTriggeredHaptic: Boolean,
    val nextLastChapterBoundaryIndex: Int,
)

internal fun resolveChapterBoundaryHapticDecision(
    previousChapterIndex: Int,
    newChapterIndex: Int,
    skipTriggeredHaptic: Boolean,
): ChapterBoundaryHapticDecision? {
    if (newChapterIndex == previousChapterIndex) return null
    return if (skipTriggeredHaptic) {
        ChapterBoundaryHapticDecision(
            shouldPerformHaptic = false,
            nextSkipTriggeredHaptic = false,
            nextLastChapterBoundaryIndex = newChapterIndex,
        )
    } else {
        ChapterBoundaryHapticDecision(
            shouldPerformHaptic = true,
            nextSkipTriggeredHaptic = false,
            nextLastChapterBoundaryIndex = newChapterIndex,
        )
    }
}

internal fun mapKeyEventToPlayerIntent(keyEvent: androidx.compose.ui.input.key.KeyEvent): PlayerIntent? =
    when (keyEvent.key) {
        Key.Spacebar -> PlayerIntent.TogglePlayPause
        Key.DirectionLeft ->
            if (keyEvent.isShiftPressed) {
                PlayerIntent.SkipPrevious
            } else {
                PlayerIntent.SeekBackward
            }
        Key.DirectionRight ->
            if (keyEvent.isShiftPressed) {
                PlayerIntent.SkipNext
            } else {
                PlayerIntent.SeekForward
            }
        else -> null
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

@Composable
private fun PlayerLoadingSkeleton(modifier: Modifier = Modifier) {
    val loadingPlayerLabel = stringResource(R.string.loading_player)
    val transition = rememberInfiniteTransition(label = "player_loading_skeleton")
    val shimmerShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "player_loading_shimmer_shift",
    )
    val shimmerBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            start =
                androidx.compose.ui.geometry
                    .Offset(x = -600f + 1200f * shimmerShift, y = 0f),
            end =
                androidx.compose.ui.geometry
                    .Offset(x = 0f + 1200f * shimmerShift, y = 600f),
        )

    Column(
        modifier =
            modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .semantics {
                    contentDescription = loadingPlayerLabel
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(shimmerBrush),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.64f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.46f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmerBrush),
        )
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 48.dp, height = 14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush),
            )
            Box(
                modifier =
                    Modifier
                        .size(width = 48.dp, height = 14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(shimmerBrush),
            )
            Box(
                modifier =
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(shimmerBrush),
            )
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(shimmerBrush),
            )
        }
    }
}
