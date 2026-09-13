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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.network.MirrorHealth
import com.jabook.app.jabook.ui.theme.MirrorHealthGreen
import com.jabook.app.jabook.ui.theme.MirrorHealthRed
import com.jabook.app.jabook.ui.theme.MirrorHealthYellow

@Composable
internal fun MirrorOption(
    domain: String,
    selected: Boolean,
    healthStatus: MirrorHealth?,
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
            onClick = null,
        )

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
            healthStatus is MirrorHealth.Healthy -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.available),
                    tint = MirrorHealthGreen,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                )
            }
            healthStatus is MirrorHealth.CloudflareProtected -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.cfProtected),
                    tint = MirrorHealthYellow,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                )
            }
            healthStatus is MirrorHealth.Dead -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.unavailable),
                    tint = MirrorHealthRed,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                )
            }
            else -> {
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

        TextButton(
            onClick = onCheckHealth,
            enabled = !isChecking,
        ) {
            Text(stringResource(R.string.check), style = MaterialTheme.typography.bodySmall)
        }

        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.deleteAction), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun AddMirrorDialog(
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
                    stringResource(R.string.mirrorExamplesHint),
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

internal fun extractDomain(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    val withoutProtocol = trimmed.removePrefix("https://").removePrefix("http://")
    val domain = withoutProtocol.substringBefore("/")

    return if (domain.contains(".") && !domain.contains(" ")) {
        domain
    } else {
        null
    }
}
