package com.jabook.app.features.player.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jabook.app.R
import com.jabook.app.shared.ui.theme.JaBookAnimations
import com.jabook.app.shared.ui.theme.animatePlayPauseScale
import com.jabook.app.shared.ui.theme.playPauseTransition

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
    onBookmarkClick: () -> Unit,
    onShowBookmarksClick: () -> Unit,
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
    modifier: Modifier = Modifier,
) {
    // Memoize computed values to reduce recomposition
    val playPauseIcon by remember(isPlaying) { derivedStateOf { if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24 } }
    val playPauseContentDescription by remember(isPlaying) { derivedStateOf { if (isPlaying) "Pause" else "Play" } }
    val speedText by remember(playbackSpeed) { derivedStateOf { "${playbackSpeed}x" } }
    val sleepTimerText by remember(sleepTimerMinutes) { derivedStateOf { if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else null } }

    // Animations
    val playPauseTransition = playPauseTransition(isPlaying)
    val playPauseScale by playPauseTransition.animatePlayPauseScale()

    val fabBackgroundColor by
        animateColorAsState(
            targetValue = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            animationSpec = tween(JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "fabBackgroundColor",
        )

    val fabContentColor by
        animateColorAsState(
            targetValue = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            animationSpec = tween(JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "fabContentColor",
        )

    val speedButtonScale by
        animateFloatAsState(
            targetValue = if (playbackSpeed != 1.0f) 1.1f else 1.0f,
            animationSpec = JaBookAnimations.springAnimationSpec,
            label = "speedButtonScale",
        )

    val timerButtonScale by
        animateFloatAsState(
            targetValue = if (sleepTimerMinutes > 0) 1.1f else 1.0f,
            animationSpec = JaBookAnimations.springAnimationSpec,
            label = "timerButtonScale",
        )

    val timerButtonColor by
        animateColorAsState(
            targetValue = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            animationSpec = tween(JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "timerButtonColor",
        )

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Previous chapter
        IconButton(
            onClick = onPreviousChapter,
            modifier =
                Modifier.size(56.dp).graphicsLayer {
                    scaleX = if (isPlaying) 1.05f else 1.0f
                    scaleY = if (isPlaying) 1.05f else 1.0f
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_previous_24),
                contentDescription = "Previous chapter",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Seek backward 15s
        IconButton(
            onClick = onSeekBackward,
            modifier =
                Modifier.size(56.dp).graphicsLayer {
                    scaleX = if (isPlaying) 1.05f else 1.0f
                    scaleY = if (isPlaying) 1.05f else 1.0f
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_replay_15_24),
                contentDescription = "Seek backward 15s",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Play/Pause with enhanced animations
        FloatingActionButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp).scale(playPauseScale),
            containerColor = fabBackgroundColor,
            contentColor = fabContentColor,
            shape = CircleShape,
        ) {
            Icon(
                painter = painterResource(playPauseIcon),
                contentDescription = playPauseContentDescription,
                modifier = Modifier.size(36.dp),
            )
        }

        // Seek forward 30s
        IconButton(
            onClick = onSeekForward,
            modifier =
                Modifier.size(56.dp).graphicsLayer {
                    scaleX = if (isPlaying) 1.05f else 1.0f
                    scaleY = if (isPlaying) 1.05f else 1.0f
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_forward_30_24),
                contentDescription = "Seek forward 30s",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Next chapter
        IconButton(
            onClick = onNextChapter,
            modifier =
                Modifier.size(56.dp).graphicsLayer {
                    scaleX = if (isPlaying) 1.05f else 1.0f
                    scaleY = if (isPlaying) 1.05f else 1.0f
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next_24),
                contentDescription = "Next chapter",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    // Speed and sleep timer controls with animations
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        // Speed control with animation
        IconButton(
            onClick = onSpeedClick,
            modifier =
                Modifier.scale(speedButtonScale)
                    .clip(CircleShape)
                    .background(
                        if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                    ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_headphones_24),
                    contentDescription = "Speed",
                    tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Sleep timer control with animation
        IconButton(
            onClick = onSleepTimerClick,
            modifier =
                Modifier.scale(timerButtonScale)
                    .clip(CircleShape)
                    .background(
                        if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                    ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.ic_book_24), contentDescription = "Sleep timer", tint = timerButtonColor)
                sleepTimerText?.let { text -> Text(text = text, style = MaterialTheme.typography.bodySmall, color = timerButtonColor) }
            }
        }

        // Bookmark control with animation
        IconButton(
            onClick = onBookmarkClick,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = 1.0f
                    scaleY = 1.0f
                },
        ) {
            Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Add bookmark", tint = MaterialTheme.colorScheme.onSurface)
        }

        // Open bookmarks list with animation
        IconButton(
            onClick = onShowBookmarksClick,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = 1.0f
                    scaleY = 1.0f
                },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "Show bookmarks",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
