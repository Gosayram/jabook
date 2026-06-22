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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination
import com.jabook.app.jabook.compose.navigation.TopLevelDestination

/**
 * Jabook Navigation Rail for medium/expanded screens.
 *
 * @param destinations List of top-level destinations shown at the top
 * @param currentDestination Current navigation destination
 * @param onNavigateToDestination Callback when a destination is selected
 * @param modifier Modifier to be applied to the layout
 * @param header Optional header composable (e.g. brand mark)
 * @param bottomDestinations Optional destinations pinned to the bottom (e.g. settings)
 * @param badgeCounts Map of destination to badge count for showing notification badges
 */
@Composable
public fun JabookNavigationRail(
    destinations: List<TopLevelDestination>,
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    bottomDestinations: List<TopLevelDestination> = emptyList(),
    badgeCounts: Map<TopLevelDestination, Int> = emptyMap(),
) {
    NavigationRail(
        modifier = modifier,
        header = header,
    ) {
        destinations.forEach { destination ->
            RailItem(
                destination = destination,
                isSelected = currentDestination.isTopLevelDestinationInHierarchy(destination),
                onClick = { onNavigateToDestination(destination) },
                badgeCount = badgeCounts[destination] ?: 0,
            )
        }

        if (bottomDestinations.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            bottomDestinations.forEach { destination ->
                RailItem(
                    destination = destination,
                    isSelected = currentDestination.isTopLevelDestinationInHierarchy(destination),
                    onClick = { onNavigateToDestination(destination) },
                    badgeCount = badgeCounts[destination] ?: 0,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RailItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int,
) {
    NavigationRailItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            val icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                    },
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(destination.iconTextId),
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(destination.iconTextId),
                )
            }
        },
        label = {
            Text(
                text = stringResource(destination.titleTextId),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        },
    )
}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination): Boolean =
    this?.route?.contains(destination.name, ignoreCase = true) == true
