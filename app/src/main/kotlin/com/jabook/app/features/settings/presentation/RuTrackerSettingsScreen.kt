package com.jabook.app.features.settings.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.jabook.app.R

data class LoginCardState(
    val username: String,
    val password: String,
    val isAuthorized: Boolean,
    val isLoading: Boolean,
)

@Composable
fun ModeToggleCard(
    isGuestMode: Boolean,
    onModeChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.rutracker_mode_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isGuestMode) stringResource(R.string.rutracker_guest_mode) else stringResource(R.string.rutracker_authenticated_mode),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Switch(
                    checked = !isGuestMode,
                    onCheckedChange = { isAuthenticated ->
                        onModeChange(!isAuthenticated)
                    },
                )
            }

            Text(
                text = if (isGuestMode) {
                    stringResource(R.string.rutracker_guest_mode_description)
                } else {
                    stringResource(R.string.rutracker_authenticated_mode_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LoginCard(
    state: LoginCardState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.rutracker_credentials_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.rutracker_username_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.rutracker_password_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.username.isNotBlank() && state.password.isNotBlank() && !state.isLoading,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.rutracker_login_button))
                    }
                }

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isAuthorized && !state.isLoading,
                ) {
                    Text(stringResource(R.string.rutracker_logout_button))
                }
            }

            if (state.isAuthorized) {
                Text(
                    text = stringResource(R.string.rutracker_authorized_as, state.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun StatusMessageCard(
    errorMessage: String?,
    successMessage: String?,
) {
    if (errorMessage != null || successMessage != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (errorMessage != null) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (errorMessage != null) "Ошибка" else "Успех",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
                Text(
                    text = errorMessage ?: successMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
    }
}

@Composable
fun RuTrackerSettingsScreen(
    viewModel: RuTrackerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // State to force recomposition when SAF Uri changes
    var safUriState by remember { mutableStateOf(viewModel.getLogFolderUri()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModeToggleCard(
            isGuestMode = state.isGuestMode,
            onModeChange = viewModel::setGuestMode,
        )

        if (!state.isGuestMode) {
            LoginCard(
                state = LoginCardState(
                    username = state.username,
                    password = state.password,
                    isAuthorized = state.isAuthorized,
                    isLoading = state.isLoading,
                ),
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
                onLogin = viewModel::login,
                onLogout = viewModel::logout,
            )
        }

        StatusMessageCard(
            errorMessage = state.error,
            successMessage = state.successMessage,
        )

        // Button to export debug logs
        Button(
            onClick = {
                // Export logs and share via system intent
                val logFile: File? = viewModel.exportLogs()
                if (logFile != null && logFile.exists()) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            logFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share debug log file"))
                        Toast.makeText(context, context.getString(R.string.export_logs_success), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.export_logs_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.log_file_not_found), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.export_debug_logs))
        }

        // Get SAF log folder Uri from ViewModel/SharedPreferences
        val logFolderUriString = safUriState
        val logFileName = "debug_log.txt"
        // Show log file path or SAF Uri
        if (logFolderUriString != null) {
            Text(
                text = stringResource(R.string.log_folder_saf, logFolderUriString, logFileName),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            // Show a hint if log file is not found
            if (viewModel.getLogFileUriFromSaf(logFileName) == null) {
                Text(
                    text = stringResource(R.string.log_file_will_appear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        } else {
            val logFile = viewModel.exportLogs()
            val logFilePath = logFile?.absolutePath ?: stringResource(R.string.log_file_not_found)
            Text(
                text = stringResource(R.string.log_file_path, logFilePath),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // Button to export log from SAF folder if SAF is used
        if (logFolderUriString != null) {
            Button(
                onClick = {
                    val uri = viewModel.getLogFileUriFromSaf(logFileName)
                    if (uri != null) {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share debug log file (SAF)"))
                            Toast.makeText(context, context.getString(R.string.export_logs_from_saf_success), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.export_logs_from_saf_failed, e.message), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.log_file_not_found_saf), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    ) {
            Text(stringResource(R.string.export_logs_from_saf))
        }
        }

        // SAF launcher for selecting log folder
        val logFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Pass the selected folder Uri to the ViewModel or save in preferences
                viewModel.setLogFolderUri(uri.toString())
                safUriState = uri.toString() // Force recomposition
                Toast.makeText(context, context.getString(R.string.log_folder_selected, uri), Toast.LENGTH_SHORT).show()
                android.util.Log.d("RuTrackerSettingsScreen", "SAF Uri saved: $uri")
                // Write a test log entry to create the file immediately
                viewModel.writeTestLogEntry()
            }
        }

        // Button to select log folder via SAF
        Button(
            onClick = {
                logFolderLauncher.launch(null)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.select_log_folder))
        }

        // Button to manually write a test log entry for SAF diagnostics
        Button(
            onClick = {
                try {
                    viewModel.writeTestLogEntry()
                    Toast.makeText(context, context.getString(R.string.test_log_written), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.write_test_log_failed, e.message), Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.write_test_log_entry))
        }

        // Button to manually check RuTracker availability
        Button(
            onClick = {
                viewModel.checkRuTrackerAvailability()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.rutracker_check_availability))
        }
    }
}
