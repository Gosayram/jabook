package com.jabook.app.features.discovery.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.R
import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.shared.utils.formatFileSize
import kotlinx.coroutines.launch

@Composable
fun AudiobookCover(modifier: Modifier = Modifier) {
  AsyncImage(
    model = null, // coverImageUrl not available in RuTrackerAudiobook
    contentDescription = stringResource(R.string.audiobook_cover),
    modifier = modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.ic_book_24),
    error = painterResource(R.drawable.ic_book_24),
  )
}

@Composable
fun AudiobookInfo(
  title: String,
  author: String,
  category: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Text(
      text = author,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    Text(
      text = category,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun ActionButtons(
  magnetUri: String?,
  torrentUrl: String?,
  isGuestMode: Boolean,
  audiobook: RuTrackerAudiobook,
  onDownload: (RuTrackerAudiobook) -> Unit,
) {
  val clipboardManager = LocalClipboard.current
  val coroutineScope = rememberCoroutineScope()

  Row(
    modifier = Modifier.padding(top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (magnetUri != null) {
      IconButton(
        onClick = {
          coroutineScope.launch {
            val clipboard = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
              ClipData.newPlainText("Magnet Link", magnetUri),
            )
          }
        },
      ) {
        Icon(
          Icons.Default.ContentCopy,
          contentDescription = "Copy magnet link",
        )
      }
    }
    if (!isGuestMode && torrentUrl != null) {
      IconButton(
        onClick = {
          onDownload(audiobook)
        },
      ) {
        Icon(
          Icons.Default.Download,
          contentDescription = "Download .torrent",
        )
      }
    }
  }
}

@Composable
fun MetadataRow(
  sizeBytes: Long,
  seeders: Int,
  leechers: Int,
  rating: Float?,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Size
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Default.CloudDownload,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = formatFileSize(sizeBytes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Seeds/Leechers
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Default.People,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = if (seeders > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "$seeders/$leechers",
        style = MaterialTheme.typography.bodySmall,
        color = if (seeders > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Rating
    if ((rating ?: 0f) > 0f) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Star,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = Color(0xFFFFC107),
        )
        Text(
          text = "%.1f".format(rating),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun AdditionalInfo(
  duration: String?,
  quality: String?,
) {
  if (!duration.isNullOrEmpty() || !quality.isNullOrEmpty()) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(top = 4.dp),
    ) {
      if (!duration.isNullOrEmpty()) {
        Text(
          text = duration,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (!quality.isNullOrEmpty()) {
        Text(
          text = "• $quality",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun AudiobookSearchResultCard(
  audiobook: RuTrackerAudiobook,
  onClick: () -> Unit,
  isGuestMode: Boolean,
  onDownload: (RuTrackerAudiobook) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth().clickable { onClick() },
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AudiobookCover()

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        AudiobookInfo(
          title = audiobook.title,
          author = audiobook.author,
          category = audiobook.category,
        )

        Spacer(modifier = Modifier.height(4.dp))

        ActionButtons(
          magnetUri = audiobook.magnetUri,
          torrentUrl = audiobook.torrentUrl,
          isGuestMode = isGuestMode,
          audiobook = audiobook,
          onDownload = onDownload,
        )

        MetadataRow(
          sizeBytes = audiobook.sizeBytes,
          seeders = audiobook.seeders,
          leechers = audiobook.leechers,
          rating = audiobook.rating,
        )

        AdditionalInfo(
          duration = audiobook.duration,
          quality = audiobook.quality,
        )
      }
    }
  }
}
