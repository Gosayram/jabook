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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.domain.model.SearchFilters

/**
 * Bottom sheet for search filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersSheet(
    filters: SearchFilters,
    onApplyFilters: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    var minSeeders by remember(filters.minSeeders) { mutableStateOf(filters.minSeeders?.toString() ?: "") }

    // Size range in MB (0 to 10GB)
    val maxFileSizeMB = 10000f
    var sizeRange by remember(filters.minSize, filters.maxSize) {
        val start = (filters.minSize ?: 0L) / (1024f * 1024f)
        val end = (filters.maxSize ?: (maxFileSizeMB * 1024 * 1024).toLong()) / (1024f * 1024f)
        mutableStateOf(start..end)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Status (Seeders)
            Text(
                text = "Minimum Seeders",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = minSeeders,
                onValueChange = { if (it.all { char -> char.isDigit() }) minSeeders = it },
                label = { Text("Count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // Size Range
            Text(
                text = "Size Range: ${formatSize(sizeRange.start)} - ${formatSize(sizeRange.endInclusive)}",
                style = MaterialTheme.typography.titleMedium,
            )
            RangeSlider(
                value = sizeRange,
                onValueChange = { sizeRange = it },
                valueRange = 0f..maxFileSizeMB,
                steps = 100, // Steps of ~100MB
            )

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = {
                        // Reset
                        onApplyFilters(SearchFilters())
                        onDismiss()
                    },
                ) {
                    Text("Reset")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onApplyFilters(
                            SearchFilters(
                                minSeeders = minSeeders.toIntOrNull(),
                                minSize = (sizeRange.start * 1024 * 1024).toLong(),
                                maxSize =
                                    if (sizeRange.endInclusive >=
                                        maxFileSizeMB
                                    ) {
                                        null
                                    } else {
                                        (sizeRange.endInclusive * 1024 * 1024).toLong()
                                    },
                            ),
                        )
                        onDismiss()
                    },
                ) {
                    Text("Apply")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatSize(mb: Float): String {
    if (mb >= 1024) {
        return "%.1f GB".format(mb / 1024)
    }
    return "%.0f MB".format(mb)
}
