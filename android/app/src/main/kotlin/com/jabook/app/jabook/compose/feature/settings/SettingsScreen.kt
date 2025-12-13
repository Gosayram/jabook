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

package com.jabook.app.jabook.compose.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.lyricist.LocalStrings
import com.jabook.app.jabook.compose.data.model.AppTheme

/**
 * Settings screen for app configuration.
 *
 * Sections:
 * - Appearance (theme)
 * - Playback (auto-play, speed)
 * - About (version, license)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenSettings) },
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
            SettingsSection(title = "Account")

            when (val status = authStatus) {
                is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated -> {
                    SettingsItem(
                        title = "Logged in as ${status.username}",
                        subtitle = "Tap to logout",
                        onClick = { viewModel.logout() },
                    )
                }
                else -> {
                    SettingsItem(
                        title = "Login to Rutracker",
                        subtitle = "Required to download torrents",
                        onClick = { onNavigateToAuth() },
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

            SettingsSection(title = "Сеть и Зеркала")

            SettingsItem(
                title = "Текущее зеркало",
                subtitle = currentMirror,
            )

            // Mirror selection radio buttons
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
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

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitchItem(
                title = "Автопереключение",
                subtitle = "Автоматически переключаться на рабочее зеркало при ошибках",
                checked = protoSettings.autoSwitchMirror,
                onCheckedChange = viewModel::updateAutoSwitch,
            )

            SettingsItem(
                title = "Добавить свое зеркало",
                subtitle = "Введите URL зеркала",
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
                                .makeText(context, "Зеркало $domain добавлено", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        } else if (domain in availableMirrors) {
                            android.widget.Toast
                                .makeText(context, "Это зеркало уже добавлено", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            android.widget.Toast
                                .makeText(context, "Неверный URL. Используйте формат: rutracker.example", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                )
            }

            HorizontalDivider()
            
            // Downloads Section
            SettingsSection(title = "Downloads")
            
            val folderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    viewModel.updateDownloadPath(it.toString())
                }
            }

            SettingsItem(
                title = "Download Location",
                subtitle = if (protoSettings.downloadPath.isNotEmpty()) {
                    android.net.Uri.parse(protoSettings.downloadPath).path ?: protoSettings.downloadPath
                } else {
                    "Internal App Storage (Default)"
                },
                onClick = { folderLauncher.launch(null) }
            )
            
            SettingsSwitchItem(
                title = "Wi-Fi Only",
                subtitle = "Download only via Wi-Fi",
                checked = protoSettings.wifiOnlyDownload,
                onCheckedChange = viewModel::updateWifiOnly
            )

            HorizontalDivider()

            // Appearance Section
            SettingsSection(title = strings.settingsSectionAppearance)

            SettingsItem(
                title = strings.settingsTheme,
                subtitle = strings.settingsThemeDescription,
            ) {
                ThemeSelector(
                    selectedTheme = userPreferences?.theme ?: AppTheme.SYSTEM,
                    onThemeSelected = viewModel::updateTheme,
                )
            }

            HorizontalDivider()

            // Playback Section
            SettingsSection(title = strings.settingsSectionPlayback)

            SettingsSwitchItem(
                title = strings.settingsAutoPlayNext,
                subtitle = strings.settingsAutoPlayNextDescription,
                checked = userPreferences?.autoPlayNext ?: true,
                onCheckedChange = viewModel::updateAutoPlayNext,
            )

            SettingsSliderItem(
                title = strings.settingsPlaybackSpeed,
                subtitle = strings.settingsPlaybackSpeedValue(userPreferences?.playbackSpeed ?: 1.0f),
                value = userPreferences?.playbackSpeed ?: 1.0f,
                valueRange = 0.5f..2.0f,
                steps = 14, // 0.5, 0.6, ..., 2.0
                onValueChange = viewModel::updatePlaybackSpeed,
            )

            HorizontalDivider()

            // About Section
            SettingsSection(title = strings.settingsSectionAbout)

            SettingsItem(
                title = strings.settingsVersion,
                subtitle = getVersionName(context),
            )

            HorizontalDivider()

            SettingsItem(
                title = "Open Source Licenses",
                onClick = {
                    android.widget.Toast
                        .makeText(context, "License screen placeholder", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
            )

            SettingsItem(
                title = "Privacy Policy",
                onClick = {
                    android.widget.Toast
                        .makeText(context, "Privacy Policy placeholder", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsItem(
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
            )
        }

        content?.invoke()
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
private fun SettingsSliderItem(
    title: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(value) { mutableStateOf(value) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThemeSelector(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))

        ThemeOption(
            theme = AppTheme.LIGHT,
            label = strings.settingsThemeLight,
            selected = selectedTheme == AppTheme.LIGHT,
            onSelected = { onThemeSelected(AppTheme.LIGHT) },
        )

        ThemeOption(
            theme = AppTheme.DARK,
            label = strings.settingsThemeDark,
            selected = selectedTheme == AppTheme.DARK,
            onSelected = { onThemeSelected(AppTheme.DARK) },
        )

        ThemeOption(
            theme = AppTheme.SYSTEM,
            label = strings.settingsThemeSystem,
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

/**
 * Get app version name from PackageManager.
 */
private fun getVersionName(context: Context): String =
    try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "Unknown"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
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
                    contentDescription = "Available",
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
                    contentDescription = "Unavailable",
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
            Text("Проверить", style = MaterialTheme.typography.bodySmall)
        }

        // Remove button for custom mirrors
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text("Удалить", style = MaterialTheme.typography.bodySmall)
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить зеркало") },
        text = {
            Column {
                Text(
                    "Введите домен зеркала RuTracker",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = onValueChange,
                    label = { Text("Домен") },
                    placeholder = { Text("rutracker.nl") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Примеры: rutracker.nl, rutracker.ru, rutracker.net.ru",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
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
