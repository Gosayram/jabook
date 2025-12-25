// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.feature.torrent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentState

/**
 * Download item component with progress and actions
 */
@Composable
fun TorrentDownloadItem(
    download: TorrentDownload,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onItemClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Title and state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))

                    // State badge
                    StateBadge(state = download.state)
                }

                // Actions
                Row {
                    // Pause/Resume button
                    if (download.state in
                        listOf(
                            TorrentState.DOWNLOADING,
                            TorrentState.SEEDING,
                            TorrentState.STREAMING,
                        )
                    ) {
                        IconButton(onClick = onPauseClick) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = stringResource(R.string.pause_download),
                            )
                        }
                    } else if (download.state == TorrentState.PAUSED) {
                        IconButton(onClick = onResumeClick) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.resume_download),
                            )
                        }
                    }

                    // Delete button
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_download),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Progress percentage
                Text(
                    text = "${(download.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Download speed
                if (download.state == TorrentState.DOWNLOADING) {
                    Text(
                        text = "↓ ${formatSpeed(download.downloadSpeed)}/s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Upload speed
                if (download.uploadSpeed > 0) {
                    Text(
                        text = "↑ ${formatSpeed(download.uploadSpeed)}/s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                // ETA
                if (download.eta > 0 &&
                    download.state in
                    listOf(
                        TorrentState.DOWNLOADING,
                        TorrentState.STREAMING,
                    )
                ) {
                    Text(
                        text = formatEta(download.eta),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Peers/Seeds info
            if (download.state in
                listOf(
                    TorrentState.DOWNLOADING,
                    TorrentState.SEEDING,
                    TorrentState.STREAMING,
                )
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        stringResource(
                            R.string.peers_seeds,
                            download.numPeers,
                            download.numSeeds,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * State badge component
 */
@Composable
private fun StateBadge(
    state: TorrentState,
    modifier: Modifier = Modifier,
) {
    val (text, color) =
        when (state) {
            TorrentState.DOWNLOADING -> stringResource(R.string.downloading_state) to MaterialTheme.colorScheme.primary
            TorrentState.PAUSED -> stringResource(R.string.paused_state) to MaterialTheme.colorScheme.onSurfaceVariant
            TorrentState.COMPLETED -> stringResource(R.string.completed_state) to MaterialTheme.colorScheme.tertiary
            TorrentState.ERROR -> stringResource(R.string.error_state) to MaterialTheme.colorScheme.error
            TorrentState.SEEDING -> stringResource(R.string.seeding_state) to MaterialTheme.colorScheme.tertiary
            TorrentState.STREAMING -> stringResource(R.string.streaming_state) to MaterialTheme.colorScheme.secondary
            TorrentState.CHECKING -> stringResource(R.string.checking_state) to MaterialTheme.colorScheme.onSurfaceVariant
            TorrentState.DOWNLOADING_METADATA -> stringResource(R.string.metadata_state) to MaterialTheme.colorScheme.onSurfaceVariant
            TorrentState.QUEUED -> stringResource(R.string.queued_state) to MaterialTheme.colorScheme.onSurfaceVariant
            TorrentState.STOPPED -> stringResource(R.string.stopped_state) to MaterialTheme.colorScheme.onSurfaceVariant
        }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * Format speed in human-readable format
 */
@Composable
private fun formatSpeed(bytesPerSecond: Long): String {
    val kb = bytesPerSecond / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> stringResource(R.string.size_gb, gb)
        mb >= 1.0 -> stringResource(R.string.size_mb, mb)
        kb >= 1.0 -> stringResource(R.string.size_kb, kb)
        else -> stringResource(R.string.size_bytes, bytesPerSecond)
    }
}

/**
 * Format ETA in human-readable format
 */
@Composable
private fun formatEta(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        hours > 0 -> stringResource(R.string.duration_hm, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_m, minutes)
        else -> stringResource(R.string.duration_less_minute)
    }
}
