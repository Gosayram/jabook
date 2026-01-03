// Copyright 2026 Jabook Contributors
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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.HtmlToAnnotatedString
import com.jabook.app.jabook.compose.designsystem.component.RemoteImage
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails

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
    onNavigateToTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
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
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    when (uiState) {
                        is TopicUiState.Success -> {
                            val context = LocalContext.current
                            IconButton(
                                onClick = {
                                    val url = viewModel.getTopicUrl()
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                            ) {
                                Icon(
                                    Icons.Default.OpenInBrowser,
                                    contentDescription = stringResource(R.string.openInBrowserButton),
                                )
                            }
                        }
                        else -> {}
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
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshTopicDetails(silent = true) },
                    onNavigateToTopic = onNavigateToTopic,
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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun TopicDetailsContent(
    details: RutrackerTopicDetails,
    viewModel: TopicViewModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
            ?: null
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = rawWindowSizeClass?.let { AdaptiveUtils.getEffectiveWindowSizeClass(it, context) } ?: rawWindowSizeClass
    val isCompact = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Compact
    val isMediumOrExpanded = windowSizeClass?.widthSizeClass != WindowWidthSizeClass.Compact

    val contentPadding =
        if (windowSizeClass != null) {
            AdaptiveUtils.getContentPadding(windowSizeClass)
        } else {
            16.dp
        }
    val itemSpacing =
        if (windowSizeClass != null) {
            AdaptiveUtils.getItemSpacing(windowSizeClass)
        } else {
            16.dp
        }

    var showDownloadMenu by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = contentPadding, vertical = itemSpacing),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
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

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Adaptive cover size: smaller on larger screens, larger on compact
                    val coverWidthFraction = if (isCompact) 0.6f else 0.5f
                    RemoteImage(
                        src = coverUrl,
                        contentDescription = details.title,
                        modifier =
                            Modifier
                                .fillMaxWidth(coverWidthFraction)
                                .aspectRatio(0.7f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        cornerRadius = 16f,
                    )
                }
            }
        }

        // Compact info row: Author, Performer, Duration, Size
        // Compact info row: Author, Performer, Duration, Size
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Metadata List (Label: Value)
                if (!details.author.isNullOrBlank()) {
                    MetadataRow(stringResource(R.string.authorLabel), details.author)
                }
                if (!details.performer.isNullOrBlank()) {
                    MetadataRow(stringResource(R.string.performerLabel), details.performer)
                }
                if (!details.series.isNullOrBlank()) {
                    MetadataRow(stringResource(R.string.seriesLabel), details.series)
                }
                // Size
                MetadataRow(stringResource(R.string.sizeLabel), details.size)

                // Render all other metadata fields
                details.allMetadata.forEach { (label, value) ->
                    // Skip fields already handled or standard ones if we want to customize them
                    val standardLabels =
                        setOf(
                            "Автор",
                            "Author",
                            "author",
                            "Исполнитель",
                            "Narrator",
                            "performer",
                            "Цикл",
                            "Серия",
                            "series",
                            "Время звучания",
                            "Duration",
                            "duration",
                            "bitrate",
                            "codec",
                            "genre",
                            "publisher",
                            "year",
                            "addedDate",
                        )
                    if (label !in standardLabels) {
                        MetadataRow(label, value)
                    }
                }

                // Registered / Downloaded
                if (!details.registeredDate.isNullOrBlank()) {
                    val downloadText =
                        if (!details.downloadsCount.isNullOrBlank()) {
                            " • " +
                                stringResource(R.string.downloadedLabel) +
                                stringResource(R.string.downloadedTimes, details.downloadsCount).trim()
                        } else {
                            ""
                        }
                    MetadataRow(stringResource(R.string.registeredLabel), details.registeredDate + downloadText)
                }

                // Seeders/Leechers (Split Left/Right)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${details.seeders}") },
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
                        label = { Text("${details.leechers}") },
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
            }
        }

        // Description and Comments section (side by side on larger screens)
        if (!details.description.isNullOrBlank() || details.comments.isNotEmpty()) {
            item {
                DescriptionAndCommentsSection(
                    description = details.description,
                    descriptionHtml = details.descriptionHtml,
                    comments = details.comments,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    currentPage = details.currentPage,
                    totalPages = details.totalPages,
                    isLoadingMore = viewModel.isLoadingMoreComments.collectAsStateWithLifecycle().value,
                    onLoadMore = { viewModel.loadMoreComments() },
                    onNavigateToTopic = onNavigateToTopic,
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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun DescriptionAndCommentsSection(
    description: String?,
    descriptionHtml: String? = null,
    comments: List<com.jabook.app.jabook.compose.domain.model.RutrackerComment>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    currentPage: Int = 1,
    totalPages: Int = 1,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onNavigateToTopic: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
            ?: null
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = rawWindowSizeClass?.let { AdaptiveUtils.getEffectiveWindowSizeClass(it, context) } ?: rawWindowSizeClass
    val isNarrow = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Compact

    val itemSpacing =
        if (windowSizeClass != null) {
            AdaptiveUtils.getItemSpacing(windowSizeClass)
        } else {
            16.dp
        }

    if (isNarrow) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            if (!description.isNullOrBlank()) {
                ExpandableDescription(
                    description = description,
                    descriptionHtml = descriptionHtml,
                    onNavigateToTopic = onNavigateToTopic,
                )
            }
            if (comments.isNotEmpty()) {
                ExpandableComments(
                    comments = comments,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onNavigateToTopic = onNavigateToTopic,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore,
                )
            }
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
            // Description column
            Column(
                modifier = Modifier.weight(1f),
            ) {
                if (!description.isNullOrBlank()) {
                    ExpandableDescription(
                        description = description,
                        descriptionHtml = descriptionHtml,
                        onNavigateToTopic = onNavigateToTopic,
                    )
                }
            }

            // Comments column
            Column(
                modifier = Modifier.weight(1f),
            ) {
                ExpandableComments(
                    comments = comments,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onNavigateToTopic = onNavigateToTopic,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

/**
 * Expandable description with collapse/expand functionality.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun ExpandableDescription(
    description: String,
    descriptionHtml: String? = null,
    onNavigateToTopic: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
            ?: null

    // Get window size class for adaptive sizing
    val rawWindowSizeClass =
        activity?.let {
            androidx.compose.material3.windowsizeclass
                .calculateWindowSizeClass(it)
        }
    val windowSizeClass = rawWindowSizeClass?.let { AdaptiveUtils.getEffectiveWindowSizeClass(it, context) } ?: rawWindowSizeClass
    val isCompact = windowSizeClass?.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact

    // Adaptive preview length based on screen size
    val maxPreviewLength = if (isCompact) 80 else 100
    val shouldShowExpand = description.length > maxPreviewLength

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

        if (descriptionHtml != null && expanded) {
            // Render full HTML description with spoilers
            val linkColor = MaterialTheme.colorScheme.primary
            val blocks =
                remember(descriptionHtml, linkColor) {
                    // Clean HTML description before parsing
                    val cleanedHtml =
                        descriptionHtml
                            .replace(Regex("<span[^>]*class=\"post-br\"[^>]*>.*?</span>", RegexOption.DOT_MATCHES_ALL), "<br>")
                            .replace(Regex("<br\\s*/?>\\s*<br\\s*/?>+"), "<br><br>") // Normalize multiple <br> tags
                            .trim()
                    com.jabook.app.jabook.compose.core.util.HtmlBlockParser
                        .parse(cleanedHtml, linkColor)
                }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                blocks.forEach { block ->
                    DescriptionBlockRenderer(block, onNavigateToTopic)
                }
            }
        } else {
            // Render preview or collapsed plain text
            val text =
                if (expanded) {
                    description
                } else {
                    if (shouldShowExpand) {
                        description.take(maxPreviewLength) + "..."
                    } else {
                        description
                    }
                }

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
    comments: List<com.jabook.app.jabook.compose.domain.model.RutrackerComment>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToTopic: (String) -> Unit,
    currentPage: Int = 1,
    totalPages: Int = 1,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Refresh when expanded
    LaunchedEffect(expanded) {
        if (expanded) {
            onRefresh()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.commentsLabel, comments.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (totalPages > 1 && expanded) {
                    Text(
                        text = stringResource(R.string.pageOfPages, currentPage, totalPages),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.height(16.dp).width(16.dp),
                    strokeWidth = 2.dp,
                )
            }

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
                // Show comments from newest to oldest (fresh comments first)
                // Sorting is now handled in ViewModel
                comments.forEach { comment ->
                    CommentItem(
                        comment = comment,
                        onNavigateToTopic = onNavigateToTopic,
                    )
                }

                // Load More button
                if (currentPage < totalPages && onLoadMore != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.loadMoreComments, totalPages - currentPage),
                        )
                    }
                }

                // Collapse button at the bottom for easy navigation
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledTonalButton(
                        onClick = { expanded = false },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp).width(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.collapse),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
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
    comment: com.jabook.app.jabook.compose.domain.model.RutrackerComment,
    onNavigateToTopic: (String) -> Unit,
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
                    RemoteImage(
                        src = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        cornerRadius = null, // Circle crop handled by contentScale
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

                val layoutResult = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Text(
                    text = annotatedText,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                        ),
                    onTextLayout = { layoutResult.value = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(annotatedText) {
                                detectTapGestures { offset ->
                                    layoutResult.value?.let { result ->
                                        val position = result.getOffsetForPosition(offset)
                                        annotatedText
                                            .getStringAnnotations(
                                                tag = "TOPIC_ID",
                                                start = position,
                                                end = position,
                                            ).firstOrNull()
                                            ?.let { annotation ->
                                                onNavigateToTopic(annotation.item)
                                            }
                                    }
                                }
                            },
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

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp), // Fixed width for labels
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
