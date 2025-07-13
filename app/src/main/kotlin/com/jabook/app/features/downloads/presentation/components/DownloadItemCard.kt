package com.jabook.app.features.downloads.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.R
import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentStatus
import com.jabook.app.shared.utils.formatFileSize

@Composable
fun DownloadItemCard(
    download: DownloadProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadItemHeader(download, onPause, onResume, onCancel, onRetry)
            DownloadItemStatus(download)
            DownloadItemProgress(download)
            DownloadItemInfo(download)
            DownloadItemPeers(download)
        }
    }
}

@Composable
private fun DownloadItemHeader(
    download: DownloadProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = download.audiobookId,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (download.status) {
                TorrentStatus.DOWNLOADING -> {
                    IconButton(onClick = onPause) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(R.string.pause_download),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TorrentStatus.PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.resume_download),
                            tint = Color(0xFF4CAF50),
                        )
                    }
                }
                TorrentStatus.ERROR, TorrentStatus.STOPPED -> {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.retry_download),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                else -> {}
            }
            if (download.status != TorrentStatus.COMPLETED) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = stringResource(R.string.cancel_download),
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemStatus(download: DownloadProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = getStatusText(download.status),
            style = MaterialTheme.typography.bodySmall,
            color = getStatusColor(download.status),
        )
        if (
            download.status == TorrentStatus.DOWNLOADING ||
            download.status == TorrentStatus.PAUSED ||
            download.status == TorrentStatus.SEEDING
        ) {
            Text(
                text = "${(download.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadItemProgress(download: DownloadProgress) {
    if (
        download.status == TorrentStatus.DOWNLOADING ||
        download.status == TorrentStatus.PAUSED ||
        download.status == TorrentStatus.SEEDING
    ) {
        LinearProgressIndicator(
            progress = { download.progress },
            modifier = Modifier.fillMaxWidth(),
            color = getProgressColor(download.status),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun DownloadItemInfo(download: DownloadProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (download.total > 0) {
                "${formatFileSize(download.downloaded)} / ${formatFileSize(download.total)}"
            } else {
                formatFileSize(download.downloaded)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (download.status == TorrentStatus.DOWNLOADING && download.downloadSpeed > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${formatFileSize(download.downloadSpeed)}/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (download.eta > 0) {
                    val etaText = formatETA(download.eta)
                    Text(
                        text = etaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemPeers(download: DownloadProgress) {
    if (download.status == TorrentStatus.DOWNLOADING || download.status == TorrentStatus.SEEDING) {
        Text(
            text = stringResource(R.string.seeders_leechers, download.seeders, download.leechers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun getStatusText(status: TorrentStatus): String {
    return when (status) {
        TorrentStatus.DOWNLOADING -> stringResource(R.string.status_downloading)
        TorrentStatus.COMPLETED -> stringResource(R.string.status_completed)
        TorrentStatus.PAUSED -> stringResource(R.string.status_paused)
        TorrentStatus.SEEDING -> stringResource(R.string.status_seeding)
        TorrentStatus.ERROR -> stringResource(R.string.status_error)
        TorrentStatus.STOPPED -> stringResource(R.string.status_stopped)
        TorrentStatus.IDLE -> stringResource(R.string.status_idle)
        TorrentStatus.PENDING -> stringResource(R.string.status_pending)
    }
}

@Composable
private fun getStatusColor(status: TorrentStatus): Color {
    return when (status) {
        TorrentStatus.DOWNLOADING -> Color(0xFF2196F3) // Blue
        TorrentStatus.COMPLETED -> Color(0xFF4CAF50) // Green
        TorrentStatus.PAUSED -> Color(0xFFFF9800) // Orange
        TorrentStatus.SEEDING -> Color(0xFF9C27B0) // Purple
        TorrentStatus.ERROR -> Color(0xFFF44336) // Red
        TorrentStatus.STOPPED -> Color(0xFF757575) // Gray
        TorrentStatus.IDLE -> Color(0xFF757575) // Gray
        TorrentStatus.PENDING -> Color(0xFF757575) // Gray
    }
}

@Composable
private fun getProgressColor(status: TorrentStatus): Color {
    return when (status) {
        TorrentStatus.DOWNLOADING -> Color(0xFF2196F3) // Blue
        TorrentStatus.PAUSED -> Color(0xFFFF9800) // Orange
        TorrentStatus.SEEDING -> Color(0xFF9C27B0) // Purple
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun formatETA(etaSeconds: Long): String {
    return when {
        etaSeconds < 60 -> "${etaSeconds}s"
        etaSeconds < 3600 -> "${etaSeconds / 60}m"
        etaSeconds < 86400 -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        else -> "${etaSeconds / 86400}d"
    }
}
