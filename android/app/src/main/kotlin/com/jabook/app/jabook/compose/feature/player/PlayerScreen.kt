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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
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
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * EntryPoint to access AudioMetadataParser from Hilt in Composable.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioMetadataParserEntryPoint {
    fun audioMetadataParser(): AudioMetadataParser
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
fun PlayerScreen(
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

    // Auto-initialize player when book data is ready
    // Only initialize once when we have Success state with actual chapters
    // Use specific keys to avoid unnecessary recomposition
    val shouldInitializePlayer =
        remember(uiState) {
            uiState is PlayerUiState.Success && (uiState as? PlayerUiState.Success)?.chapters?.isNotEmpty() == true
        }
    androidx.compose.runtime.LaunchedEffect(shouldInitializePlayer) {
        if (shouldInitializePlayer) {
            viewModel.initializePlayer()
        }
    }

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Vinyl Mode State
    var isVinylMode by remember { mutableStateOf(false) }

    // Navigator for SupportingPaneScaffold
    val scaffoldNavigator = rememberSupportingPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Check for Power Save Mode to disable expensive visual effects
    val isPowerSaveMode by remember(context) {
        val powerManager = context.getSystemService<PowerManager>()
        mutableStateOf(powerManager?.isPowerSaveMode == true)
    }

    // Request notification permission on Android 13+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher =
            androidx.activity.compose.rememberLauncherForActivityResult(
                contract =
                    androidx.activity.result.contract.ActivityResultContracts
                        .RequestPermission(),
                onResult = { isGranted ->
                    if (!isGranted) {
                        // Show rationale explaining why notification permission is needed
                        android.util.Log.w("PlayerScreen", "Notification permission denied")
                        scope.launch {
                            val result =
                                snackbarHostState.showSnackbar(
                                    message = "Notifications help control playback from the notification bar",
                                    actionLabel = "Settings",
                                    duration = androidx.compose.material3.SnackbarDuration.Long,
                                )

                            // If user clicks "Settings", open app settings
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                try {
                                    val intent =
                                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                        }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("PlayerScreen", "Failed to open settings", e)
                                }
                            }
                        }
                    }
                },
            )

        androidx.compose.runtime.LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Request RECORD_AUDIO permission for audio visualizer
    val recordAudioPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    android.util.Log.d("PlayerScreen", "RECORD_AUDIO permission granted, visualizer enabled")
                } else {
                    android.util.Log.w("PlayerScreen", "RECORD_AUDIO permission denied, visualizer disabled")
                }
            },
        )

    // Check and request RECORD_AUDIO permission
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val hasPermission =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
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
            onNavigateBack()
        }
    }

    // Playback Speed Sheet
    if (showSpeedSheet) {
        val speedSheetState = rememberModalBottomSheetState()
        PlaybackSpeedSheet(
            currentSpeed = playbackSpeed,
            pitchCorrectionEnabled = pitchCorrectionEnabled,
            onSpeedSelected = viewModel::setPlaybackSpeed,
            onPitchCorrectionChanged = viewModel::setPitchCorrectionEnabled,
            onDismiss = { showSpeedSheet = false },
            sheetState = speedSheetState,
        )
    }

    // Sleep Timer Sheet
    if (showSleepTimerSheet) {
        SleepTimerSheet(
            currentState = sleepTimerState,
            onStartTimer = viewModel::startSleepTimer,
            onStartTimerEndOfChapter = viewModel::startSleepTimerEndOfChapter,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimerSheet = false },
        )
    }

    // Removed Chapter Selector Sheet - using adaptive pane instead

    // Player Settings Sheet
    if (showSettingsSheet && uiState is PlayerUiState.Success) {
        val state = uiState as PlayerUiState.Success
        PlayerSettingsSheet(
            book = state.book,
            onUpdateSettings = viewModel::updateBookSeekSettings,
            onResetSettings = viewModel::resetBookSeekSettings,
            onDismiss = { showSettingsSheet = false },
            isVinylMode = isVinylMode,
            onVinylModeChange = { isVinylMode = it },
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
                            is PlayerUiState.Loading -> {
                                LoadingScreen(message = "Loading player...")
                            }

                            is PlayerUiState.Success -> {
                                val chapterRepeatMode by viewModel.chapterRepeatMode.collectAsStateWithLifecycle()
                                // Click debouncer for preventing double clicks (inspired by Easybook)
                                val clickDebouncer = rememberClickDebouncer(debounceTimeMs = 300L)

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
                                        chapterRepeatMode = chapterRepeatMode,
                                        onPlayPause = {
                                            clickDebouncer.debounce {
                                                if (state.isPlaying) viewModel.pause() else viewModel.play()
                                            }
                                        },
                                        onSkipNext = {
                                            clickDebouncer.debounce { viewModel.skipToNext() }
                                        },
                                        onSkipPrevious = {
                                            clickDebouncer.debounce { viewModel.skipToPrevious() }
                                        },
                                        onSeek = viewModel::seekTo,
                                        onSeekForward = {
                                            clickDebouncer.debounce { viewModel.seekForward() }
                                        },
                                        onSeekBackward = {
                                            clickDebouncer.debounce { viewModel.seekBackward() }
                                        },
                                        onSelectChapter = viewModel::skipToChapter,
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
                                        onSleepTimerClick = { showSleepTimerSheet = true },
                                        onChapterRepeatClick = {
                                            clickDebouncer.debounce {
                                                viewModel.toggleChapterRepeat()
                                            }
                                        },
                                        onStatsClick = { showStatsOverlay = true },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                }
                            }

                            is PlayerUiState.Error -> {
                                ErrorScreen(
                                    message = state.message,
                                    onRetry = onNavigateBack,
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
                if (uiState is PlayerUiState.Success) {
                    val state = uiState as PlayerUiState.Success
                    PlayerChapterPane(
                        chapters = state.chapters,
                        currentChapterIndex = state.currentChapterIndex,
                        normalizeEnabled = normalizeEnabled,
                        onChapterClick = { chapterIndex ->
                            // Start playback immediately (skipToChapter now includes play())
                            viewModel.skipToChapter(chapterIndex)
                            // On compact screens, smoothly close the pane after selection
                            scope.launch {
                                // Small delay to ensure playback starts before closing
                                kotlinx.coroutines.delay(50)
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

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun PlayerContent(
    state: PlayerUiState.Success,
    playbackSpeed: Float,
    hazeState: HazeState?,
    isVinylMode: Boolean,
    sleepTimerState: com.jabook.app.jabook.compose.domain.model.SleepTimerState,
    normalizeEnabled: Boolean,
    chapterRepeatMode: ChapterRepeatMode,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onChapterClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onChapterRepeatClick: () -> Unit,
    onStatsClick: () -> Unit,
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
    val windowSizeClass = AdaptiveUtils.getEffectiveWindowSizeClass(rawWindowSizeClass, context) ?: rawWindowSizeClass

    // Adaptive sizes for compact screens (phones)
    val isCompact = windowSizeClass.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
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
    // Increased cover size: 88% for compact, 92% for larger screens
    val coverWidth = if (isCompact) 0.88f else 0.92f
    val contentPadding = AdaptiveUtils.getContentPadding(windowSizeClass)
    // Increased spacing for better ergonomics
    val itemSpacing = if (isCompact) 16.dp else AdaptiveUtils.getItemSpacing(windowSizeClass)
    // Spacing for compact screens between specific elements
    val smallItemSpacing = if (isCompact) 8.dp else 12.dp
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
                val metadata = metadataParser.parseMetadata(fileUrl)
                authorFromMetadata = metadata?.artist?.takeIf { it.isNotBlank() }
            }
        }
    }

    val displayAuthor = authorFromMetadata

    // Lyrics visibility state
    var showLyrics by remember { mutableStateOf(false) }

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
                        bottom = if (isCompact) 80.dp else 112.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            // Author from metadata (above cover)
            if (displayAuthor != null) {
                item {
                    Text(
                        text = displayAuthor,
                        style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
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

                if (showLyrics && !state.lyrics.isNullOrEmpty()) {
                    Box(
                        modifier =
                            imageModifier
                                .fillMaxWidth(coverWidth)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    ) {
                        LyricsView(
                            lyrics = state.lyrics!!,
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
                                .clickable {
                                    if (!state.lyrics.isNullOrEmpty()) {
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
                                .clickable {
                                    // Make cover clickable to toggle lyrics if available
                                    if (!state.lyrics.isNullOrEmpty()) {
                                        showLyrics = !showLyrics
                                    }
                                    // Or if no lyrics, maybe handle as "show controls" or just do nothing (existing behavior)
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
                    // Progress bar
                    val progress =
                        state.currentChapter?.let {
                            val durationMs = it.duration.inWholeMilliseconds
                            if (durationMs > 0) state.currentPosition.toFloat() / durationMs.toFloat() else 0f
                        } ?: 0f

                    SquigglySlider(
                        value = progress,
                        onValueChange = { newProgress ->
                            state.currentChapter?.let { chapter ->
                                onSeek((newProgress * chapter.duration.inWholeMilliseconds.toFloat()).toLong())
                            }
                        },
                        isPlaying = state.isPlaying,
                        activeTrackColor = themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = (themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.24f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics {
                                    val current = formatDuration(state.currentPosition)
                                    val total = formatDuration(state.currentChapter?.duration?.inWholeMilliseconds ?: 0)
                                    stateDescription = "$current of $total"
                                },
                    )

                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(state.currentPosition),
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = formatDuration(state.currentChapter?.duration?.inWholeMilliseconds ?: 0),
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
                                state.currentChapterIndex + 1,
                                state.chapters.size,
                            )

                        // Calculate finish time
                        val remainingMs =
                            state.currentChapter
                                ?.duration
                                ?.inWholeMilliseconds
                                ?.minus(state.currentPosition) ?: 0
                        val speed = state.playbackSpeed
                        // Avoid division by zero
                        val realRemainingMs = if (speed > 0) (remainingMs / speed).toLong() else remainingMs

                        val finishTime =
                            java.util.Calendar.getInstance().apply {
                                add(java.util.Calendar.MILLISECOND, realRemainingMs.toInt())
                            }
                        val formattedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(finishTime.time)
                        val finishText = stringResource(R.string.finishAt, formattedTime)

                        Text(
                            text = "$chapterText • $finishText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Audio Visualizer
            item {
                val service =
                    com.jabook.app.jabook.audio.AudioPlayerService
                        .getInstance()
                val waveformData by service
                    ?.getVisualizerWaveformData()
                    ?.collectAsStateWithLifecycle()
                    ?: remember { androidx.compose.runtime.mutableStateOf(FloatArray(256)) }

                // Initialize visualizer when playing
                LaunchedEffect(state.isPlaying) {
                    if (state.isPlaying) {
                        service?.initializeVisualizer()
                    }
                }

                AudioVisualizer(
                    waveformData = waveformData,
                    isPlaying = state.isPlaying,
                    style = VisualizerStyle.CIRCULAR, // Upgraded to Circular Visualizer
                    height = if (isCompact) 40.dp else 48.dp,
                    primaryColor = state.themeColors?.primaryColor ?: MaterialTheme.colorScheme.primary,
                    secondaryColor =
                        state.themeColors?.primaryColor?.copy(alpha = 0.5f)
                            ?: MaterialTheme.colorScheme.secondary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 8.dp else 0.dp),
                )
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
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White,
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
                            tint = Color.White.copy(alpha = 0.8f),
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
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
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
                            tint = Color.White.copy(alpha = 0.8f),
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
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Spacer before control buttons
            item {
                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))
            }

            // Control Buttons Row (Speed, Repeat & Sleep Timer)
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(controlButtonSpacing, Alignment.CenterHorizontally),
                ) {
                    // Playback Speed Button
                    FilledTonalButton(
                        onClick = onSpeedClick,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(controlButtonHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = stringResource(R.string.playbackSpeedTitle),
                            modifier =
                                Modifier
                                    .size(controlButtonIconSize)
                                    .padding(end = if (isCompact) 6.dp else 8.dp),
                        )
                        Text(
                            text =
                                run {
                                    val formattedSpeed =
                                        if (playbackSpeed % 1.0f == 0.0f) {
                                            // Whole number - show without decimal
                                            playbackSpeed.toInt().toString()
                                        } else {
                                            // Decimal - format with locale
                                            val locale = java.util.Locale.getDefault()
                                            // skip-migration
                                            val isRussian = locale.language == "ru"
                                            val symbols =
                                                java.text.DecimalFormatSymbols(
                                                    if (isRussian) locale else java.util.Locale.US,
                                                )
                                            val formatter = java.text.DecimalFormat("#.##", symbols)
                                            formatter.format(playbackSpeed)
                                        }
                                    "${formattedSpeed}x"
                                },
                            fontSize = controlButtonTextSize,
                        )
                    }

                    // Chapter Repeat Button
                    FilledTonalButton(
                        onClick = onChapterRepeatClick,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(controlButtonHeight),
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
                            ChapterRepeatMode.INFINITE -> {
                                // Show infinity symbol (∞) for infinite repeat mode
                                Text(
                                    text = "∞",
                                    fontSize = controlButtonTextSize,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            ChapterRepeatMode.OFF -> {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = stringResource(R.string.noRepeat),
                                    modifier = Modifier.size(controlButtonIconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ChapterRepeatMode.ONCE -> {
                                Icon(
                                    imageVector = Icons.Filled.RepeatOne,
                                    contentDescription = stringResource(R.string.repeatTrack),
                                    modifier = Modifier.size(controlButtonIconSize),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    // Sleep Timer Button
                    FilledTonalButton(
                        onClick = onSleepTimerClick,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(controlButtonHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = stringResource(R.string.sleepTimer),
                            modifier = Modifier.size(controlButtonIconSize),
                        )
                        if (sleepTimerState is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active) {
                            Text(
                                text = (sleepTimerState as com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active).formattedTime,
                                fontSize = controlButtonTextSize,
                            )
                        }
                    }

                    // Lyrics Toggle Button (Show only if lyrics available)
                    if (!state.lyrics.isNullOrEmpty()) {
                        FilledTonalButton(
                            onClick = { showLyrics = !showLyrics },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(controlButtonHeight),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor =
                                        if (showLyrics) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    contentColor =
                                        if (showLyrics) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                ),
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Description,
                                contentDescription = "Lyrics",
                                modifier = Modifier.size(controlButtonIconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
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
                        text = String.format(stringResource(R.string.secondsSuffix), rewindSeconds.toInt()),
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
                        text = String.format(stringResource(R.string.secondsSuffix), forwardSeconds.toInt()),
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
                    text = "Vinyl Mode", // TODO: Localize
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isVinylMode,
                    onCheckedChange = onVinylModeChange,
                )
            }
        }
    }
}

/**
 * Format duration in milliseconds to MM:SS format.
 */
internal fun formatDuration(durationMs: Long): String {
    val duration = durationMs.milliseconds
    val minutes = duration.inWholeMinutes
    val seconds = duration.inWholeSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
