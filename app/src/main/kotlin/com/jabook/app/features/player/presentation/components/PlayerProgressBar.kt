package com.jabook.app.features.player.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.jabook.app.shared.ui.theme.JaBookAnimations
import java.util.Locale

@Composable
fun PlayerProgressBar(
  modifier: Modifier = Modifier,
  currentPosition: Long,
  duration: Long,
  bookmarkPositions: List<Long> = emptyList(),
  onSeekTo: (Long) -> Unit,
) {
  var isUserDragging by remember { mutableStateOf(false) }
  var dragPosition by remember { mutableFloatStateOf(0f) }

  val progress by
    remember(currentPosition, duration, isUserDragging, dragPosition) {
      derivedStateOf {
        if (duration > 0) {
          if (isUserDragging) dragPosition else currentPosition.toFloat() / duration.toFloat()
        } else {
          0f
        }
      }
    }

  // Animated progress for smooth transitions
  val animatedProgress by
    animateFloatAsState(
      targetValue = progress,
      animationSpec =
        if (isUserDragging) {
          tween(0) // No animation during dragging
        } else {
          tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.STANDARD_EASING)
        },
      label = "progressAnimation",
    )

  // Memoize formatted time strings to reduce recomposition
  val currentTimeText by remember(currentPosition) { derivedStateOf { formatTime(currentPosition) } }
  val durationText by remember(duration) { derivedStateOf { formatTime(duration) } }

  // Animated bookmark visibility
  val bookmarkAlpha by
    animateFloatAsState(
      targetValue = if (bookmarkPositions.isNotEmpty()) 1f else 0f,
      animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
      label = "bookmarkAlpha",
    )

  Column(modifier = modifier) {
    // Progress slider with overlay
    Box {
      Slider(
        value = if (isUserDragging) dragPosition else animatedProgress,
        onValueChange = { value ->
          isUserDragging = true
          dragPosition = value
        },
        onValueChangeFinished = {
          isUserDragging = false
          val seekPosition = (dragPosition * duration).toLong()
          onSeekTo(seekPosition)
        },
        modifier = Modifier.fillMaxWidth(),
      )

      // Draw bookmark markers as overlay with animation
      if (bookmarkPositions.isNotEmpty() && duration > 0) {
        val bookmarkColor = MaterialTheme.colorScheme.secondary
        Canvas(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), // Account for slider thumb
        ) {
          drawBookmarkMarkers(
            bookmarkPositions = bookmarkPositions,
            duration = duration,
            alpha = bookmarkAlpha,
            color = bookmarkColor,
          )
        }
      }
    }

    // Time display with animated text
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = currentTimeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = durationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Draw bookmark markers on the progress bar with animation support */
private fun DrawScope.drawBookmarkMarkers(
  bookmarkPositions: List<Long>,
  duration: Long,
  alpha: Float,
  color: Color,
) {
  val trackWidth = size.width
  val trackHeight = size.height
  val markerHeight = trackHeight * 0.8f
  val markerWidth = 3.dp.toPx()

  bookmarkPositions.forEach { position ->
    val relativePosition = position.toFloat() / duration.toFloat()
    val x = relativePosition * trackWidth

    // Draw bookmark marker with animation
    drawRect(
      color = color.copy(alpha = alpha),
      topLeft =
        androidx.compose.ui.geometry
          .Offset(x = x - markerWidth / 2, y = (trackHeight - markerHeight) / 2),
      size =
        androidx.compose.ui.geometry
          .Size(width = markerWidth, height = markerHeight),
    )
  }
}

/** Format time in mm:ss or hh:mm:ss format */
private fun formatTime(timeMs: Long): String {
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
