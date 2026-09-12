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

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.SurfaceElevationTokens
import com.jabook.app.jabook.compose.feature.library.ProductivePeriod
import com.jabook.app.jabook.compose.feature.library.WeeklyRecapState
import com.jabook.app.jabook.compose.feature.library.YearRecapState

internal fun getVersionName(context: Context): String =
    try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: context.getString(R.string.unknown)
    } catch (e: PackageManager.NameNotFoundException) {
        context.getString(R.string.unknown)
    }

@Composable
internal fun ProfileHeader(
    authStatus: com.jabook.app.jabook.compose.domain.model.AuthStatus,
    onSignIn: () -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(48.dp).background(
                            MaterialTheme.colorScheme.primaryContainer,
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column {
                    val name =
                        when (authStatus) {
                            is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated -> authStatus.username
                            else -> stringResource(R.string.settingsProfileGuest)
                        }
                    Text(text = name, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (authStatus !is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated) {
                TextButton(onClick = onSignIn) { Text(stringResource(R.string.settingsSignIn)) }
            }
        }
    }
}

@Composable
internal fun StackedSegmentedControl(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (labelText, value) ->
                SegmentedButton(
                    selected = value == selectedValue,
                    onClick = { onSelect(value) },
                    label = { Text(labelText) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                )
            }
        }
    }
}

@Composable
internal fun WeeklyRecapCard(
    stats: WeeklyRecapState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = SurfaceElevationTokens.Level2),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.weeklyRecapTitle),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                WeeklyStatItem(
                    icon = Icons.Filled.Headphones,
                    value = stats.minutesListened.toString(),
                    label = stringResource(R.string.minutesLabel),
                )
                WeeklyStatItem(
                    icon = Icons.Filled.Check,
                    value = stats.booksCompleted.toString(),
                    label = stringResource(R.string.booksLabel),
                )
                WeeklyStatItem(
                    icon = Icons.Filled.Whatshot,
                    value = stats.streakDays.toString(),
                    label = stringResource(R.string.streakDaysLabel),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.productivePeriodLabel,
                        when (stats.productivePeriod) {
                            ProductivePeriod.MORNING -> stringResource(R.string.productiveMorning)
                            ProductivePeriod.DAY -> stringResource(R.string.productiveDay)
                            ProductivePeriod.EVENING -> stringResource(R.string.productiveEvening)
                            ProductivePeriod.NIGHT -> stringResource(R.string.productiveNight)
                            ProductivePeriod.UNKNOWN -> stringResource(R.string.unknown)
                        },
                    ),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun WeeklyStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
internal fun YearRecapPromptCard(
    yearRecap: YearRecapState,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = SurfaceElevationTokens.Level1),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.yearRecapTitle, yearRecap.year),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.yearRecapShareHint, yearRecap.totalMinutesListened),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            OutlinedButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(text = stringResource(R.string.share))
            }
        }
    }
}

internal fun formatTimestamp(millis: Long): String =
    com.jabook.app.jabook.compose.util.DateTimeFormatter
        .formatGOST(millis)
