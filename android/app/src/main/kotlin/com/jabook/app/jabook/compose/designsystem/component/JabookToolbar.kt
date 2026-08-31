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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * M3 toolbar fallbacks — Surface + Row spacedBy 16dp + CircleShape pill.
 * ponytail: M3 1.5 HorizontalFloatingToolbar (alpha) downgraded to stable Surface+Row;
 * restore HorizontalFloatingToolbar when M3 1.5 stable. Expanded is kept for API compat.
 */
@Composable
public fun JabookFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // ponytail: expanded kept for API compat — stable fallback always shows pill; collapse not emulated
    Surface(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) leadingContent()
            content()
            if (trailingContent != null) trailingContent()
        }
    }
}

@Composable
public fun JabookDockedToolbar(
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // ponytail: docked = pill full-width 16dp pad
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) leadingContent()
            content()
            if (trailingContent != null) trailingContent()
        }
    }
}

/**
 * Player context toolbar — replaces overflow bottom sheet for quick actions.
 * Host should manage [expanded] via scroll or manual; collapsed state hides to FAB-like affordance.
 */
@Composable
public fun JabookPlayerFloatingToolbar(
    expanded: Boolean,
    onFabClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // ponytail: floatingActionButton overload not used — keep minimal until stable
    JabookFloatingToolbar(
        expanded = expanded,
        modifier = modifier,
        content = content,
    )
}
