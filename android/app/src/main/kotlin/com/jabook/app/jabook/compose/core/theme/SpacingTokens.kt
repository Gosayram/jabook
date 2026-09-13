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
 * 8dp scaffold grid per foundations/layout.
 * All paddings / gaps must derive from here, not ad-hoc literals.
 */
public object SpacingTokens {
    public val None: Dp = 0.dp
    public val Xs: Dp = 4.dp
    public val Sm: Dp = 8.dp
    public val Md: Dp = 16.dp
    public val Lg: Dp = 24.dp
    public val Xl: Dp = 32.dp

    // ponytail: scaffold 8dp spacing — single source, swap if layout spec changes.
    public val ScaffoldSpacer: Dp = 8.dp
    public val ContentPaddingCompact: Dp = 16.dp
    public val ContentPaddingMedium: Dp = 24.dp
    public val ContentPaddingExpanded: Dp = 24.dp
    public val GridMinCellCompact: Dp = 150.dp
    public val GridMinCellExpanded: Dp = 168.dp
}
