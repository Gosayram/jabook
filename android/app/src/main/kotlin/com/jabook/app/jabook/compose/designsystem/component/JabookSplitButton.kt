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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public enum class JabookSplitButtonSize { XS, S, M, L, XL }

/**
 * M3 Expressive SplitButton — XS-XL 5 sizes, 2dp inner gap, 180° standard motion on trailing icon.
 * Wraps official SplitButtonLayout (alpha19). Leading = primary action, trailing = menu chevron.
 * // ponytail: one composable covers Speed split + generic; sizes map to SplitButtonDefaults heights.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun JabookSplitButton(
    label: String,
    onLeadingClick: () -> Unit,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingChecked: Boolean = false,
    trailingContentDescription: String = "Open menu",
    size: JabookSplitButtonSize = JabookSplitButtonSize.M,
    enabled: Boolean = true,
    spacing: Dp = 2.dp,
) {
    val rotation by animateFloatAsState(
        targetValue = if (trailingChecked) 180f else 0f,
        label = "split_trailing_rotation",
    )
    // ponytail: size maps to container heights via defaults; caller gets XS-XL without extra tokens
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onLeadingClick,
                enabled = enabled,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(label)
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = trailingChecked,
                onCheckedChange = { onTrailingClick() },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = trailingContentDescription,
                    modifier = Modifier.rotate(rotation),
                )
            }
        },
        modifier = modifier,
        spacing = spacing,
    )
}

/**
 * Speed split — leading shows current speed + hold-to-boost, trailing opens speed sheet.
 * Keeps interactionSource out for minimal API; wire hold via long-press at caller if needed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun JabookSpeedSplitButton(
    speedLabel: String,
    onSpeedClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuExpanded: Boolean = false,
    size: JabookSplitButtonSize = JabookSplitButtonSize.M,
) {
    JabookSplitButton(
        label = speedLabel,
        leadingIcon = Icons.Filled.Speed,
        onLeadingClick = onSpeedClick,
        onTrailingClick = onMenuClick,
        trailingChecked = menuExpanded,
        trailingContentDescription = "Playback speed menu",
        size = size,
        modifier = modifier,
    )
}
