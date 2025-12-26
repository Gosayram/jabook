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

package com.jabook.app.jabook.compose.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.CaptchaData

@Suppress("DEPRECATION") // hiltViewModel is from correct package but marked deprecated in some versions
@Composable
fun AuthScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWebView: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val authStatus by viewModel.authStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Constants for padding
    val spacerHeight = 16.dp
    val sectionSpacing = 24.dp

    // Handle WebView Login Navigation
    LaunchedEffect(uiState.showWebViewLogin) {
        if (uiState.showWebViewLogin) {
            onNavigateToWebView("https://rutracker.org/forum/login.php")
        }
    }

    // Sync cookies on Resume (returning from WebView)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (uiState.showWebViewLogin) {
                        viewModel.onWebViewLoginCompleted()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(authStatus) {
        if (authStatus is AuthStatus.Authenticated) {
            onNavigateBack()
        }
    }

    // Pre-fill if saved credentials exist
    LaunchedEffect(uiState.savedCredentials) {
        uiState.savedCredentials?.let {
            username = it.username
            password = it.password
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Header Section
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Placeholder
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(R.string.rutrackerAuth),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            Text(
                text = stringResource(R.string.login_description_placeholder), // Assuming this string or creating a placeholder
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            // Input Fields Section
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentType = ContentType.Username
                        },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(spacerHeight))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    val image =
                        if (passwordVisible) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        }

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = image,
                            contentDescription =
                                if (passwordVisible) {
                                    stringResource(R.string.hidePassword)
                                } else {
                                    stringResource(R.string.showPassword)
                                },
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentType = ContentType.Password
                        },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                )
                Text(
                    text = stringResource(R.string.rememberMe),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            // Actions Section
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CircularProgressIndicator()
            }

            AnimatedVisibility(
                visible = !uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { viewModel.login(username, password, rememberMe) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = username.isNotBlank() && password.isNotBlank(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.login))
                    }

                    Text(
                        text = stringResource(R.string.or),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = { viewModel.requestWebViewLogin() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.loginViaWebView))
                    }
                }
            }
        }
    }

    if (uiState.captchaData != null) {
        CaptchaDialog(
            captchaData = uiState.captchaData!!,
            onConfirm = { code ->
                viewModel.login(
                    username,
                    password,
                    rememberMe,
                    code,
                )
            },
            onDismiss = { viewModel.dismissCaptcha() },
        )
    }
}

@Composable
fun CaptchaDialog(
    captchaData: CaptchaData,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.captchaRequired),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncImage(
                    model = captchaData.url,
                    contentDescription = stringResource(R.string.captchaImage),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.enterCode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.isNotBlank(),
            ) {
                Text(stringResource(R.string.verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@kotlinx.serialization.Serializable
object AuthRoute
