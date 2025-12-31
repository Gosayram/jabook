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

package com.jabook.app.jabook.compose.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.constants.PlaybackSpeedConstants

/**
 * Bottom sheet for selecting playback speed.
 *
 * @param currentSpeed Current playback speed (e.g., 1.0f)
 * @param onSpeedSelected Callback when speed is selected
 * @param onDismiss Callback to dismiss the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.playbackSpeed),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            LazyColumn {
                items(PlaybackSpeedConstants.generateSpeedsList()) { speed ->
                    SpeedOption(
                        speed = speed,
                        isSelected = speed == currentSpeed,
                        onClick = {
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedOption(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(PlaybackSpeedConstants.formatSpeed(speed))
        },
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
