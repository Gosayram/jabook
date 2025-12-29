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

package com.jabook.app.jabook.compose.feature.topic

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.HtmlToAnnotatedString
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails

/**
 * Topic Screen - displays detailed information about a RuTracker topic.
 *
 * @param topicId Topic ID to display
 * @param onNavigateBack Callback to navigate back
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION") // hiltViewModel is from correct package but marked deprecated in some versions
@Composable
fun TopicScreen(
    topicId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authStatus by viewModel.authStatus.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show messages
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState) {
                            is TopicUiState.Success -> (uiState as TopicUiState.Success).details.title
                            else -> stringResource(R.string.bookDetails)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(snackbarData = snackbarData)
            }
        },
    ) { padding ->
        when (val state = uiState) {
            is TopicUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is TopicUiState.Success -> {
                TopicDetailsContent(
                    details = state.details,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding),
                )
            }

            is TopicUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = viewModel::retry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                )
            }
        }
    }
}

/**
 * Content displaying topic details.
 */
@Composable
private fun TopicDetailsContent(
    details: TopicDetails,
    viewModel: TopicViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showDownloadMenu by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cover image (if available) - adaptive size
        details.coverUrl?.let { rawCoverUrl ->
            item {
                val context = LocalContext.current
                val density = context.resources.displayMetrics.density
                val cornerRadiusPx = 16f * density // 16dp rounded corners for topic detail view

                // Normalize URL - handle protocol-relative URLs and ensure absolute URL
                val coverUrl =
                    when {
                        rawCoverUrl.startsWith("http://") || rawCoverUrl.startsWith("https://") -> rawCoverUrl
                        rawCoverUrl.startsWith("//") -> "https:$rawCoverUrl"
                        else -> rawCoverUrl // Keep as is, Coil will handle relative URLs if baseUri is set
                    }

                // Use high priority for detail screen - cover must load immediately
                // Note: Coil3 handles caching automatically via ImageLoader config
                val imageRequest =
                    coil3.request.ImageRequest
                        .Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .allowHardware(true)
                        .transformations(RoundedCornersTransformation(cornerRadiusPx))
                        .placeholder(
                            ColorDrawable(
                                MaterialTheme.colorScheme.surfaceVariant.toArgb(),
                            ).asImage(),
                        ).error(
                            ColorDrawable(
                                MaterialTheme.colorScheme.error.toArgb(),
                            ).asImage(),
                        ).fallback(
                            ColorDrawable(
                                MaterialTheme.colorScheme.surfaceVariant.toArgb(),
                            ).asImage(),
                        ).build()

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Adaptive cover size: smaller in landscape, larger in portrait
                    val coverWidthFraction = if (isLandscape) 0.4f else 0.6f
                    coil3.compose.AsyncImage(
                        model = imageRequest,
                        contentDescription = details.title,
                        modifier =
                            Modifier
                                .fillMaxWidth(coverWidthFraction)
                                .aspectRatio(0.7f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
        }

        // Compact info row: Author, Performer, Duration, Size
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Author, Performer, and Series in compact layout
                if (!details.author.isNullOrBlank() || !details.performer.isNullOrBlank() || !details.series.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Author and Performer in one row
                        if (!details.author.isNullOrBlank() || !details.performer.isNullOrBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                details.author?.let { author ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.authorLabel),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = author,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                details.performer?.let { performer ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.performerLabel),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = performer,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                        // Series/Cycle
                        details.series?.let { series ->
                            Column {
                                Text(
                                    text = stringResource(R.string.seriesLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = series,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                // Seeders/Leechers + Size + Duration in one row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeedersLeechersChip(
                        seeders = details.seeders,
                        leechers = details.leechers,
                    )

                    Text(
                        text = details.size,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    details.duration?.let { duration ->
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Download button with menu
        item {
            if (details.magnetUrl != null || details.torrentUrl.isNotBlank()) {
                Box {
                    FilledTonalButton(
                        onClick = { showDownloadMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.downloadLabel))
                    }

                    DropdownMenu(
                        expanded = showDownloadMenu,
                        onDismissRequest = { showDownloadMenu = false },
                    ) {
                        // 1. Download torrent release (content) - highest priority
                        if (details.magnetUrl != null || details.torrentUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloadTorrentRelease)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.downloadTorrentRelease(
                                        details.magnetUrl,
                                        details.torrentUrl,
                                    )
                                    showDownloadMenu = false
                                },
                            )
                        }

                        // 2. Download via magnet link (if available)
                        if (details.magnetUrl != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloadViaMagnet)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.downloadViaMagnet(details.magnetUrl)
                                    showDownloadMenu = false
                                },
                            )
                        }

                        // 3. Download torrent file (.torrent)
                        if (details.torrentUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloadTorrentFile)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.downloadTorrentFile()
                                    showDownloadMenu = false
                                },
                            )
                        }

                        // 4. Copy magnet link to clipboard
                        if (details.magnetUrl != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copyMagnetLink)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.copyMagnetLink(details.magnetUrl)
                                    showDownloadMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Bitrate and Codec (Duration already shown in compact row)
        if (!details.bitrate.isNullOrBlank() || !details.audioCodec.isNullOrBlank()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    details.bitrate?.let { bitrate ->
                        Text(
                            text = stringResource(R.string.bitrateFormat, bitrate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    details.audioCodec?.let { codec ->
                        Text(
                            text = stringResource(R.string.formatFormat, codec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Description and Comments section (side by side on larger screens)
        if (!details.description.isNullOrBlank() || details.comments.isNotEmpty()) {
            item {
                DescriptionAndCommentsSection(
                    description = details.description,
                    descriptionHtml = details.descriptionHtml,
                    comments = details.comments,
                )
            }
        }

        // MediaInfo Section
        details.mediaInfo?.let { mediaInfo ->
            // Video tracks
            if (mediaInfo.video.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.videoLabel),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(
                    items = mediaInfo.video,
                    key = { video -> "${video.codec}_${video.resolution}_${video.bitrate}" },
                ) { video ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            video.codec?.let {
                                Text(
                                    stringResource(R.string.codecLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            video.resolution?.let {
                                Text(
                                    stringResource(R.string.resolutionLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            video.bitrate?.let {
                                Text(
                                    stringResource(R.string.bitrateLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            // Audio tracks
            if (mediaInfo.audio.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.audioLabel),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(
                    items = mediaInfo.audio,
                    key = { audio -> "${audio.codec}_${audio.bitrate}_${audio.channels}_${audio.language}" },
                ) { audio ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            audio.codec?.let {
                                Text(
                                    stringResource(R.string.codecLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            audio.bitrate?.let {
                                Text(
                                    stringResource(R.string.bitrateLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            audio.channels?.let {
                                Text(
                                    stringResource(R.string.channelsLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            audio.language?.let {
                                Text(
                                    stringResource(R.string.languageLabel, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Genres section removed - not needed as files list
    }
}

/**
 * Seeders and Leechers chips.
 */
@Composable
private fun SeedersLeechersChip(
    seeders: Int,
    leechers: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {},
            label = { Text("$seeders") },
            leadingIcon = {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.seeders),
                    tint = Color(0xFF4CAF50), // Green
                )
            },
        )

        AssistChip(
            onClick = {},
            label = { Text("$leechers") },
            leadingIcon = {
                Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(R.string.leechers),
                    tint = Color(0xFFFF9800), // Orange
                )
            },
        )
    }
}

/**
 * Description and Comments section with adaptive layout.
 * Shows side by side on larger screens, stacked on smaller screens.
 */
@Composable
private fun DescriptionAndCommentsSection(
    description: String?,
    descriptionHtml: String? = null,
    comments: List<com.jabook.app.jabook.compose.data.remote.model.Comment>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    val isLargeScreen = configuration.screenWidthDp >= 600
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Use two-column layout for large screens or landscape orientation
    if ((isLargeScreen || isLandscape) && !description.isNullOrBlank() && comments.isNotEmpty()) {
        // Two column layout for larger screens or landscape
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Description column
            Column(
                modifier = Modifier.weight(1f),
            ) {
                if (!description.isNullOrBlank()) {
                    ExpandableDescription(
                        description = description,
                        descriptionHtml = descriptionHtml,
                    )
                }
            }

            // Comments column
            Column(
                modifier = Modifier.weight(1f),
            ) {
                ExpandableComments(comments = comments)
            }
        }
    } else {
        // Single column layout for smaller screens or when only one section exists
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!description.isNullOrBlank()) {
                ExpandableDescription(
                    description = description,
                    descriptionHtml = descriptionHtml,
                )
            }
            if (comments.isNotEmpty()) {
                ExpandableComments(comments = comments)
            }
        }
    }
}

/**
 * Expandable description with collapse/expand functionality.
 */
@Composable
private fun ExpandableDescription(
    description: String,
    descriptionHtml: String? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val maxPreviewLength = 150
    val shouldShowExpand = description.length > maxPreviewLength
    val context = LocalContext.current

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.titleSmall,
            )

            if (shouldShowExpand) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(0.dp),
                ) {
                    Text(
                        if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Use HTML if available, otherwise use plain text
        val linkColor = MaterialTheme.colorScheme.primary
        val annotatedText =
            remember(descriptionHtml, description, expanded, linkColor) {
                if (descriptionHtml != null && expanded) {
                    HtmlToAnnotatedString.convert(
                        descriptionHtml,
                        linkColor = linkColor,
                    )
                } else {
                    val text =
                        if (expanded) {
                            description
                                .replace(Regex("[ \t]+"), " ")
                                .replace(Regex("\n{3,}"), "\n\n")
                                .trim()
                        } else {
                            description
                                .take(maxPreviewLength)
                                .replace(Regex("[ \t]+"), " ")
                                .trim() + if (shouldShowExpand) "..." else ""
                        }
                    AnnotatedString(text)
                }
            }

        Text(
            text = annotatedText,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * File list item showing file name and size.
 */
@Composable
private fun FileListItem(
    file: String,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(file) },
        leadingContent = {
            Icon(
                Icons.Filled.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = modifier,
    )
}

/**
 * Expandable comments section.
 */
@Composable
private fun ExpandableComments(
    comments: List<com.jabook.app.jabook.compose.data.remote.model.Comment>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.commentsLabel, comments.size),
                style = MaterialTheme.typography.titleSmall,
            )

            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(0.dp),
            ) {
                Text(
                    if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                comments.forEach { comment ->
                    CommentItem(comment = comment)
                }
            }
        }
    }
}

/**
 * Single comment item.
 */
@Composable
private fun CommentItem(
    comment: com.jabook.app.jabook.compose.data.remote.model.Comment,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar
            Box(
                modifier =
                    Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .aspectRatio(1f),
            ) {
                comment.avatarUrl?.let { url ->
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .transformations(CircleCropTransformation())
                                .placeholder(ColorDrawable(MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()))
                                .error(ColorDrawable(MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()))
                                .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: run {
                    // Placeholder when no avatar - show first letter of username
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = comment.author.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Comment content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Author and date
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = comment.author,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = comment.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Comment text with clickable links
                val linkColor = MaterialTheme.colorScheme.primary
                val annotatedText =
                    remember(comment.html, comment.text, linkColor) {
                        if (comment.html != null) {
                            HtmlToAnnotatedString.convert(
                                comment.html,
                                linkColor = linkColor,
                            )
                        } else {
                            val text =
                                comment.text
                                    .replace(Regex("[ \t]+"), " ")
                                    .replace(Regex("\n{3,}"), "\n\n")
                                    .trim()
                            AnnotatedString(text)
                        }
                    }

                Text(
                    text = annotatedText,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Error content with retry button.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message.ifEmpty { stringResource(R.string.anErrorOccurred) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
