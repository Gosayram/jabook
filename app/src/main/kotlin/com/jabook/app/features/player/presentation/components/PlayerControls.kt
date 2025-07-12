package com.jabook.app.features.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jabook.app.R

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Previous chapter
        IconButton(onClick = onPreviousChapter, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_previous_24),
                contentDescription = "Previous chapter",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Seek backward 15s
        IconButton(onClick = onSeekBackward, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_replay_15_24),
                contentDescription = "Seek backward 15s",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Play/Pause
        FloatingActionButton(onClick = onPlayPause, modifier = Modifier.size(72.dp), containerColor = MaterialTheme.colorScheme.primary) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp),
            )
        }

        // Seek forward 30s
        IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_forward_30_24),
                contentDescription = "Seek forward 30s",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Next chapter
        IconButton(onClick = onNextChapter, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next_24),
                contentDescription = "Next chapter",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    // Speed and sleep timer controls
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        // Speed control
        IconButton(onClick = onSpeedClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_headphones_24),
                    contentDescription = "Speed",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(text = "${playbackSpeed}x", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Sleep timer control
        IconButton(onClick = onSleepTimerClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_24),
                    contentDescription = "Sleep timer",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                if (sleepTimerMinutes > 0) {
                    Text(
                        text = "${sleepTimerMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
