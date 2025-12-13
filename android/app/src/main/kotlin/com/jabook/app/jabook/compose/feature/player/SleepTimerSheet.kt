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

package com.jabook.app.jabook.compose.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.domain.model.SleepTimerState

/**
 * Bottom sheet for managing sleep timer.
 *
 * @param currentState Current sleep timer state
 * @param onStartTimer Callback when timer duration is selected
 * @param onCancelTimer Callback to cancel active timer
 * @param onDismiss Callback to dismiss the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    currentState: SleepTimerState,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState =
        androidx.compose.material3.rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Таймер сна",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))

            when (currentState) {
                is SleepTimerState.Idle -> {
                    // Timer options
                    val durations = listOf(5, 10, 15, 30, 45, 60)

                    durations.forEach { minutes ->
                        ListItem(
                            headlineContent = {
                                Text("$minutes минут${if (minutes == 1) "а" else ""}")
                            },
                            leadingContent = {
                                androidx.compose.material3.Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onStartTimer(minutes)
                                    onDismiss()
                                },
                        )
                    }
                }

                is SleepTimerState.Active -> {
                    // Show countdown
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                    ) {
                        Text(
                            text = "Таймер активен",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        Text(
                            text = currentState.formattedTime,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Text(
                            text = "Воспроизведение автоматически остановится",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                onCancelTimer()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) {
                            Text("Отменить таймер")
                        }
                    }
                }
            }
        }
    }
}
