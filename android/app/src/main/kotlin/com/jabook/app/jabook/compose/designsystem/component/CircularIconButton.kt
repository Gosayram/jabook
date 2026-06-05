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

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized circular icon button with Material state layers.
 *
 * Provides three variants via [CircularIconButtonStyle]:
 * - [CircularIconButtonStyle.DEFAULT] — transparent background, standard touch target
 * - [CircularIconButtonStyle.TONAL] — tonal surface container background
 * - [CircularIconButtonStyle.FILLED] — primary-colored filled background
 *
 * All variants enforce circular shape and 48dp minimum touch target.
 *
 * @param icon ImageVector to display
 * @param contentDescription Accessibility description (required for icon-only buttons)
 * @param onClick Click handler
 * @param modifier Modifier for sizing/positioning
 * @param style Button visual variant
 * @param enabled Whether the button is interactive
 * @param size Override icon + container size (default 40dp icon / 48dp touch)
 */
@Composable
public fun CircularIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CircularIconButtonStyle = CircularIconButtonStyle.DEFAULT,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val iconModifier = Modifier.size(size).semantics(mergeDescendants = true) {}
    val colors =
        when (style) {
            CircularIconButtonStyle.DEFAULT -> IconButtonDefaults.iconButtonColors()
            CircularIconButtonStyle.TONAL -> IconButtonDefaults.filledTonalIconButtonColors()
            CircularIconButtonStyle.FILLED ->
                IconButtonDefaults.filledIconButtonColors()
        }

    when (style) {
        CircularIconButtonStyle.DEFAULT ->
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = iconModifier,
                )
            }

        CircularIconButtonStyle.TONAL ->
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = CircleShape,
                colors = colors,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = iconModifier,
                )
            }

        CircularIconButtonStyle.FILLED ->
            FilledIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = CircleShape,
                colors = colors,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = iconModifier,
                )
            }
    }
}

public enum class CircularIconButtonStyle {
    DEFAULT,
    TONAL,
    FILLED,
}
