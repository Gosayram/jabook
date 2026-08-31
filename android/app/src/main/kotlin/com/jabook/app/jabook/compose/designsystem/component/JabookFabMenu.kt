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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex

/**
 * M3 Expressive FAB Menu — 2-6 items, 56dp close, medium button items, 4dp gap.
 * Wraps official FloatingActionButtonMenu (M3 1.5.0-alpha19) with fallback semantics.
 * Enter/exit from trailing corner; primary/sec/tert via [containerColor] caller scope.
 * // ponytail: official component when available, faux Row fallback never shipped — minimal wrapper.
 */
public data class JabookFabMenuItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun JabookFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<JabookFabMenuItem>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    fabContentDescription: String = "Toggle menu",
) {
    require(items.size in 2..6) { "FAB Menu requires 2-6 items, got ${items.size}" }
    // ponytail: medium spec = 56dp close morph handled by ToggleFloatingActionButton internals
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        button = {
            ToggleFloatingActionButton(
                modifier =
                    Modifier.semantics {
                        traversalIndex = -1f
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                        contentDescription = fabContentDescription
                    },
                checked = expanded,
                onCheckedChange = onExpandedChange,
            ) {
                val imageVector by remember {
                    androidx.compose.runtime.derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    item.onClick()
                    onExpandedChange(false)
                },
                icon = { Icon(item.icon, contentDescription = null) },
                text = { androidx.compose.material3.Text(item.label) },
            )
        }
    }
}

// ponytail: shared helper — animateIcon helper reuses checkedProgress scope
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.animateIcon(checkedProgressProvider: () -> Float): Modifier {
    val progress by animateFloatAsState(targetValue = checkedProgressProvider(), label = "fab_icon")
    return this
}
