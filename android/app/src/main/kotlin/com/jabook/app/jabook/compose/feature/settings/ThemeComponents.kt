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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.getAllAccentSwatches
import com.jabook.app.jabook.compose.data.model.AppTheme

@Composable
internal fun ThemeSelector(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        ThemeLivePreviewCard(selectedTheme = selectedTheme)
        Spacer(modifier = Modifier.height(12.dp))

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
private fun ThemeLivePreviewCard(
    selectedTheme: AppTheme,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.themePreviewTitle),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.themePreviewBookTitle),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            text =
                                when (selectedTheme) {
                                    AppTheme.SYSTEM -> stringResource(R.string.systemDefault)
                                    AppTheme.LIGHT -> stringResource(R.string.light)
                                    AppTheme.DARK -> stringResource(R.string.dark)
                                    AppTheme.AMOLED -> stringResource(R.string.themeAmoled)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
            onClick = null,
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
internal fun FontSelector(
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
            onValueChange = {},
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

@Composable
internal fun AccentSwatchSelector(
    selectedIndex: Int,
    onSwatchSelected: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        getAllAccentSwatches().forEachIndexed { index, swatch ->
            val isSelected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(
                            color = swatch.primary,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ).then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                )
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                )
                            },
                        ).clickable(onClick = { onSwatchSelected(index) })
                        .semantics {
                            selected = isSelected
                            contentDescription = swatch.name
                        },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = swatch.name,
                        modifier = Modifier.size(20.dp),
                        tint =
                            if (swatch.primary.luminance() < 0.5f) {
                                androidx.compose.ui.graphics.Color.White
                            } else {
                                androidx.compose.ui.graphics.Color.Black
                            },
                    )
                }
            }
        }
    }
}
