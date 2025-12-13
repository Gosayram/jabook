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

package com.jabook.app.jabook.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.lyricist.LocalStrings
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import com.jabook.app.jabook.compose.navigation.JabookAppState
import com.jabook.app.jabook.compose.navigation.JabookNavHost
import com.jabook.app.jabook.compose.navigation.TopLevelDestination
import com.jabook.app.jabook.compose.navigation.rememberJabookAppState
import com.jabook.app.jabook.ui.theme.JabookTheme

/**
 * Root composable for the Jabook app.
 *
 * This is the main entry point for the Compose UI, containing:
 * - Theme wrapper
 * - Bottom navigation bar
 * - Navigation graph
 *
 * Based on Now in Android's app structure.
 *
 * @param appState App state holder, defaults to remembered state
 */
@Composable
fun JabookApp(
    intent: android.content.Intent? = null,
    appState: JabookAppState = rememberJabookAppState(),
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Detect if this is a beta/dev/stage flavor by checking package name
    // Beta: com.jabook.app.jabook.beta, Dev: .dev, Stage: .stage, Prod: com.jabook.app.jabook
    val context = LocalContext.current
    val packageName = context.packageName
    val isBetaFlavor = packageName.endsWith(".beta") || packageName.endsWith(".dev") || packageName.endsWith(".stage")

    // State for permission dialogs
    var showStoragePermissionDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Launcher for "Manage All Files" settings intent (Android 11+)
    val manageExternalStorageLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(),
        ) {
            // Re-check permission on return (not guaranteed to be granted)
            // Ideally trigger a re-composition or check via ViewModel
        }

    // Launcher for standard runtime permissions
    val permissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestMultiplePermissions(),
            onResult = { /* Permissions handled by system, app will adapt */ },
        )

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Check MANAGE_EXTERNAL_STORAGE
            if (!android.os.Environment.isExternalStorageManager()) {
                showStoragePermissionDialog = true
            } else {
                // Already have storage, check Notifications (Android 13+)
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
            }
        } else {
            // Android < 11: Request legacy storage permissions
            val permissions = mutableListOf<String>()
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

            val needed =
                permissions.filter {
                    androidx.core.content.ContextCompat
                        .checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }

            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            }
        }
    }

    if (showStoragePermissionDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                // Don't allow dismissing without decision for now, or assume denied
                showStoragePermissionDialog = false
            },
            title = { Text("Permission Required") },
            text = {
                Text(
                    "This app needs full file access to download and manage audiobooks in your folders. Please grant 'All files access' in the next screen.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showStoragePermissionDialog = false
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = android.net.Uri.parse("package:$packageName")
                            manageExternalStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            // Fallback for some devices
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageExternalStorageLauncher.launch(intent)
                        }
                    },
                ) {
                    Text("Grant Access")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showStoragePermissionDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Handle deep links when intent changes
    androidx.compose.runtime.LaunchedEffect(intent) {
        if (intent != null) {
            appState.navController.handleDeepLink(intent)
        }
    }

    val darkTheme =
        when (uiState) {
            is MainActivityUiState.Loading -> androidx.compose.foundation.isSystemInDarkTheme()
            is MainActivityUiState.Success -> {
                val theme = (uiState as MainActivityUiState.Success).userData.theme
                when (theme) {
                    com.jabook.app.jabook.compose.data.model.AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    com.jabook.app.jabook.compose.data.model.AppTheme.LIGHT -> false
                    com.jabook.app.jabook.compose.data.model.AppTheme.DARK -> true
                }
            }
        }

    // Setup Lyricist for type-safe localization
    // This must wrap the theme/content so LocalStrings is available in all subcompositions
    ProvideStrings(rememberStrings()) {
        JabookTheme(
            darkTheme = darkTheme,
            isBetaFlavor = isBetaFlavor,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { androidx.compose.material3.SnackbarHost(appState.snackbarHostState) },
                bottomBar = {
                    JabookBottomBar(
                        destinations = appState.topLevelDestinations,
                        currentDestination = appState.currentDestination,
                        onNavigateToDestination = { destination ->
                            appState.navigateToTopLevelDestination(destination)
                        },
                    )
                },
            ) { padding ->
                JabookNavHost(
                    appState = appState,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

/**
 * Bottom navigation bar for top-level destinations.
 *
 * @param destinations List of top-level destinations to show
 * @param currentDestination Current navigation destination
 * @param onNavigateToDestination Callback when a destination is selected
 */
@Composable
private fun JabookBottomBar(
    destinations: List<TopLevelDestination>,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    NavigationBar(
        modifier = modifier,
    ) {
        destinations.forEach { destination ->
            val isSelected = currentDestination.isTopLevelDestinationInHierarchy(destination)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector =
                            if (isSelected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                        contentDescription = destination.iconText(strings),
                    )
                },
                label = { Text(destination.titleText(strings)) },
            )
        }
    }
}

/**
 * Checks if the current destination is in the hierarchy of a top-level destination.
 *
 * This matches based on route name, checking if the destination route contains
 * the top-level destination name (case-insensitive).
 *
 * @param destination The top-level destination to check against
 * @return true if the current destination is part of this top-level destination's hierarchy
 */
private fun androidx.navigation.NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination): Boolean =
    this?.route?.contains(destination.name, ignoreCase = true) == true
