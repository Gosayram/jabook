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

package com.jabook.app.jabook.compose.core.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 state layer opacities per foundations/interaction/states/page.md:64.
 *
 * State layer is an overlay using the on-color (content color) at fixed opacity.
 * Size: 40dp layer inside 48dp minimum touch target (a11y).
 * ponytail: tokens only — M3 components (IconButton, FilledIconButton) already apply these via ripple/indication;
 *           custom overlays can use onColor.copy(alpha = HoverOpacity) etc. No new dep, stdlib Modifier.
 */
public object StateLayerTokens {
    /** Hover — 8% */
    @Suppress("ConstPropertyName")
    public const val HOVER_OPACITY: Float = 0.08f

    /** Focus — 10% (spec 10-12%; M3 spec uses 0.10) */
    @Suppress("ConstPropertyName")
    public const val FOCUS_OPACITY: Float = 0.10f

    /** Pressed — 10% (ripple) */
    @Suppress("ConstPropertyName")
    public const val PRESSED_OPACITY: Float = 0.10f

    /** Dragged — 16% */
    @Suppress("ConstPropertyName")
    public const val DRAGGED_OPACITY: Float = 0.16f

    /** State layer visual size — 40dp centered in 48dp hit target */
    @Suppress("PropertyName")
    public val StateLayerSize: Dp = 40.dp

    /** Minimum interactive target — 48dp per M3 a11y */
    @Suppress("PropertyName")
    public val MinTouchTarget: Dp = 48.dp

    // Aliases for existing PascalCase consumers
    @Suppress("ConstPropertyName")
    public const val HoverOpacity: Float = HOVER_OPACITY

    @Suppress("ConstPropertyName")
    public const val FocusOpacity: Float = FOCUS_OPACITY

    @Suppress("ConstPropertyName")
    public const val PressedOpacity: Float = PRESSED_OPACITY

    @Suppress("ConstPropertyName")
    public const val DraggedOpacity: Float = DRAGGED_OPACITY
}
