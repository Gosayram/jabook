package com.jabook.app.features.player.presentation.components

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
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    bookmarkPositions: List<Long> = emptyList(),
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isUserDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val progress by
        remember(currentPosition, duration, isUserDragging, dragPosition) {
            derivedStateOf {
                if (duration > 0) {
                    if (isUserDragging) dragPosition else currentPosition.toFloat() / duration.toFloat()
                } else 0f
            }
        }

    // Memoize formatted time strings to reduce recomposition
    val currentTimeText by remember(currentPosition) { derivedStateOf { formatTime(currentPosition) } }

    val durationText by remember(duration) { derivedStateOf { formatTime(duration) } }

    Column(modifier = modifier) {
        // Progress slider
        Box {
            Slider(
                value = progress,
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

            // Draw bookmark markers as overlay - only if needed
            if (duration > 0 && bookmarkPositions.isNotEmpty()) {
                val bookmarkColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxWidth().matchParentSize()) {
                    val sliderWidth = size.width
                    val sliderHeight = size.height
                    val markerHeight = sliderHeight / 2f

                    // Use forEach instead of map for better performance
                    bookmarkPositions.forEach { pos ->
                        val x = (pos.toFloat() / duration) * sliderWidth
                        drawLine(
                            color = bookmarkColor,
                            start = androidx.compose.ui.geometry.Offset(x, sliderHeight - markerHeight),
                            end = androidx.compose.ui.geometry.Offset(x, sliderHeight),
                            strokeWidth = 2f,
                        )
                    }
                }
            }
        }

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = currentTimeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = durationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
