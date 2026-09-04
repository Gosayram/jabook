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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp

/**
 * FAB Menu fallback — AnimatedVisibility + Column (standard library), 56dp close via FloatingActionButton.
 * ponytail: M3 1.5 FloatingActionButtonMenu (alpha) downgraded to stable; restore official when M3 1.5 stable.
 * 2-6 items, 4dp gap, enter/exit from trailing corner emulated via fade+expand.
 */
public data class JabookFabMenuItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
    val onClick: () -> Unit,
)

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
    // ponytail: stable Column + AnimatedVisibility; ToggleFloatingActionButton replaced by standard FAB (56dp)
    Box(modifier = modifier) {
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Column(
                    horizontalAlignment = horizontalAlignment,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // ponytail: ExtendedFAB for label; SmallFAB for icon — mimics FloatingActionButtonMenuItem
                            ExtendedFloatingActionButton(
                                onClick = {
                                    item.onClick()
                                    onExpandedChange(false)
                                },
                                icon = { Icon(item.icon, contentDescription = null) },
                                text = { Text(item.label) },
                            )
                        }
                    }
                }
            }
            // ponytail: 56dp close — standard FloatingActionButton is 56dp; keep semantics from alpha wrapper
            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                modifier =
                    Modifier
                        .size(56.dp)
                        .semantics {
                            traversalIndex = -1f
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                            contentDescription = fabContentDescription
                        }.align(horizontalAlignment.toColumnAlign()),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = null,
                )
            }
        }
    }
}

private fun Alignment.Horizontal.toColumnAlign(): Alignment.Horizontal =
    when (this) {
        Alignment.Start -> Alignment.Start
        Alignment.CenterHorizontally -> Alignment.CenterHorizontally
        else -> Alignment.End
    }
