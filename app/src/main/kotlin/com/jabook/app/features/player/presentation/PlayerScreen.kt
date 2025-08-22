package com.jabook.app.features.player.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jabook.app.features.player.PlayerViewModel
import com.jabook.app.features.player.presentation.components.PlayerControls
import com.jabook.app.features.player.presentation.components.PlayerControlsParams
import com.jabook.app.features.player.presentation.components.PlayerProgressBar
import com.jabook.app.features.player.presentation.components.SleepTimerDialog
import com.jabook.app.features.player.presentation.components.SpeedDialog
import com.jabook.app.shared.ui.AppThemeMode
import com.jabook.app.shared.ui.ThemeViewModel
import com.jabook.app.shared.ui.components.ThemeToggleButton
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel,
    themeMode: AppThemeMode,
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        TopAppBar(
            title = { Text("Now Playing") },
            actions = {
                ThemeToggleButton(themeMode = themeMode, onToggle = { themeViewModel.toggleTheme() })
            },
        )
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
            if (uiState.currentAudiobook != null) {
                val audiobook = uiState.currentAudiobook!!
                PlayerCoverSection()
                Spacer(modifier = Modifier.height(28.dp))
                PlayerTitleSection(audiobook)
                Spacer(modifier = Modifier.height(24.dp))
                PlayerProgressSection(
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    bookmarks = uiState.bookmarks.map { it.positionMs },
                    onSeekTo = { viewModel.seekTo(it) },
                )
                Spacer(modifier = Modifier.height(28.dp))
                PlayerControlsSection(
                    params =
                        PlayerControlsSectionParams(
                            uiState = uiState,
                            onPlayPause = { viewModel.playPause() },
                            onSeekForward = { viewModel.seekForward() },
                            onSeekBackward = { viewModel.seekBackward() },
                            onNextChapter = { viewModel.nextChapter() },
                            onPreviousChapter = { viewModel.previousChapter() },
                            onSpeedClick = { viewModel.showSpeedDialog() },
                            onSleepTimerClick = { viewModel.showSleepTimerDialog() },
                            onBookmarkClick = { viewModel.addBookmark() },
                            onShowBookmarksClick = {
                                viewModel.showBookmarksSheet()
                                coroutineScope.launch { sheetState.show() }
                            },
                        ),
                )
            } else {
                PlayerEmptyState()
            }
        }
    }
    if (uiState.isSpeedDialogVisible) {
        SpeedDialogSection(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = {
                viewModel.setPlaybackSpeed(it)
                viewModel.hideSpeedDialog()
            },
            onDismiss = { viewModel.hideSpeedDialog() },
        )
    }
    if (uiState.isSleepTimerDialogVisible) {
        SleepTimerDialogSection(
            currentMinutes = uiState.sleepTimerMinutes,
            onTimerSet = {
                viewModel.setSleepTimer(it)
                viewModel.hideSleepTimerDialog()
            },
            onDismiss = { viewModel.hideSleepTimerDialog() },
        )
    }
    if (uiState.isBookmarksSheetVisible) {
        BookmarksSheetSection(
            bookmarks = uiState.bookmarks,
            onBookmarkClick = { position ->
                viewModel.seekTo(position)
                viewModel.hideBookmarksSheet()
                coroutineScope.launch { sheetState.hide() }
            },
            onDismiss = { viewModel.hideBookmarksSheet() },
            sheetState = sheetState,
        )
    }
}

@Composable
private fun PlayerCoverSection() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.size(250.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = "Cover art",
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerTitleSection(audiobook: com.jabook.app.core.domain.model.Audiobook) {
    Text(
        text = audiobook.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = audiobook.author,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayerProgressSection(
    currentPosition: Long,
    duration: Long,
    bookmarks: List<Long>,
    onSeekTo: (Long) -> Unit,
) {
    PlayerProgressBar(
        currentPosition = currentPosition,
        duration = duration,
        bookmarkPositions = bookmarks,
        onSeekTo = onSeekTo,
        modifier = Modifier.fillMaxWidth(),
    )
}

data class PlayerControlsSectionParams(
    val uiState: com.jabook.app.features.player.PlayerUiState,
    val onPlayPause: () -> Unit,
    val onSeekForward: () -> Unit,
    val onSeekBackward: () -> Unit,
    val onNextChapter: () -> Unit,
    val onPreviousChapter: () -> Unit,
    val onSpeedClick: () -> Unit,
    val onSleepTimerClick: () -> Unit,
    val onBookmarkClick: () -> Unit,
    val onShowBookmarksClick: () -> Unit,
)

@Composable
private fun PlayerControlsSection(params: PlayerControlsSectionParams) {
    val playerParams =
        PlayerControlsParams(
            isPlaying = params.uiState.isPlaying,
            playbackSpeed = params.uiState.playbackSpeed,
            sleepTimerMinutes = params.uiState.sleepTimerMinutes,
            onPlayPause = params.onPlayPause,
            onSeekForward = params.onSeekForward,
            onSeekBackward = params.onSeekBackward,
            onNextChapter = params.onNextChapter,
            onPreviousChapter = params.onPreviousChapter,
            onSpeedClick = params.onSpeedClick,
            onSleepTimerClick = params.onSleepTimerClick,
            onBookmarkClick = params.onBookmarkClick,
            onShowBookmarksClick = params.onShowBookmarksClick,
            modifier = Modifier.fillMaxWidth(),
        )
    PlayerControls(params = playerParams)
}

@Composable
private fun PlayerEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No audiobook loaded",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeedDialogSection(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    SpeedDialog(
        currentSpeed = currentSpeed,
        onSpeedSelected = onSpeedSelected,
        onDismiss = onDismiss,
    )
}

@Composable
private fun SleepTimerDialogSection(
    currentMinutes: Int,
    onTimerSet: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SleepTimerDialog(
        currentMinutes = currentMinutes,
        onTimerSet = onTimerSet,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheetSection(
    bookmarks: List<com.jabook.app.core.domain.model.Bookmark>,
    onBookmarkClick: (Long) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(id = com.jabook.app.R.string.bookmarks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (bookmarks.isEmpty()) {
                Text(text = stringResource(id = com.jabook.app.R.string.no_bookmarks))
            } else {
                LazyColumn {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                                    onBookmarkClick(bookmark.positionMs)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = bookmark.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = formatBookmarkTime(bookmark.positionMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { /* Bookmark removal not implemented yet */ }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Remove bookmark")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBookmarkTime(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

// Helper function to format time for display
@Composable
fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

// Helper function to format sleep timer
@Composable
fun formatSleepTimer(minutes: Int): String =
    if (minutes > 0) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d", hours, remainingMinutes)
        } else {
            String.format(Locale.getDefault(), "%02d", remainingMinutes)
        }
    } else {
        ""
    }
