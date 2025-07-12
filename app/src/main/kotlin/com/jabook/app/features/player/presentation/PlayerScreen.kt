package com.jabook.app.features.player.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jabook.app.features.player.PlayerViewModel
import com.jabook.app.features.player.presentation.components.PlayerControls
import com.jabook.app.features.player.presentation.components.PlayerProgressBar
import com.jabook.app.features.player.presentation.components.SleepTimerDialog
import com.jabook.app.features.player.presentation.components.SpeedDialog
import java.util.Locale

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Show player content if audiobook is loaded
            if (uiState.currentAudiobook != null) {
                val audiobook = uiState.currentAudiobook!!

                // Cover art placeholder
                Card(
                    modifier = Modifier.size(250.dp).align(Alignment.CenterHorizontally),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Cover art",
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title and author
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

                Spacer(modifier = Modifier.height(32.dp))

                // Progress bar
                PlayerProgressBar(
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    onSeekTo = { position -> viewModel.seekTo(position) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Player controls
                PlayerControls(
                    isPlaying = uiState.isPlaying,
                    onPlayPause = { viewModel.playPause() },
                    onSeekForward = { viewModel.seekForward() },
                    onSeekBackward = { viewModel.seekBackward() },
                    onNextChapter = { viewModel.nextChapter() },
                    onPreviousChapter = { viewModel.previousChapter() },
                    onSpeedClick = { viewModel.showSpeedDialog() },
                    onSleepTimerClick = { viewModel.showSleepTimerDialog() },
                    playbackSpeed = uiState.playbackSpeed,
                    sleepTimerMinutes = uiState.sleepTimerMinutes,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No audiobook loaded",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Speed dialog
    if (uiState.isSpeedDialogVisible) {
        SpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { speed ->
                viewModel.setPlaybackSpeed(speed)
                viewModel.hideSpeedDialog()
            },
            onDismiss = { viewModel.hideSpeedDialog() },
        )
    }

    // Sleep timer dialog
    if (uiState.isSleepTimerDialogVisible) {
        SleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutes,
            onTimerSet = { minutes ->
                viewModel.setSleepTimer(minutes)
                viewModel.hideSleepTimerDialog()
            },
            onDismiss = { viewModel.hideSleepTimerDialog() },
        )
    }
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
fun formatSleepTimer(minutes: Int): String {
    return if (minutes > 0) {
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
}
