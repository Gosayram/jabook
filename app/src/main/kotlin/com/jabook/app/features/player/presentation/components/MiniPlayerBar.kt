package com.jabook.app.features.player.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.R
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.shared.ui.theme.JaBookAnimations

@Composable
fun MiniPlayerBar(
    audiobook: Audiobook?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (audiobook == null) return

    // Memoize computed values
    val playPauseIcon by remember(isPlaying) { derivedStateOf { if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24 } }
    val playPauseContentDescription by remember(isPlaying) { derivedStateOf { if (isPlaying) "Pause" else "Play" } }

    val progress by
        remember(currentPosition, duration) {
            derivedStateOf { if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f }
        }

    // Animations
    val animatedProgress by
        animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.STANDARD_EASING),
            label = "progressAnimation",
        )

    val playButtonScale by
        animateFloatAsState(
            targetValue = if (isPlaying) 1.1f else 1.0f,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "playButtonScale",
        )

    val playButtonBackgroundColor by
        animateColorAsState(
            targetValue = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "playButtonBackgroundColor",
        )

    val playButtonContentColor by
        animateColorAsState(
            targetValue = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "playButtonContentColor",
        )

    val barElevation by
        animateFloatAsState(
            targetValue = if (isPlaying) 4f else 2f,
            animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
            label = "barElevation",
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onBarClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer { shadowElevation = barElevation }
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Book icon placeholder with subtle animation
            Box(
                modifier =
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).graphicsLayer {
                        scaleX = if (isPlaying) 1.05f else 1.0f
                        scaleY = if (isPlaying) 1.05f else 1.0f
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Title and author with animation
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp).graphicsLayer { alpha = if (isPlaying) 1.0f else 0.8f }) {
                Text(
                    text = audiobook.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = audiobook.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Play/Pause button with enhanced animations
            Box(
                modifier =
                    Modifier.size(40.dp).scale(playButtonScale).clip(CircleShape).background(playButtonBackgroundColor).clickable {
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(playPauseIcon),
                    contentDescription = playPauseContentDescription,
                    tint = playButtonContentColor,
                )
            }
        }

        // Progress bar with smooth animation
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
    }
}
