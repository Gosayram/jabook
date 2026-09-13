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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass

/**
 * One entry of an [AdaptiveMenu].
 *
 * @property title Row label
 * @property leadingIcon Optional icon before the title; replaced by a check
 *   mark in the compact sheet when [isSelected]
 * @property leadingIconTint Optional tint for [leadingIcon]; defaults to the
 *   theme content color
 * @property subtitle Optional secondary line under the title
 * @property isSelected Renders a primary-tinted check mark: trailing in the
 *   dropdown variant, leading in the compact sheet variant
 * @property enabled Disabled rows are not clickable
 * @property onClick Invoked on row click; does NOT auto-dismiss — invoke the
 *   menu's `onDismiss` inside this callback when needed
 */
public data class AdaptiveMenuItem(
    val title: String,
    val leadingIcon: ImageVector? = null,
    val leadingIconTint: Color? = null,
    val subtitle: String? = null,
    val isSelected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Adaptive menu rendering the same [items] as a [DropdownMenu] on medium/expanded
 * window widths and as a [JabookModalBottomSheet] menu on compact widths
 * (M3 guidance: bottom sheets on compact, menus on larger screens).
 *
 * Controlled component: the caller owns visibility via [expanded] and closes
 * it via [onDismiss]. Compose it inside the anchor layout (e.g. a Box behind
 * the trigger IconButton) so the dropdown variant anchors correctly; the sheet
 * variant is a modal popup and ignores anchoring.
 *
 * @param expanded Whether the menu is currently shown
 * @param onDismiss Called when the user dismisses the menu (scrim tap for the
 *   sheet, outside tap for the dropdown)
 * @param items Menu entries rendered in order
 * @param headerTitle Optional title shown above the items in the sheet variant
 * @param modifier Modifier for the dropdown variant; ignored by the modal sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AdaptiveMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<AdaptiveMenuItem>,
    headerTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    // ponytail: raw width class decides; resolve/override per call-site if a screen ever needs to
    val isCompact = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Compact
    if (isCompact) {
        if (expanded) {
            JabookModalBottomSheet(
                onDismissRequest = onDismiss,
                title = headerTitle,
            ) {
                items.forEach { item ->
                    val supporting: (@Composable () -> Unit)? =
                        item.subtitle?.let { subtitle ->
                            { Text(text = subtitle) }
                        }
                    ListItem(
                        headlineContent = { Text(text = item.title) },
                        supportingContent = supporting,
                        leadingContent = {
                            when {
                                item.isSelected ->
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )

                                item.leadingIcon != null ->
                                    Icon(
                                        imageVector = item.leadingIcon,
                                        contentDescription = null,
                                        tint = item.leadingIconTint ?: Color.Unspecified,
                                    )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                // M3: unavailable items stay visible but appear disabled (0.38 content alpha)
                                .alpha(if (item.enabled) 1f else 0.38f)
                                .clickable(enabled = item.enabled) { item.onClick() },
                    )
                }
            }
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = modifier,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        val subtitle = item.subtitle
                        if (subtitle != null) {
                            Column {
                                Text(text = item.title)
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(text = item.title)
                        }
                    },
                    onClick = item.onClick,
                    enabled = item.enabled,
                    leadingIcon = {
                        if (item.leadingIcon != null) {
                            Icon(
                                imageVector = item.leadingIcon,
                                contentDescription = null,
                                tint = item.leadingIconTint ?: Color.Unspecified,
                            )
                        }
                    },
                    trailingIcon = {
                        if (item.isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}
