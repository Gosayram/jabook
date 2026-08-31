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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive toolbars — floating (H 16dp V 24dp) collapses to FAB, docked 64dp full-width 16dp pad.
 * Wraps official HorizontalFloatingToolbar (alpha19). Fallback faux Row not needed — official stable.
 * // ponytail: thin wrappers; caller controls expanded via scroll behavior or manual toggle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun JabookFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun JabookDockedToolbar(
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // ponytail: docked = floating toolbar always-expanded full-width 64dp, 16dp pad
    HorizontalFloatingToolbar(
        expanded = true,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        content = content,
    )
}

/**
 * Player context toolbar — replaces overflow bottom sheet for quick actions.
 * Host should manage [expanded] via scroll or manual; collapsed state hides to FAB-like affordance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun JabookPlayerFloatingToolbar(
    expanded: Boolean,
    onFabClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // ponytail: single variant; floatingActionButton overload not used — keep API minimal until H toolbar+Fab needed
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        content = content,
    )
}
