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

package com.jabook.app.jabook.compose.designsystem.component

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.getErrorMessage
import com.jabook.app.jabook.compose.core.util.getStringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standard error screen with icon, message, and optional retry action.
 *
 * Supports both string messages and Throwable for better error handling.
 *
 * @param message Error message to display (if throwable is null)
 * @param throwable Optional throwable error (takes precedence over message)
 * @param modifier Modifier to be applied to the container
 * @param onRetry Optional retry action callback
 * @param details Optional full error detail text. When provided, a "View details"
 * button opens a dialog with the selectable full text and a copy-to-clipboard action.
 * Pass null (default) for the plain icon/message/retry layout.
 */
@Composable
public fun ErrorScreen(
    message: String = "",
    throwable: Throwable? = null,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    details: String? = null,
) {
    val errorMessage =
        if (throwable != null) {
            throwable.getErrorMessage()
        } else {
            message.ifEmpty { stringResource(R.string.error_something_goes_wrong) }
        }

    val errorStringRes =
        if (throwable != null) {
            throwable.getStringRes()
        } else {
            R.string.error_something_goes_wrong
        }

    var showDetailsDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = stringResource(R.string.error),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = errorMessage.ifEmpty { stringResource(errorStringRes) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (details != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showDetailsDialog = true }) {
                Text(stringResource(R.string.errorDetailsTitle))
            }
        }

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retryButton))
            }
        }
    }

    if (showDetailsDialog && details != null) {
        ErrorDetailsDialog(details = details, onDismiss = { showDetailsDialog = false })
    }
}

/**
 * Dialog with the full selectable error detail text and a copy-to-clipboard
 * action that shows a transient checkmark after copying.
 */
@Composable
private fun ErrorDetailsDialog(
    details: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.errorDetailsTitle)) },
        text = {
            SelectionContainer {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("errorDetails", details)),
                        )
                        copied = true
                    }
                },
            ) {
                if (copied) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.errorDetailsCopied))
                } else {
                    Text(stringResource(R.string.copyToClipboardLabel))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
