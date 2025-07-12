package com.jabook.app.features.player.presentation.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.R
import com.jabook.app.core.domain.model.Audiobook

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

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onBarClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Book icon placeholder
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Title and author
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = audiobook.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = audiobook.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Play/Pause button
            Box(
                modifier =
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable {
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Progress bar
        val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
        )
    }
}
