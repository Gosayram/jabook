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

package com.jabook.app.jabook.compose.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

/**
 * Content for the Jabook Navigation Drawer.
 *
 * Features:
 * - Account Header (MaterialDrawer style)
 * - Scrollable list of Top Level Destinations
 * - Sticky Footer for Settings/About
 */
@Composable
public fun JabookDrawerContent(
    destinations: List<TopLevelDestination>,
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    accountProfile: AccountProfile = AccountProfile("Guest User", "guest@jabook.app"), // TODO: Real user data
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.fillMaxWidth(0.85f), // Slightly wider than default
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Sticky Header
        AccountHeader(
            selectedAccount = accountProfile,
            onAccountClick = { /* TODO: Account switching */ },
        )

        // Scrollable Content
        Column(
            modifier =
                Modifier
                    .weight(1f) // Takes remaining space
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
        ) {
            // Main Destinations
            destinations.forEach { destination ->
                val selected =
                    currentDestination?.hierarchy?.any {
                        it.route?.contains(destination.name, ignoreCase = true) == true
                    } == true

                NavigationDrawerItem(
                    label = { Text(stringResource(destination.titleTextId)) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    selected = selected,
                    onClick = { onNavigateToDestination(destination) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }

        // Sticky Footer (Divider + Secondary Items)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
        ) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Settings
            NavigationDrawerItem(
                label = { Text("Settings") }, // TODO: strings.xml
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = onNavigateToSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            // About
            NavigationDrawerItem(
                label = { Text("About") }, // TODO: strings.xml
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                selected = false,
                onClick = onNavigateToAbout,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}
