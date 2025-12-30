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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.getErrorMessage
import com.jabook.app.jabook.compose.core.util.getStringRes

/**
 * Standard error screen with icon, message, and optional retry action.
 *
 * Supports both string messages and Throwable for better error handling.
 *
 * @param message Error message to display (if throwable is null)
 * @param throwable Optional throwable error (takes precedence over message)
 * @param modifier Modifier to be applied to the container
 * @param onRetry Optional retry action callback
 */
@Composable
fun ErrorScreen(
    message: String = "",
    throwable: Throwable? = null,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
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

    Column(
        modifier =
            modifier
                .fillMaxSize()
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

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retryButton))
            }
        }
    }
}
