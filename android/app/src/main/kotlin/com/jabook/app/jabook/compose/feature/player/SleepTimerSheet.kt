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

import android.app.TimePickerDialog
import android.text.format.DateFormat
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import java.time.LocalDateTime

/**
 * Bottom sheet for managing sleep timer.
 *
 * @param currentState Current sleep timer state
 * @param lastUsedDurationMinutes Last used fixed timer duration in minutes
 * @param onStartTimer Callback when timer duration is selected
 * @param onStartTimerEndOfChapter Callback when "End of Chapter" is selected
 * @param onStartTimerEndOfTrack Callback when "End of Track" is selected
 * @param onCancelTimer Callback to cancel active timer
 * @param onDismiss Callback to dismiss the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SleepTimerSheet(
    currentState: SleepTimerState,
    lastUsedDurationMinutes: Int?,
    onStartTimer: (Int) -> Unit,
    onStartTimerEndOfChapter: () -> Unit,
    onStartTimerEndOfTrack: () -> Unit,
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
        val context = LocalContext.current
        val now = LocalDateTime.now()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.sleepTimerTitle),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))

            when (currentState) {
                is SleepTimerState.Idle -> {
                    lastUsedDurationMinutes?.let { minutes ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(
                                        R.string.sleepTimerRepeatYesterday,
                                        minutes,
                                    ),
                                )
                            },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.durationMinutesFull,
                                        minutes,
                                        minutes,
                                    ),
                                )
                            },
                            leadingContent = {
                                Icon(
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

                    // End of Chapter option
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.endOfChapterLabel))
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onStartTimerEndOfChapter()
                                onDismiss()
                            },
                    )

                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.endOfTrackLabel))
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onStartTimerEndOfTrack()
                                onDismiss()
                            },
                    )

                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.stopAtSpecificTime))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.stopAtSpecificTimeDesc))
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val minutesUntilStop =
                                            SleepTimerSchedulePolicy.minutesUntil(
                                                targetHour = hourOfDay,
                                                targetMinute = minute,
                                                now = LocalDateTime.now(),
                                            )
                                        onStartTimer(minutesUntilStop)
                                        onDismiss()
                                    },
                                    now.hour,
                                    now.minute,
                                    DateFormat.is24HourFormat(context),
                                ).show()
                            },
                    )

                    // Timer options
                    val durations = listOf(5, 10, 15, 30, 45, 60)

                    durations.forEach { minutes ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.durationMinutesFull,
                                        minutes,
                                        minutes,
                                    ),
                                )
                            },
                            leadingContent = {
                                Icon(
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
                    ActiveTimerContent(
                        timeText =
                            stringResource(
                                R.string.sleep_timer_active,
                                formatSleepTimerRemaining(currentState.remainingSeconds),
                            ),
                        detailsText =
                            stringResource(
                                R.string.sleepTimerStopsAt,
                                formatSleepTimerStopAt(currentState.remainingSeconds),
                                formatSleepTimerRemaining(currentState.remainingSeconds),
                            ),
                        onCancelTimer = onCancelTimer,
                        onDismiss = onDismiss,
                    )
                }

                is SleepTimerState.EndOfChapter -> {
                    // Show End of Chapter status
                    ActiveTimerContent(
                        timeText =
                            stringResource(
                                R.string.sleep_timer_end_of_chapter,
                                stringResource(R.string.endOfChapterLabel),
                            ),
                        onCancelTimer = onCancelTimer,
                        onDismiss = onDismiss,
                    )
                }

                is SleepTimerState.EndOfTrack -> {
                    ActiveTimerContent(
                        timeText =
                            stringResource(
                                R.string.sleep_timer_end_of_track,
                                if (currentState.fallbackFromChapter) {
                                    stringResource(R.string.endOfTrackFallbackLabel)
                                } else {
                                    stringResource(R.string.endOfTrackLabel)
                                },
                            ),
                        onCancelTimer = onCancelTimer,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerContent(
    timeText: String,
    detailsText: String? = null,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.timerActive),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = timeText,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = stringResource(R.string.playbackWillStopAutomatically),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!detailsText.isNullOrBlank()) {
            Text(
                text = detailsText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                onCancelTimer()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            Text(stringResource(R.string.cancelTimer))
        }
    }
}
