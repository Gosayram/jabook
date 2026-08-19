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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.os.PowerManager
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.core.util.UiFormatters
import com.jabook.app.jabook.compose.core.util.rememberReduceMotion
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.feature.player.SquigglySlider
import com.jabook.app.jabook.compose.util.rememberClickDebouncer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
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
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val hasNextChapter by viewModel.hasNextChapter.collectAsStateWithLifecycle()
    val hasPreviousChapter by viewModel.hasPreviousChapter.collectAsStateWithLifecycle()
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
    val audioManager = remember(context) { context.getSystemService<AudioManager>() }
    val wsc = LocalWindowSizeClass.current
    val resolved = wsc?.let { AdaptiveUtils.resolveWindowSizeClassOrNull(it, context) } ?: wsc
    val isCompactScreen =
        resolved?.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
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

    // Keep expensive visual effects in sync with Battery Saver while the player is visible.
    var isPowerSaveMode by remember(context) {
        val powerManager = context.getSystemService<PowerManager>()
        mutableStateOf(powerManager?.isPowerSaveMode == true)
    }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context,
                    intent: Intent,
                ) {
                    isPowerSaveMode = receiverContext.getSystemService<PowerManager>()?.isPowerSaveMode == true
                }
            }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
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
                currentPositionMs = currentPosition,
                chapters = state.chapters,
                currentChapterIndex = state.currentChapterIndex,
                onJumpToBookmark = { bookmark ->
                    viewModel.seekToBookmark(bookmark)
                },
                onDeleteBookmark = { bookmarkId ->
                    deleteBookmarkVoiceNotes(context.filesDir, bookmarkId)
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
                    // TopAppBar applies statusBars insets itself; zeroed to avoid double inset under NavigationSuiteScaffold.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                androidx.compose.material3.IconButton(
                                    onClick = { navigationClickGuard.run(currentOnNavigateBack) },
                                ) {
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
                                            isBookmarkNoteSheetVisible ||
                                            showOverflowMenu
                                    if (shouldIgnoreShortcuts) return@onPreviewKeyEvent false
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            audioManager?.adjustMusicVolumeIfMutable(
                                                direction = AudioManager.ADJUST_RAISE,
                                                flags = AudioManager.FLAG_SHOW_UI,
                                            )
                                            true
                                        }
                                        Key.DirectionDown -> {
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
                                                positionMs = currentPosition,
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

                                    PremiumPlayerBackground(
                                        themeColors = state.themeColors,
                                        coverImageModel = CoverUtils.getCoverModel(state.book, context),
                                        hazeState = overlayHazeState,
                                        isPowerSaveMode = isPowerSaveMode,
                                        isPlaying = state.isPlaying,
                                    ) {
                                        PlayerContent(
                                            state = state,
                                            playbackSpeed = playbackSpeed,
                                            reduceMotion = reduceMotion,
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
                                                    viewModel.dispatch(PlayerIntent.SkipPrevious)
                                                }
                                            },
                                            hasNextChapter = hasNextChapter,
                                            hasPreviousChapter = hasPreviousChapter,
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
                                            onSelectChapter = { chapterIndex, positionMs ->
                                                viewModel.dispatch(PlayerIntent.SelectChapter(chapterIndex, positionMs))
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
                                                deleteBookmarkVoiceNotes(context.filesDir, bookmarkId)
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
                                            currentPositionMs = currentPosition,
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
                        },
                    )
                }
            }
        },
        modifier = modifier.background(MaterialTheme.colorScheme.background),
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
    hasNextChapter: Boolean,
    hasPreviousChapter: Boolean,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSelectChapter: (Int, Long) -> Unit,
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
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    currentPositionMs: Long,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope?,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope?,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val seekState = rememberPlayerSeekState(state = state, abRepeatState = abRepeatState, currentPositionMs = currentPositionMs)
    val playbackPositionLabel = stringResource(R.string.playbackPositionLabel)
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
                            onClick = { if (hasLyrics) showLyrics(!showingLyrics) },
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
                value = seekState.displayedProgress.value,
                onValueChange = { newProgress ->
                    seekState.onSliderValueChange(newProgress, hapticFeedback)
                },
                onValueChangeFinished = {
                    seekState.onSliderValueChangeFinished(onSeek, onSelectChapter)
                },
                onLongPress = { pressedProgress ->
                    if (seekState.timeline.totalDurationMs <= 0) return@SquigglySlider
                    val target =
                        ChapterSeekbarPolicy.resolveSeekTarget(
                            chapters = state.chapters,
                            progress = pressedProgress.coerceIn(0f, 1f),
                        )
                    HapticManager.performTap(hapticFeedback)
                    onAddBookmarkAtPosition(target.chapterIndex, target.chapterPositionMs) { }
                },
                isPlaying = state.isPlaying,
                chapterMarkersFractions = seekState.timeline.chapterMarkersFractions,
                bookmarkMarkersFractions = seekState.bookmarkMarkersFractions.value,
                abRepeatRange = seekState.abRepeatFractions.value,
                waveformData = FloatArray(0),
                activeTrackColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                inactiveTrackColor = (themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.24f),
                valueFormatter = seekState.valueFormatter,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics {
                            contentDescription = playbackPositionLabel
                            val current = formatDuration(seekState.currentGlobalPositionMs)
                            val total = formatDuration(seekState.timeline.totalDurationMs)
                            stateDescription = "$current of $total"
                            progressBarRangeInfo = ProgressBarRangeInfo(seekState.displayedProgress.value, 0f..1f)
                            setProgress { targetProgress ->
                                if (seekState.timeline.totalDurationMs <= 0) return@setProgress false
                                val target =
                                    ChapterSeekbarPolicy.resolveSeekTarget(
                                        chapters = state.chapters,
                                        progress = targetProgress.coerceIn(0f, 1f),
                                    )
                                if (target.chapterIndex != state.currentChapterIndex) {
                                    onSelectChapter(target.chapterIndex, target.chapterPositionMs)
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

            val elapsedFormatted = formatDuration(seekState.currentGlobalPositionMs)
            val totalFormatted = formatDuration(seekState.timeline.totalDurationMs)
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

            PlayerPlaybackButtons(
                isPlaying = state.isPlaying,
                rewindInterval = state.rewindInterval,
                forwardInterval = state.forwardInterval,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                hasNextChapter = hasNextChapter,
                hasPreviousChapter = hasPreviousChapter,
                onSeekForward = onSeekForward,
                onSeekBackward = onSeekBackward,
                isCompact = isCompact,
                primaryColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                onPrimaryColor = themeColors?.onPrimaryColor ?: MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth(),
            )

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
    reduceMotion: Boolean,
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
    hasNextChapter: Boolean,
    hasPreviousChapter: Boolean,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSelectChapter: (Int, Long) -> Unit,
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
    currentPositionMs: Long = 0L,
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
    val wsc = LocalWindowSizeClass.current
    val windowSizeClass = wsc?.let { AdaptiveUtils.resolveWindowSizeClassOrNull(it, context) } ?: wsc

    // Adaptive sizes for compact screens (phones)
    val isCompact =
        windowSizeClass == null ||
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
    // Optimized cover size: 70% for compact (phone optimization), 88% for larger screens
    val coverWidth = if (isCompact) 0.70f else 0.88f
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    // Increased spacing for better ergonomics
    val itemSpacing = if (isCompact) 16.dp else AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)
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

    // Discard any unsaved pending voice note when the player leaves composition (#40).
    // Recorder/player lifecycle lives in BookmarkNoteSheet.
    DisposableEffect(Unit) {
        onDispose {
            discardBookmarkVoiceNote(context.filesDir, pendingBookmarkAudioPath)
        }
    }

    LaunchedEffect(showBookmarkNoteSheet) { onBookmarkNoteSheetVisibilityChanged(showBookmarkNoteSheet) }

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
                hasNextChapter = hasNextChapter,
                hasPreviousChapter = hasPreviousChapter,
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
                snackbarHostState = snackbarHostState,
                currentPositionMs = currentPositionMs,
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

                    PlayerCoverSection(
                        state = state,
                        imageModifier = imageModifier,
                        coverWidth = coverWidth,
                        showingLyrics = showingLyrics,
                        showLyrics = { showLyrics = it },
                        isVinylMode = isVinylMode,
                        reduceMotion = reduceMotion,
                        hasLyrics = hasLyrics,
                        onStatsClick = onStatsClick,
                        onSeek = onSeek,
                        currentPositionMs = currentPositionMs,
                    )
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
                        val seekState =
                            rememberPlayerSeekState(state = state, abRepeatState = abRepeatState, currentPositionMs = currentPositionMs)
                        val playbackPositionLabel = stringResource(R.string.playbackPositionLabel)
                        val seekBackwardActionLabel =
                            stringResource(R.string.seekBackwardDescription, state.rewindInterval)
                        val seekForwardActionLabel =
                            stringResource(R.string.seekForwardDescription, state.forwardInterval)

                        SquigglySlider(
                            value = seekState.displayedProgress.value,
                            onValueChange = { newProgress ->
                                seekState.onSliderValueChange(newProgress, hapticFeedback)
                            },
                            onValueChangeFinished = {
                                seekState.onSliderValueChangeFinished(onSeek, onSelectChapter)
                            },
                            onLongPress = { pressedProgress ->
                                if (seekState.timeline.totalDurationMs <= 0) return@SquigglySlider
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
                            chapterMarkersFractions = seekState.timeline.chapterMarkersFractions,
                            bookmarkMarkersFractions = seekState.bookmarkMarkersFractions.value,
                            abRepeatRange = seekState.abRepeatFractions.value,
                            waveformData = seekbarWaveformData,
                            activeTrackColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                            inactiveTrackColor =
                                (themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary).copy(
                                    alpha = 0.24f,
                                ),
                            valueFormatter = seekState.valueFormatter,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .semantics {
                                        contentDescription = playbackPositionLabel
                                        val current = formatDuration(seekState.currentGlobalPositionMs)
                                        val total = formatDuration(seekState.timeline.totalDurationMs)
                                        stateDescription = "$current of $total"
                                        progressBarRangeInfo = ProgressBarRangeInfo(seekState.displayedProgress.value, 0f..1f)
                                        setProgress { targetProgress ->
                                            if (seekState.timeline.totalDurationMs <= 0) return@setProgress false
                                            val target =
                                                ChapterSeekbarPolicy.resolveSeekTarget(
                                                    chapters = state.chapters,
                                                    progress = targetProgress.coerceIn(0f, 1f),
                                                )
                                            if (target.chapterIndex != state.currentChapterIndex) {
                                                onSelectChapter(target.chapterIndex, target.chapterPositionMs)
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

                        if (seekState.isDragging) {
                            val previewTitle =
                                state.chapters
                                    .getOrNull(seekState.previewSeekTarget.value.chapterIndex)
                                    ?.title
                                    .orEmpty()
                            Text(
                                text = "${seekState.previewSeekTarget.value.chapterIndex + 1}. $previewTitle",
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
                        val elapsedFormatted = formatDuration(seekState.currentGlobalPositionMs)
                        val totalFormatted = formatDuration(seekState.timeline.totalDurationMs)
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
                            val remainingMs = (seekState.timeline.totalDurationMs - seekState.currentGlobalPositionMs).coerceAtLeast(0L)
                            val speed = state.playbackSpeed
                            val realRemainingMs = if (speed > 0f) (remainingMs / speed).toLong() else remainingMs
                            val remainingText =
                                if (realRemainingMs > 0L) {
                                    "-${UiFormatters.formatDurationCompact(realRemainingMs)}"
                                } else {
                                    ""
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
                        PlayerVisualizerSection(
                            hasRecordAudioPermission = hasRecordAudioPermission,
                            isPlaying = state.isPlaying,
                            visualizerMode = visualizerMode,
                            waveformData = visualizerWaveformData,
                            themeColors = state.themeColors,
                            onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                            onInitializeVisualizer = onInitializeVisualizer,
                            onSetVisualizerEnabled = onSetVisualizerEnabled,
                        )
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
                    PlayerPlaybackButtons(
                        isPlaying = state.isPlaying,
                        rewindInterval = state.rewindInterval,
                        forwardInterval = state.forwardInterval,
                        onPlayPause = onPlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        hasNextChapter = hasNextChapter,
                        hasPreviousChapter = hasPreviousChapter,
                        onSeekForward = onSeekForward,
                        onSeekBackward = onSeekBackward,
                        isCompact = isCompact,
                        playPauseButtonScale = playPauseButtonScale,
                        playPauseIconScale = playPauseIconScale,
                        primaryColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                        onPrimaryColor = themeColors?.onPrimaryColor ?: MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isCompact) smallItemSpacing else 0.dp),
                    )
                }

                // Spacer before control buttons
                item {
                    Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))
                }

                // Control Buttons - compact: two rows, larger screens: single row
                item {
                    PlayerControlRow(
                        isCompact = isCompact,
                        playbackSpeedLabel = playbackSpeedLabel,
                        chapterRepeatMode = chapterRepeatMode,
                        abRepeatState = abRepeatState,
                        sleepTimerState = sleepTimerState,
                        bookmarkCount = state.bookmarks.size,
                        hasLyrics = hasLyrics,
                        showingLyrics = showingLyrics,
                        speedButtonInteractionSource = speedButtonInteractionSource,
                        onSpeedButtonClick = {
                            if (suppressNextSpeedClick) {
                                suppressNextSpeedClick = false
                            } else {
                                onSpeedClick()
                            }
                        },
                        onAudioSettingsClick = onAudioSettingsClick,
                        onVisualizerModeCycle = onVisualizerModeCycle,
                        onChapterRepeatClick = onChapterRepeatClick,
                        onABRepeatClick = onABRepeatClick,
                        onSleepTimerClick = onSleepTimerClick,
                        onBookmarksClick = onBookmarksClick,
                        onToggleLyrics = {
                            HapticManager.performTap(hapticFeedback)
                            showLyrics = !showLyrics
                        },
                    )
                }
            }
        }
    }

    if (showBookmarkNoteSheet && pendingBookmarkId != null) {
        pendingBookmarkId?.let { bookmarkId ->
            BookmarkNoteSheet(
                bookmarkId = bookmarkId,
                note = pendingBookmarkNote,
                onNoteChange = { pendingBookmarkNote = it },
                audioPath = pendingBookmarkAudioPath,
                onAudioPathChange = { pendingBookmarkAudioPath = it },
                hasRecordAudioPermission = hasRecordAudioPermission,
                onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                onSave = { noteText, noteAudioPath ->
                    onUpdateBookmark(bookmarkId, noteText, noteAudioPath)
                    showBookmarkNoteSheet = false
                    pendingBookmarkId = null
                    pendingBookmarkNote = ""
                    pendingBookmarkAudioPath = null
                },
                onDismiss = {
                    showBookmarkNoteSheet = false
                    pendingBookmarkId = null
                    pendingBookmarkNote = ""
                    discardBookmarkVoiceNote(context.filesDir, pendingBookmarkAudioPath)
                    pendingBookmarkAudioPath = null
                },
                onError = { message ->
                    seekScope.launch { snackbarHostState.showSnackbar(message) }
                },
            )
        }
    }
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
