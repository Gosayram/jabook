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

package com.jabook.app.jabook.compose.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.SearchFilters

/**
 * Side panel component for search filters on wide screens.
 *
 * This component is shown in the supporting pane of SupportingPaneScaffold on medium/expanded screens.
 * It provides filter controls for online search results.
 *
 * @param filters Current filter values
 * @param onApplyFilters Callback when filters are applied
 * @param onReset Callback to reset filters
 * @param modifier Modifier for the root composable
 */
@Composable
fun SearchFiltersPane(
    filters: SearchFilters,
    onApplyFilters: (SearchFilters) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var minSeeders by remember(filters.minSeeders) { mutableStateOf(filters.minSeeders?.toString() ?: "") }

    // Size range in MB (0 to 10GB)
    val maxFileSizeMB = 10000f
    var sizeRange by remember(filters.minSize, filters.maxSize) {
        val start = (filters.minSize ?: 0L) / (1024f * 1024f)
        val end = (filters.maxSize ?: (maxFileSizeMB * 1024 * 1024).toLong()) / (1024f * 1024f)
        mutableStateOf(start..end)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filters),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        HorizontalDivider()

        // Filter content
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Minimum Seeders
            Text(
                text = stringResource(R.string.minimumSeeders),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = minSeeders,
                onValueChange = { if (it.all { char -> char.isDigit() }) minSeeders = it },
                label = { Text(stringResource(R.string.count)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Size Range
            Text(
                text = "Size Range",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${formatSize(sizeRange.start)} - ${formatSize(sizeRange.endInclusive)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RangeSlider(
                value = sizeRange,
                onValueChange = { sizeRange = it },
                valueRange = 0f..maxFileSizeMB,
                steps = 100, // Steps of ~100MB
            )
        }

        HorizontalDivider()

        // Action buttons
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onReset()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.resetButton))
            }
            Button(
                onClick = {
                    onApplyFilters(
                        SearchFilters(
                            minSeeders = minSeeders.toIntOrNull(),
                            minSize = (sizeRange.start * 1024 * 1024).toLong(),
                            maxSize =
                                if (sizeRange.endInclusive >= maxFileSizeMB) {
                                    null
                                } else {
                                    (sizeRange.endInclusive * 1024 * 1024).toLong()
                                },
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.applyButton))
            }
        }
    }
}

/**
 * Formats a file size from MB to human-readable string.
 */
private fun formatSize(mb: Float): String {
    if (mb >= 1024) {
        return String.format("%.1f GB", mb / 1024)
    }
    return String.format("%.0f MB", mb)
}
