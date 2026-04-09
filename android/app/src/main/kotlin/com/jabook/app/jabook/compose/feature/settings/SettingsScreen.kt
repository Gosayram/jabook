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

package com.jabook.app.jabook.compose.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.constants.PlaybackSpeedConstants
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.ScanProgress
import kotlinx.coroutines.launch

private object GitHubUrls {
    public const val REPOSITORY = "https://github.com/Gosayram/jabook"
    public const val LICENSE = "$REPOSITORY/blob/main/LICENSE"
    public const val CHANGELOG = "$REPOSITORY/blob/main/CHANGELOG.md"
    public const val APACHE_LICENSE = "https://www.apache.org/licenses/LICENSE-2.0"

    public fun releaseTag(version: String): String {
        val cleanVersion = version.replace("-dev", "").replace("-beta", "").replace("-prod", "")
        return "$REPOSITORY/releases/tag/$cleanVersion"
    }
}

/**
 * Settings screen for app configuration.
 *
 * Sections:
 * - Appearance (theme)
 * - Playback (auto-play, speed)
 * - About (version, license)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun SettingsScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    onNavigateToScanSettings: () -> Unit = {},
    onNavigateToAudioSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    indexingViewModel: com.jabook.app.jabook.compose.feature.indexing.IndexingViewModel = hiltViewModel(),
) {
    // Get window size class for adaptive sizing
    val context = LocalContext.current
    // Request notification permission for Android 13+
    val notificationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestPermission(),
        ) { isGranted ->
            if (!isGranted) {
                android.widget.Toast
                    .makeText(
                        context,
                        "Разрешение на уведомления отклонено. Вы не увидите прогресс индексации.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }
        }
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClassOrNull(rawWindowSizeClass, context)
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)
    val smallSpacing = AdaptiveUtils.getSmallSpacingOrDefault(windowSizeClass)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateToAuth = dropUnlessResumed { navigationClickGuard.run(onNavigateToAuth) }
    val safeNavigateToDebug = dropUnlessResumed { navigationClickGuard.run(onNavigateToDebug) }
    val safeNavigateToScanSettings = dropUnlessResumed { navigationClickGuard.run(onNavigateToScanSettings) }
    val safeNavigateToAudioSettings = dropUnlessResumed { navigationClickGuard.run(onNavigateToAudioSettings) }
    val safeNavigateToDownloads = dropUnlessResumed { navigationClickGuard.run(onNavigateToDownloads) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navSettingsText)) },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Authentication Section
            val authStatus by viewModel.authStatus.collectAsStateWithLifecycle()
            SettingsSection(
                title = stringResource(R.string.account),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            when (val status = authStatus) {
                is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated -> {
                    SettingsItem(
                        title = stringResource(R.string.loggedInAs, status.username),
                        subtitle = stringResource(R.string.tapToLogout),
                        onClick = { viewModel.logout() },
                    )
                }
                else -> {
                    SettingsItem(
                        title = stringResource(R.string.loginToRutracker),
                        subtitle = stringResource(R.string.requiredToDownloadTorrents),
                        onClick = { safeNavigateToAuth() },
                    )
                }
            }

            HorizontalDivider()

            // Network & Mirrors Section
            val currentMirror by viewModel.currentMirror.collectAsStateWithLifecycle()
            val availableMirrors by viewModel.availableMirrors.collectAsStateWithLifecycle()
            val protoSettings by viewModel.protoSettings.collectAsStateWithLifecycle()

            // State for health checks and dialog
            var showAddMirrorDialog by remember { mutableStateOf(false) }
            var customMirrorUrl by remember { mutableStateOf("") }
            var healthCheckInProgress by remember { mutableStateOf<String?>(null) }
            val healthStatus = remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }

            SettingsSection(
                title = stringResource(R.string.networkAndMirrors),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.currentMirror),
                subtitle = currentMirror,
            )

            // Mirror selection radio buttons
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = contentPadding),
            ) {
                availableMirrors.forEach { mirror ->
                    MirrorOption(
                        domain = mirror,
                        selected = mirror == currentMirror,
                        healthStatus = healthStatus.value[mirror],
                        isChecking = healthCheckInProgress == mirror,
                        onSelected = { viewModel.updateMirror(mirror) },
                        onCheckHealth = {
                            healthCheckInProgress = mirror
                            viewModel.checkMirrorHealth(mirror) { isHealthy ->
                                healthCheckInProgress = null
                                healthStatus.value = healthStatus.value + (mirror to isHealthy)
                            }
                        },
                        onRemove =
                            if (mirror !in com.jabook.app.jabook.compose.data.network.MirrorManager.DEFAULT_MIRRORS) {
                                { viewModel.removeCustomMirror(mirror) }
                            } else {
                                null
                            },
                    )
                }
            }

            Spacer(modifier = Modifier.height(itemSpacing))

            SettingsSwitchItem(
                title = stringResource(R.string.autoSwitching),
                subtitle = stringResource(R.string.autoSwitchToWorkingMirrorOnError),
                checked = protoSettings.autoSwitchMirror,
                onCheckedChange = viewModel::updateAutoSwitch,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.addCustomMirror),
                subtitle = stringResource(R.string.enterMirrorUrl),
                onClick = {
                    customMirrorUrl = ""
                    showAddMirrorDialog = true
                },
            )

            // Custom mirror dialog
            if (showAddMirrorDialog) {
                AddMirrorDialog(
                    currentValue = customMirrorUrl,
                    onValueChange = { customMirrorUrl = it },
                    onDismiss = { showAddMirrorDialog = false },
                    onConfirm = {
                        val domain = extractDomain(customMirrorUrl)
                        if (domain != null && domain !in availableMirrors) {
                            viewModel.addCustomMirror(domain)
                            showAddMirrorDialog = false
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.mirrorAddedFormat),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        } else if (domain in availableMirrors) {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.mirrorAlreadyAddedError),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.invalidUrlFormatError),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            HorizontalDivider()

            // Indexing Section
            val indexingProgress by indexingViewModel.indexingProgress.collectAsStateWithLifecycle()
            val isIndexing by indexingViewModel.isIndexing.collectAsStateWithLifecycle()
            val indexingStartTime by indexingViewModel.indexingStartTime.collectAsStateWithLifecycle()
            val clearingInProgress by indexingViewModel.clearingInProgress.collectAsStateWithLifecycle()

            var showIndexingDialog by remember { mutableStateOf(false) }
            var indexSize by remember { mutableStateOf(0) }
            var indexMetadata by remember {
                mutableStateOf<com.jabook.app.jabook.compose.data.local.dao.IndexMetadata?>(
                    null,
                )
            }
            var elapsedTimeStr by remember { mutableStateOf("") }

            // Timer for elapsed time
            LaunchedEffect(isIndexing, indexingStartTime) {
                if (isIndexing && indexingStartTime != null) {
                    while (true) {
                        val duration = System.currentTimeMillis() - indexingStartTime!!
                        val seconds = duration / 1000
                        elapsedTimeStr = DateUtils.formatElapsedTime(seconds)
                        kotlinx.coroutines.delay(1000L)
                    }
                } else {
                    elapsedTimeStr = ""
                }
            }

            LaunchedEffect(Unit) {
                indexSize = indexingViewModel.getIndexSize()
                indexMetadata = indexingViewModel.getIndexMetadata()
            }

            // Update index size when indexing completes - use database as single source of truth
            LaunchedEffect(indexingProgress) {
                if (indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed) {
                    // Immediately refresh index size from database after completion
                    indexSize = indexingViewModel.getIndexSize()
                    indexMetadata = indexingViewModel.getIndexMetadata()
                }
            }

            SettingsSection(title = "Индексация форумов", contentPadding = contentPadding, itemSpacing = itemSpacing)

            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsItem(
                title = if (indexSize == 0) "Индекс не создан" else "Индекс: $indexSize тем",
                subtitle = if (indexSize == 0) "Нажмите для создания индекса" else "Нажмите для обновления индекса",
                onClick = {
                    // Check and request notification permission on Android 13+
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    showIndexingDialog = true
                    indexingViewModel.startIndexing(context)
                },
            )

            if (isIndexing || indexSize > 0 || clearingInProgress) {
                SettingsItem(
                    title = if (clearingInProgress) "Очистка индекса..." else "Статус индексации",
                    subtitle =
                        when {
                            clearingInProgress -> "Пожалуйста, подождите..."
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress -> {
                                val progress = indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress
                                val timeText = if (elapsedTimeStr.isNotEmpty()) " • $elapsedTimeStr" else ""
                                "Индексируем: ${progress.currentForum} (${progress.currentForumIndex + 1}/${progress.totalForums})$timeText"
                            }
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed -> {
                                // Use indexSize from database as single source of truth
                                val completed =
                                    indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed
                                val displayCount =
                                    if (indexSize > 0) {
                                        indexSize
                                    } else {
                                        completed.totalTopics
                                    }
                                val durationMs =
                                    (indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed)
                                        .durationMs
                                val durationText = if (durationMs > 0) " за ${durationMs / 1000} сек" else ""
                                "Завершено: $displayCount тем$durationText"
                            }
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error ->
                                "Ошибка: ${(indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error).message}"
                            else -> "Готово к индексации"
                        },
                ) {
                    // Show progress bar during indexing or clearing
                    if (isIndexing &&
                        indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress
                    ) {
                        val progress = indexingProgress as com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress
                        val progressValue = progress.currentForumIndex.toFloat() / progress.totalForums.toFloat()

                        Column(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progressValue },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                            )
                            // Progress text
                            Text(
                                text = "Прогресс: ${(progressValue * 100).toInt()}% • Тем: ${progress.topicsIndexed}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else if (clearingInProgress) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )
                    }
                }

                // Add clear index button if index exists and not busy
                if (indexSize > 0 && !isIndexing && !clearingInProgress) {
                    var showClearConfirmDialog by remember { mutableStateOf(false) }

                    SettingsItem(
                        title = "Сбросить индекс",
                        subtitle = "Удалить все проиндексированные темы ($indexSize тем)",
                        onClick = {
                            showClearConfirmDialog = true
                        },
                    )

                    if (showClearConfirmDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearConfirmDialog = false },
                            title = { Text("Сбросить индекс?") },
                            text = {
                                Text(
                                    "Это действие удалит все $indexSize проиндексированных тем. " +
                                        "Для поиска потребуется создать новый индекс.",
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showClearConfirmDialog = false
                                        coroutineScope.launch {
                                            val success = indexingViewModel.clearIndex()
                                            if (success) {
                                                indexSize = indexingViewModel.getIndexSize()
                                                indexMetadata = indexingViewModel.getIndexMetadata()
                                            }
                                        }
                                    },
                                ) {
                                    Text("Сбросить")
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showClearConfirmDialog = false }) {
                                    Text("Отмена")
                                }
                            },
                        )
                    }
                }

                // Show index metadata if available
                indexMetadata?.let { metadata ->
                    if (indexSize > 0) {
                        val oldestDate =
                            metadata.oldest?.let { timestamp ->
                                java.text
                                    .SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(timestamp))
                            } ?: "неизвестно"
                        val newestDate =
                            metadata.newest?.let { timestamp ->
                                java.text
                                    .SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(timestamp))
                            } ?: "неизвестно"

                        SettingsItem(
                            title = "Проверка индекса",
                            subtitle = "Тем: $indexSize | Старые: $oldestDate | Новые: $newestDate",
                        )
                    }
                }
            }

            // Indexing progress dialog
            if (showIndexingDialog &&
                indexingProgress !is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Idle
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                com.jabook.app.jabook.compose.feature.indexing.IndexingProgressDialog(
                    progress = indexingProgress,
                    indexSize = indexSize, // Pass current index size from database as single source of truth
                    onDismiss = {
                        if (indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed ||
                            indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error
                        ) {
                            showIndexingDialog = false
                            // Refresh index size from database after indexing completes
                            coroutineScope.launch {
                                indexSize = indexingViewModel.getIndexSize()
                            }
                        }
                    },
                    onHide = {
                        // Hide dialog and start foreground service to continue indexing in background
                        showIndexingDialog = false
                        indexingViewModel.startIndexingInBackground(context)
                    },
                )
            }

            HorizontalDivider()

            // Library Section
            SettingsSection(
                title = stringResource(R.string.library),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            // Scan Progress
            val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
            SettingsItem(
                title = stringResource(R.string.scan_library),
                subtitle =
                    when (val p = scanProgress) {
                        is ScanProgress.Idle -> stringResource(R.string.tap_to_scan_now)
                        is ScanProgress.Discovery -> stringResource(R.string.scan_status_discovery, p.fileCount)
                        is ScanProgress.Parsing ->
                            stringResource(
                                R.string.scan_status_parsing,
                                p.currentBook,
                                p.progress,
                                p.total,
                            )
                        is ScanProgress.Saving -> stringResource(R.string.scan_status_saving)
                        is ScanProgress.Completed ->
                            pluralStringResource(
                                R.plurals.scan_status_complete_plural,
                                p.booksAdded,
                                p.booksAdded,
                            )
                        is ScanProgress.Error -> stringResource(R.string.scan_status_error, p.message)
                    },
                onClick =
                    if (scanProgress is ScanProgress.Idle ||
                        scanProgress is ScanProgress.Completed ||
                        scanProgress is ScanProgress.Error
                    ) {
                        { viewModel.scanLibrary() }
                    } else {
                        null
                    },
            ) {
                // Show progress bar for active states
                // Show progress bar for active states
                if (scanProgress is ScanProgress.Discovery ||
                    scanProgress is ScanProgress.Parsing ||
                    scanProgress is ScanProgress.Saving
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.cancelScan() },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }

            SettingsItem(
                title = stringResource(R.string.libraryFoldersTitle),
                subtitle = stringResource(R.string.manageFoldersToScanForAudiobooks),
                onClick = { safeNavigateToScanSettings() },
            )

            // Chapter Normalization Toggle
            val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
            SettingsSwitchItem(
                title = stringResource(R.string.normalizeChapterTitles),
                subtitle = stringResource(R.string.normalizeChapterTitlesDesc),
                checked = userPrefs?.normalizeChapterTitles ?: false,
                onCheckedChange = { viewModel.updateNormalizeChapterTitles(it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            HorizontalDivider()

            // Audio Section
            SettingsSection(
                title = stringResource(R.string.audioTitle),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.audioSettingsTitle),
                subtitle = stringResource(R.string.audioDescription),
                onClick = { safeNavigateToAudioSettings() },
            )

            HorizontalDivider()

            // Active Downloads Card
            val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()

            if (activeDownloads.isNotEmpty()) {
                val totalSpeed = activeDownloads.sumOf { it.downloadSpeed }
                val downloadCount =
                    activeDownloads.count {
                        it.state == com.jabook.app.jabook.compose.data.torrent.TorrentState.DOWNLOADING
                    }

                SettingsItem(
                    title = stringResource(R.string.active_downloads),
                    subtitle =
                        if (downloadCount > 0) {
                            stringResource(
                                R.string.downloading_count_speed,
                                downloadCount,
                                formatBytes(totalSpeed.toLong()) + "/s",
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.downloads_active_plural,
                                activeDownloads.size,
                                activeDownloads.size,
                            )
                        },
                    onClick = { safeNavigateToDownloads() },
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        // If we have active downloads, show indeterminate if speed > 0, else determinate
                        // Since we don't have total progress easily aggregated, keep it indeterminate for now
                        // or ideally calculate average progress
                    )
                }

                HorizontalDivider()
            }

            // Downloads Section
            SettingsSection(
                title = stringResource(R.string.downloads),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            val folderLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                        androidx.activity.result.contract.ActivityResultContracts
                            .OpenDocumentTree(),
                ) { uri ->
                    uri?.let {
                        val takeFlags =
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(it, takeFlags)
                        viewModel.updateDownloadPath(it.toString())
                    }
                }

            SettingsItem(
                title = stringResource(R.string.downloadLocationTitle),
                subtitle =
                    if (protoSettings.downloadPath.isNotEmpty()) {
                        android.net.Uri
                            .parse(protoSettings.downloadPath)
                            .path ?: protoSettings.downloadPath
                    } else {
                        stringResource(R.string.internalAppStorageDefault)
                    },
                onClick = { folderLauncher.launch(null) },
            )

            SettingsSwitchItem(
                title = stringResource(R.string.wifiOnly),
                subtitle = stringResource(R.string.downloadOnlyViaWifi),
                checked = protoSettings.wifiOnlyDownload,
                onCheckedChange = { enabled -> viewModel.updateWifiOnly(enabled) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            // Bandwidth Limiting
            SettingsSwitchItem(
                title = stringResource(R.string.limitDownloadSpeed),
                subtitle = stringResource(R.string.setMaximumDownloadSpeed),
                checked = protoSettings.limitDownloadSpeed,
                onCheckedChange = viewModel::updateLimitDownloadSpeed,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            if (protoSettings.limitDownloadSpeed) {
                SettingsSliderItem(
                    title = stringResource(R.string.maxSpeed),
                    sliderValue = protoSettings.maxDownloadSpeedKb.toFloat(),
                    onValueChange = { viewModel.updateMaxDownloadSpeed(it.toInt()) },
                    valueRange = 100f..10000f,
                    steps = 98,
                    valueFormatter = { "${it.toInt()} KB/s" },
                    contentPadding = contentPadding,
                    itemSpacing = itemSpacing,
                    smallSpacing = smallSpacing,
                )
            }

            // Concurrent Downloads
            SettingsSliderItem(
                title = stringResource(R.string.concurrentDownloads),
                sliderValue = protoSettings.maxConcurrentDownloads.toFloat(),
                onValueChange = { viewModel.updateMaxConcurrentDownloads(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                valueFormatter = { "${it.toInt()}" },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            Spacer(modifier = Modifier.height(itemSpacing))

            // Storage Usage
            val torrentStorageSize by viewModel.torrentStorageSize.collectAsStateWithLifecycle()
            LaunchedEffect(protoSettings.downloadPath) {
                viewModel.loadTorrentStorageSize()
            }

            SettingsItem(
                title = stringResource(R.string.downloadsStorage),
                subtitle = stringResource(R.string.storageUsedFormat, formatBytes(torrentStorageSize)),
            )

            var showDeleteAllDialog by remember { mutableStateOf(false) }

            SettingsItem(
                title = stringResource(R.string.deleteAllDownloads),
                subtitle = stringResource(R.string.deleteAllDownloadsDesc),
                onClick = { showDeleteAllDialog = true },
            )

            if (showDeleteAllDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllDialog = false },
                    title = { Text(stringResource(R.string.deleteAllDownloads)) },
                    text = { Text(stringResource(R.string.deleteAllConfirmation)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteAllTorrents(true)
                                showDeleteAllDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.deleteButton))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            HorizontalDivider()

            // Backup & Restore Section
            SettingsSection(
                title = stringResource(R.string.backupRestoreTitle),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            val backupState by viewModel.backupState.collectAsStateWithLifecycle()

            // State for import dialog
            var showImportConfirmation by remember { mutableStateOf(false) }
            var selectedBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }

            // File picker for import
            val importFilePicker =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                        androidx.activity.result.contract.ActivityResultContracts
                            .GetContent(),
                ) { uri: android.net.Uri? ->
                    uri?.let {
                        // Show confirmation dialog
                        showImportConfirmation = true
                        selectedBackupUri = it
                    }
                }

            SettingsItem(
                title = stringResource(R.string.exportDataButton),
                subtitle = stringResource(R.string.saveSettingsAndLibraryToBackupFile),
                onClick = { viewModel.exportData() },
            )

            SettingsItem(
                title = stringResource(R.string.importDataButton),
                subtitle = stringResource(R.string.restoreSettingsAndLibraryFromBackup),
                onClick = {
                    // Launch file picker for JSON files
                    importFilePicker.launch("application/json")
                },
            )

            // Import Confirmation Dialog
            if (showImportConfirmation) {
                AlertDialog(
                    onDismissRequest = { showImportConfirmation = false },
                    title = { Text(stringResource(R.string.importBackup)) },
                    text = {
                        Text(
                            stringResource(R.string.thisWillReplaceYourCurrentSettingsAreYouSureYouWan),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedBackupUri?.let { viewModel.importData(it) }
                                showImportConfirmation = false
                            },
                        ) {
                            Text(stringResource(R.string.importButton))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportConfirmation = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            // Handle Export Success - Share file
            LaunchedEffect(backupState) {
                if (backupState is BackupUiState.ExportReady) {
                    val uri = (backupState as BackupUiState.ExportReady).uri
                    val intent =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.jabookBackup))
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                context.getString(R.string.backupOfJabookSettingsAndData),
                            )
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(
                        android.content.Intent.createChooser(intent, context.getString(R.string.exportBackup)),
                    )
                    viewModel.resetBackupState()
                }
            }

            // Handle Import Success - Show statistics
            LaunchedEffect(backupState) {
                if (backupState is BackupUiState.ImportComplete) {
                    val stats = (backupState as BackupUiState.ImportComplete).stats
                    android.widget.Toast
                        .makeText(
                            context,
                            context.getString(R.string.importSuccessfulStats),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    viewModel.resetBackupState()
                }
            }

            // Handle Errors
            LaunchedEffect(backupState) {
                if (backupState is BackupUiState.Error) {
                    val error = (backupState as BackupUiState.Error).message
                    android.widget.Toast
                        .makeText(context, error, android.widget.Toast.LENGTH_LONG)
                        .show()
                    viewModel.resetBackupState()
                }
            }

            HorizontalDivider()

            // Cache Management Section
            SettingsSection(
                title = stringResource(R.string.cacheManagement),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
            val cacheOperation by viewModel.cacheOperation.collectAsStateWithLifecycle()

            // State for clear cache dialog
            var showClearCacheDialog by remember { mutableStateOf(false) }

            // Load cache statistics on first composition
            LaunchedEffect(Unit) {
                viewModel.loadCacheStatistics()
            }

            // Total cache size
            SettingsItem(
                title = stringResource(R.string.totalCacheSize),
                subtitle =
                    cacheStats?.let { formatBytes(it.totalSize) }
                        ?: if (cacheOperation is CacheOperationState.Loading) {
                            stringResource(
                                R.string.calculating,
                            )
                        } else {
                            stringResource(R.string.unknown)
                        },
            )

            // Last cleanup timestamp
            SettingsItem(
                title = stringResource(R.string.lastCleanup),
                subtitle =
                    cacheStats?.let {
                        if (it.lastCleanup > 0) {
                            formatTimestamp(it.lastCleanup)
                        } else {
                            stringResource(R.string.neverDate)
                        }
                    } ?: "-",
            )

            // Clear all cache button
            SettingsItem(
                title = stringResource(R.string.clearAllCacheButton),
                subtitle =
                    cacheStats?.let {
                        stringResource(
                            R.string.freeUpCacheSize,
                            formatBytes(it.totalSize),
                        )
                    } ?: "",
                onClick = {
                    if (cacheOperation != CacheOperationState.Clearing) {
                        showClearCacheDialog = true
                    }
                },
            )

            // Clear cache confirmation dialog
            if (showClearCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearCacheDialog = false },
                    title = { Text(stringResource(R.string.clearCache)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.clearCacheConfirmation,
                                cacheStats?.let { formatBytes(it.totalSize) } ?: stringResource(R.string.unknown),
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearCache()
                                showClearCacheDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.clearButton))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearCacheDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            // Show toast on success/error
            LaunchedEffect(cacheOperation) {
                when (cacheOperation) {
                    is CacheOperationState.Success -> {
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.cacheClearedSuccessMessage),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        viewModel.resetCacheOperation()
                    }
                    is CacheOperationState.Error -> {
                        val error = (cacheOperation as CacheOperationState.Error).message
                        android.widget.Toast
                            .makeText(context, error, android.widget.Toast.LENGTH_LONG)
                            .show()
                        viewModel.resetCacheOperation()
                    }
                    else -> {}
                }
            }

            HorizontalDivider()

            // Appearance Section
            SettingsSection(
                title = stringResource(R.string.appearance),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.themeTitle),
                subtitle = stringResource(R.string.chooseAppTheme),
            ) {
                ThemeSelector(
                    selectedTheme = userPreferences?.theme ?: AppTheme.SYSTEM,
                    onThemeSelected = { theme -> viewModel.updateTheme(theme) },
                )
            }

            SettingsItem(
                title = stringResource(R.string.languageSettingsLabel),
                subtitle = stringResource(R.string.languageDescription),
                onClick = { openSystemLanguageSettings(context) },
            )

            SettingsItem(
                title = stringResource(R.string.fontTitle),
                subtitle = stringResource(R.string.chooseFontFamily),
            ) {
                FontSelector(
                    selectedFont = userPreferences?.font ?: com.jabook.app.jabook.compose.data.model.AppFont.DEFAULT,
                    onFontSelected = { font -> viewModel.updateFont(font) },
                )
            }

            HorizontalDivider()

            // Playback Section
            SettingsSection(
                title = stringResource(R.string.playback),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsSwitchItem(
                title = stringResource(R.string.autoplayNextChapter),
                subtitle = stringResource(R.string.automaticallyPlayNextChapterWhenCurrentEnds),
                checked = userPreferences?.autoPlayNext ?: true,
                onCheckedChange = viewModel::updateAutoPlayNext,
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            // Playback Speed
            SettingsSliderItem(
                title = stringResource(R.string.playbackSpeed),
                sliderValue = userPreferences?.playbackSpeed ?: 1.0f,
                onValueChange = { viewModel.updatePlaybackSpeed(it) },
                valueRange = PlaybackSpeedConstants.MIN_SPEED..PlaybackSpeedConstants.MAX_SPEED,
                steps = PlaybackSpeedConstants.SLIDER_STEPS,
                valueFormatter = { PlaybackSpeedConstants.formatSpeed(it) },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            // Seek Intervals
            SettingsSliderItem(
                title = stringResource(R.string.rewindDurationTitle),
                sliderValue = protoSettings.rewindDurationSeconds.toFloat(),
                onValueChange = { viewModel.updateAudioSettings(rewindSeconds = it.toInt()) },
                valueRange = 5f..60f,
                steps = 10,
                valueFormatter = { "${it.toInt()}s" },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            SettingsSliderItem(
                title = stringResource(R.string.forwardDurationTitle),
                sliderValue = protoSettings.forwardDurationSeconds.toFloat(),
                onValueChange = { viewModel.updateAudioSettings(forwardSeconds = it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
                valueFormatter = { "${it.toInt()}s" },
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
                smallSpacing = smallSpacing,
            )

            // Reset Global Book Settings
            var showResetBookSettingsDialog by remember { mutableStateOf(false) }

            SettingsItem(
                title = stringResource(R.string.resetAllBookSettings),
                subtitle =
                    stringResource(R.string.resetAllBookSettingsConfirmation)
                        .substringBefore(stringResource(R.string.n)), // Use first line as subtitle or full desc
                onClick = { showResetBookSettingsDialog = true },
            )

            if (showResetBookSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showResetBookSettingsDialog = false },
                    title = { Text(stringResource(R.string.resetAllBookSettings)) },
                    text = { Text(stringResource(R.string.resetAllBookSettingsConfirmation)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resetAllBookSettings()
                                showResetBookSettingsDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.resetButton))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetBookSettingsDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            HorizontalDivider()

            // About Section
            SettingsSection(
                title = stringResource(R.string.aboutTitle),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.version),
                subtitle = getVersionName(context),
                onClick = {
                    val url = GitHubUrls.releaseTag(getVersionName(context))
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                },
            )

            HorizontalDivider()

            SettingsItem(
                title = stringResource(R.string.openSourceLicenses),
                subtitle = stringResource(R.string.viewLicenses),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.LICENSE),
                        )
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.changelog),
                subtitle = stringResource(R.string.changelogDescription),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.CHANGELOG),
                        )
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.github),
                subtitle = stringResource(R.string.githubRepositoryDescription),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.REPOSITORY),
                        )
                    context.startActivity(intent)
                },
            )

            SettingsItem(
                title = stringResource(R.string.privacyPolicy),
                subtitle = stringResource(R.string.apache20OpenSourceLicense),
                onClick = {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubUrls.APACHE_LICENSE),
                        )
                    context.startActivity(intent)
                },
            )

            // Developer Tools Section
            SettingsSection(
                title = stringResource(R.string.developer),
                contentPadding = contentPadding,
                itemSpacing = itemSpacing,
            )

            SettingsItem(
                title = stringResource(R.string.debugToolsTitle),
                subtitle = stringResource(R.string.viewLogsTestMirrorsCheckCache),
                onClick = { safeNavigateToDebug() },
            )

            Spacer(modifier = Modifier.height(itemSpacing))
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = itemSpacing),
    )
}

@Composable
internal fun SettingsItem(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        content?.invoke()
    }
}

@Composable
internal fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    smallSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch,
                ).padding(horizontal = contentPadding, vertical = itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(smallSpacing))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = null, // Handled by parent Row
        )
    }
}

@Composable
internal fun SettingsSliderItem(
    title: String,
    subtitle: String? = null,
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueFormatter: (Float) -> String = { it.toString() },
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    smallSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    var currentValue by remember(sliderValue) { mutableStateOf(sliderValue) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = itemSpacing),
    ) {
        // Title and subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(smallSpacing))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Current value display
            Text(
                text = valueFormatter(currentValue),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider with min/max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Min label
            Text(
                text = valueFormatter(valueRange.start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Slider
            Slider(
                value = currentValue,
                onValueChange = { currentValue = it },
                onValueChangeFinished = { onValueChange(currentValue) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )

            // Max label
            Text(
                text = valueFormatter(valueRange.endInclusive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeSelector(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))

        ThemeOption(
            theme = AppTheme.LIGHT,
            label = stringResource(R.string.light),
            selected = selectedTheme == AppTheme.LIGHT,
            onSelected = { onThemeSelected(AppTheme.LIGHT) },
        )

        ThemeOption(
            theme = AppTheme.DARK,
            label = stringResource(R.string.dark),
            selected = selectedTheme == AppTheme.DARK,
            onSelected = { onThemeSelected(AppTheme.DARK) },
        )

        ThemeOption(
            theme = AppTheme.AMOLED,
            label = stringResource(R.string.themeAmoled),
            selected = selectedTheme == AppTheme.AMOLED,
            onSelected = { onThemeSelected(AppTheme.AMOLED) },
        )

        ThemeOption(
            theme = AppTheme.SYSTEM,
            label = stringResource(R.string.systemDefault),
            selected = selectedTheme == AppTheme.SYSTEM,
            onSelected = { onThemeSelected(AppTheme.SYSTEM) },
        )
    }
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onSelected,
                    role = Role.RadioButton,
                ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Handled by parent Row
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSelector(
    selectedFont: com.jabook.app.jabook.compose.data.model.AppFont,
    onFontSelected: (com.jabook.app.jabook.compose.data.model.AppFont) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fonts = com.jabook.app.jabook.compose.data.model.AppFont.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedFont.displayName,
            onValueChange = {}, // Read-only
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    ),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            fonts.forEach { font ->
                DropdownMenuItem(
                    text = { Text(font.displayName) },
                    onClick = {
                        onFontSelected(font)
                        expanded = false
                    },
                    leadingIcon =
                        if (selectedFont == font) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/**
 * Get app version name from PackageManager.
 */
private fun getVersionName(context: Context): String =
    try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: context.getString(R.string.unknown)
    } catch (e: PackageManager.NameNotFoundException) {
        context.getString(R.string.unknown)
    }

/**
 * Mirror selection option with health check button.
 */
@Composable
private fun MirrorOption(
    domain: String,
    selected: Boolean,
    healthStatus: Boolean?,
    isChecking: Boolean,
    onSelected: () -> Unit,
    onCheckHealth: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onSelected,
                    role = Role.RadioButton,
                ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Handled by parent Row
        )

        // Health status icon
        when {
            isChecking -> {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            healthStatus == true -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.available),
                    tint =
                        androidx.compose.ui.graphics
                            .Color(0xFF4CAF50),
                    // Green
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                )
            }
            healthStatus == false -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.unavailable),
                    tint =
                        androidx.compose.ui.graphics
                            .Color(0xFFF44336),
                    // Red
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                )
            }
            else -> {
                // Unknown status - show nothing or a subtle indicator
                Spacer(modifier = Modifier.width(24.dp))
            }
        }

        Text(
            text = domain,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
        )

        // Health check button
        TextButton(
            onClick = onCheckHealth,
            enabled = !isChecking,
        ) {
            Text(stringResource(R.string.check), style = MaterialTheme.typography.bodySmall)
        }

        // Remove button for custom mirrors
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.deleteAction), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Dialog for adding a custom mirror.
 */
@Composable
private fun AddMirrorDialog(
    currentValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    itemSpacing: androidx.compose.ui.unit.Dp,
    smallSpacing: androidx.compose.ui.unit.Dp,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addMirror)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.enterRutrackerMirrorDomain),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(itemSpacing))
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = onValueChange,
                    label = { Text(stringResource(R.string.domain)) },
                    placeholder = { Text(stringResource(R.string.rutrackernl)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(smallSpacing))
                Text(
                    "Примеры: rutracker.nl, rutracker.ru, rutracker.net.ru",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancelAction))
            }
        },
    )
}

/**
 * Extract domain from URL or domain string.
 *
 * Accepts: "rutracker.nl", "https://rutracker.nl", "rutracker.nl/forum"
 * Returns: "rutracker.nl" or null if invalid
 */
private fun extractDomain(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    // Remove protocol
    val withoutProtocol = trimmed.removePrefix("https://").removePrefix("http://")

    // Remove path
    val domain = withoutProtocol.substringBefore("/")

    // Basic validation: must contain at least one dot and no spaces
    return if (domain.contains(".") && !domain.contains(" ")) {
        domain
    } else {
        null
    }
}

/**
 * Format bytes to human-readable string (B, KB, MB, GB).
 */
private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

/**
 * Format timestamp to readable date string (GOST 7.64-90 format).
 */
private fun formatTimestamp(millis: Long): String =
    com.jabook.app.jabook.compose.util.DateTimeFormatter
        .formatGOST(millis)

private fun openSystemLanguageSettings(context: Context) {
    val appLanguageIntent =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    val fallbackIntent =
        Intent(Settings.ACTION_LOCALE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.startActivity(appLanguageIntent)
        } else {
            context.startActivity(fallbackIntent)
        }
    } catch (_: ActivityNotFoundException) {
        context.startActivity(fallbackIntent)
    }
}
