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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.domain.model.AuthStatus

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
    val downloadMessage by viewModel.downloadMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show download message in Snackbar
    LaunchedEffect(downloadMessage) {
        downloadMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearDownloadMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Детали книги") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState is TopicUiState.Success && authStatus is AuthStatus.Authenticated) {
                FloatingActionButton(onClick = viewModel::downloadTorrent) {
                    Icon(Icons.Filled.Download, contentDescription = "Download")
                }
            }
        },
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title
        item {
            Text(
                text = details.title,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        // Author/Performer
        if (!details.author.isNullOrBlank()) {
            item {
                Text(
                    text = "Автор: ${details.author}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (!details.performer.isNullOrBlank()) {
            item {
                Text(
                    text = "Читает: ${details.performer}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // Seeders/Leechers + Size
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            }
        }

        // Duration, Bitrate, Codec
        if (!details.duration.isNullOrBlank() ||
            !details.bitrate.isNullOrBlank() ||
            !details.audioCodec.isNullOrBlank()
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    details.duration?.let {
                        Text(
                            text = "Длительность: $it",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    details.bitrate?.let {
                        Text(
                            text = "Битрейт: $it",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    details.audioCodec?.let {
                        Text(
                            text = "Формат: $it",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Description (collapsible)
        if (!details.description.isNullOrBlank()) {
            item {
                ExpandableDescription(description = details.description)
            }
        }

        // File List Header
        item {
            Text(
                text = "Файлы",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // File List
        items(details.genres) { file ->
            FileListItem(file = file)
        }
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
                    contentDescription = "Seeders",
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
                    contentDescription = "Leechers",
                    tint = Color(0xFFFF9800), // Orange
                )
            },
        )
    }
}

/**
 * Expandable description with collapse/expand functionality.
 */
@Composable
private fun ExpandableDescription(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val maxPreviewLength = 200

    Column(modifier = modifier) {
        Text(
            text = "Описание",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text =
                if (expanded) {
                    description
                } else {
                    description.take(maxPreviewLength)
                },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (description.length > maxPreviewLength) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Свернуть" else "Развернуть")
            }
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
            text = "Ошибка: $message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}
