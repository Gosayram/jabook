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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
 * SplitButton fallback — Row with two Buttons weight + 2dp gap.
 * ponytail: M3 1.5 SplitButtonLayout (alpha) downgraded to stable Row; restore SplitButtonLayout
 * when M3 1.5 stable. Size param kept for API compat (no-op until stable).
 */
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
    // ponytail: faux connected split — 2dp gap, connectedItemShape; replace with SplitButtonDefaults when stable
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Button(
            onClick = onLeadingClick,
            enabled = enabled,
            shape = connectedItemShape(0, 2),
            modifier = Modifier.weight(1f),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(label)
        }
        FilledTonalButton(
            onClick = onTrailingClick,
            enabled = enabled,
            shape = connectedItemShape(1, 2),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = trailingContentDescription,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

/**
 * Speed split — leading shows current speed + hold-to-boost, trailing opens speed sheet.
 * Keeps interactionSource out for minimal API; wire hold via long-press at caller if needed.
 */
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
