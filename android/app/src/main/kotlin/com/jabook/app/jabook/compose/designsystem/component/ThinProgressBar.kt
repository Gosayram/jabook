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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thin progress bar for cards, mini-player, detail progress, and downloads.
 *
 * Delegates to Material3 [LinearProgressIndicator] so it gains progress
 * semantics (screen-reader announcements) and RTL support for free, while
 * keeping the rounded, no-gap, no-stop-dot look via explicit stroke config.
 *
 * @param progress Progress fraction 0..1
 * @param modifier Modifier for width/height
 * @param trackColor Background track color
 * @param progressColor Filled progress color
 * @param height Bar height (default 3dp for card strips)
 */
@Composable
public fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    progressColor: Color = Color.White.copy(alpha = 0.8f),
    height: Dp = 3.dp,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(height),
        color = progressColor,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
