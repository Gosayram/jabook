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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import com.jabook.app.jabook.compose.navigation.TopLevelDestination

/**
 * Jabook Navigation Rail for medium/expanded screens.
 *
 * @param destinations List of top-level destinations
 * @param currentDestination Current navigation destination
 * @param onNavigateToDestination Callback when a destination is selected
 * @param modifier Modifier to be applied to the layout
 * @param header Optional header composable (e.g. Floating Action Button)
 */
@Composable
public fun JabookNavigationRail(
    destinations: List<TopLevelDestination>,
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (ColumnScope.() -> Unit)? = null,
) {
    NavigationRail(
        modifier = modifier,
        header = header,
    ) {
        destinations.forEach { destination ->
            val isSelected = currentDestination.isTopLevelDestinationInHierarchy(destination)

            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(destination.iconTextId),
                    )
                },
                label = { Text(stringResource(destination.titleTextId)) },
            )
        }
    }
}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination): Boolean =
    this?.route?.contains(destination.name, ignoreCase = true) == true
