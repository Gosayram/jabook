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
 * keeping the rounded, no-gap look via explicit stroke config.
 * Stop dot is gated on track contrast per progress-indicators/page.md (<3:1 needs 4dp dot).
 *
 * @param progress Progress fraction 0..1
 * @param modifier Modifier for width/height
 * @param trackColor Background track color (defaults suit overlays on artwork;
 * pass `colorScheme`-derived colors when placed on themed surfaces)
 * @param progressColor Filled progress color
 * @param height Bar height (default 3dp for card strips)
 * @param drawStopIndicator override NTC gate; null = auto (enable on White alpha <3:1, disable on onSurface 0.12f)
 */
@Composable
public fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    progressColor: Color = Color.White.copy(alpha = 0.8f),
    height: Dp = 3.dp,
    drawStopIndicator: Boolean? = null,
) {
    // ponytail: auto gate — White 0.2f on artwork needs dot (<3:1), onSurface 0.12f on surfaceContainer already has contrast
    val shouldDrawStop = drawStopIndicator ?: (trackColor.alpha < 0.25f)
    if (shouldDrawStop) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth().height(height),
            color = progressColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
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
}
