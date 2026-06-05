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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Jabook-styled switch with animated thumb and optional check icon.
 * Uses Material state layers and provides 48dp touch target.
 */
@Composable
public fun JaBookSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val thumbScale by animateFloatAsState(
        targetValue = if (checked) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "thumbScale",
    )

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        alpha = abs(thumbScale)
                        scaleX = thumbScale
                        scaleY = thumbScale
                    },
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledCheckedThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledCheckedTrackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ),
    )
}