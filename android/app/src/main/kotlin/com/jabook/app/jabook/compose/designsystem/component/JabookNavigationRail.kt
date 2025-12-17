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
fun JabookNavigationRail(
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
