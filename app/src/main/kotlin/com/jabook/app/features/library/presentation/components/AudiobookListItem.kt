package com.jabook.app.features.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.R
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.DownloadStatus
import com.jabook.app.shared.ui.theme.JaBookTheme
import java.util.Date

/** List item component for displaying an audiobook in the library. Shows cover, title, author, progress, and action buttons. */
@Composable
fun AudiobookListItem(
    audiobook: Audiobook,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onRatingChange: (Float) -> Unit,
    onMarkCompleted: () -> Unit,
    onResetPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cover Image
            AudiobookCover(
                coverUrl = audiobook.coverUrl,
                localCoverPath = audiobook.localCoverPath,
                title = audiobook.title,
                modifier = Modifier.size(64.dp),
            )

            // Content
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Title
                Text(
                    text = audiobook.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Author
                Text(
                    text = audiobook.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Narrator (if available)
                if (!audiobook.narrator.isNullOrBlank()) {
                    Text(
                        text = "Narrator: ${audiobook.narrator}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Progress Bar
                if (audiobook.progressPercentage > 0) {
                    LinearProgressIndicator(
                        progress = { audiobook.progressPercentage },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                // Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Duration and Progress
                    Text(
                        text =
                        if (audiobook.progressPercentage > 0) {
                            "${audiobook.currentPositionFormatted} / ${audiobook.durationFormatted}"
                        } else {
                            audiobook.durationFormatted
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Download Status Badge
                    if (audiobook.downloadStatus != DownloadStatus.NOT_DOWNLOADED) {
                        DownloadStatusBadge(status = audiobook.downloadStatus, progress = audiobook.downloadProgress)
                    }
                }
            }

            // Action Buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Favorite Button
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector =
                        if (audiobook.isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription =
                        if (audiobook.isFavorite) {
                            stringResource(R.string.remove_from_favorites)
                        } else {
                            stringResource(R.string.add_to_favorites)
                        },
                        tint =
                        if (audiobook.isFavorite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                // More Options Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!audiobook.isCompleted) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_as_completed)) },
                                onClick = {
                                    onMarkCompleted()
                                    showMenu = false
                                },
                            )
                        }

                        if (audiobook.progressPercentage > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset_playback)) },
                                onClick = {
                                    onResetPlayback()
                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Component for displaying audiobook cover image. */
@Composable
private fun AudiobookCover(coverUrl: String?, localCoverPath: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val imageUrl = localCoverPath ?: coverUrl

        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.cd_book_cover),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Placeholder with first letter of title
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Component for displaying download status badge. */
@Composable
private fun DownloadStatusBadge(status: DownloadStatus, progress: Float) {
    val (text, color) =
        when (status) {
            DownloadStatus.QUEUED -> "Queued" to MaterialTheme.colorScheme.secondary
            DownloadStatus.DOWNLOADING -> "Downloading" to MaterialTheme.colorScheme.primary
            DownloadStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.tertiary
            DownloadStatus.COMPLETED -> "Downloaded" to MaterialTheme.colorScheme.primary
            DownloadStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
            DownloadStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
            DownloadStatus.NOT_DOWNLOADED -> "" to Color.Transparent
        }

    if (text.isNotEmpty()) {
        Text(
            text =
            if (status == DownloadStatus.DOWNLOADING) {
                "$text ${(progress * 100).toInt()}%"
            } else {
                text
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier =
            Modifier.background(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudiobookListItemPreview() {
    JaBookTheme {
        val sampleAudiobook =
            Audiobook(
                id = "1",
                title = "The Fellowship of the Ring",
                author = "J.R.R. Tolkien",
                narrator = "Rob Inglis",
                category = "Fantasy",
                // 19 hours
                durationMs = 19 * 60 * 60 * 1000L,
                // 2 hours
                currentPositionMs = 2 * 60 * 60 * 1000L,
                isFavorite = true,
                downloadStatus = DownloadStatus.COMPLETED,
                lastPlayedAt = Date(),
            )

        AudiobookListItem(
            audiobook = sampleAudiobook,
            onClick = {},
            onFavoriteClick = {},
            onRatingChange = {},
            onMarkCompleted = {},
            onResetPlayback = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
