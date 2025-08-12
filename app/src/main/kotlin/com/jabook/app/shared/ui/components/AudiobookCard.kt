package com.jabook.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.DownloadStatus

/** Карточка аудиокниги для отображения в списке или сетке Адаптируется под разные размеры экрана и API уровни */
@Composable
fun AudiobookCard(
    audiobook: Audiobook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    cardStyle: AudiobookCardStyle = AudiobookCardStyle.Grid,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        when (cardStyle) {
            AudiobookCardStyle.Grid -> {
                GridCardContent(audiobook = audiobook, showProgress = showProgress)
            }
            AudiobookCardStyle.List -> {
                ListCardContent(audiobook = audiobook, showProgress = showProgress)
            }
        }
    }
}

@Composable
private fun GridCardContent(audiobook: Audiobook, showProgress: Boolean) {
    Column(modifier = Modifier.padding(12.dp)) {
        // Обложка книги
        Box(
            modifier =
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = audiobook.coverUrl,
                contentDescription = "Обложка: ${audiobook.title}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )

            // Индикатор статуса загрузки
            if (audiobook.downloadStatus != DownloadStatus.NOT_DOWNLOADED) {
                DownloadStatusIndicator(status = audiobook.downloadStatus, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }

        // Информация о книге
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Прогресс прослушивания
            if (showProgress && audiobook.progressPercentage > 0) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    LinearProgressIndicator(
                        progress = audiobook.progressPercentage,
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "${(audiobook.progressPercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListCardContent(audiobook: Audiobook, showProgress: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Обложка книги
        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(
                model = audiobook.coverUrl,
                contentDescription = "Обложка: ${audiobook.title}",
                modifier = Modifier.size(80.dp),
                contentScale = ContentScale.Crop,
            )

            // Индикатор статуса загрузки
            if (audiobook.downloadStatus != DownloadStatus.NOT_DOWNLOADED) {
                DownloadStatusIndicator(status = audiobook.downloadStatus, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }

        // Информация о книге
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            // Длительность
            if (audiobook.durationFormatted.isNotEmpty()) {
                Text(
                    text = audiobook.durationFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Прогресс прослушивания
            if (showProgress && audiobook.progressPercentage > 0) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    LinearProgressIndicator(
                        progress = audiobook.progressPercentage,
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "${(audiobook.progressPercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusIndicator(status: DownloadStatus, modifier: Modifier = Modifier) {
    val (backgroundColor, text) =
        when (status) {
            DownloadStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.surface to ""
            DownloadStatus.QUEUED -> MaterialTheme.colorScheme.tertiary to "⏳"
            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary to "↓"
            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary to "✓"
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error to "!"
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.outline to "⏸"
            DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.surface to ""
        }

    if (text.isNotEmpty()) {
        Box(
            modifier = modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Стили отображения карточек аудиокниг */
enum class AudiobookCardStyle {
    Grid, // Сетка с квадратными карточками
    List, // Список с горизонтальными карточками
}
