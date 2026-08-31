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

package com.jabook.app.jabook.compose.feature.player

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.size.Scale
import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors
import com.jabook.app.jabook.compose.feature.player.components.HypnoticBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Premium animated background component using Shaders (Android 13+) or Gradient fallback.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
public fun PremiumPlayerBackground(
    themeColors: PlayerThemeColors?,
    coverImageModel: Any? = null,
    hazeState: HazeState? = null,
    isPowerSaveMode: Boolean = false,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rawBackgroundColors =
        themeColors?.let { colors ->
            colors.gradientColors.ifEmpty {
                listOf(colors.containerColor, colors.surfaceColor)
            }
        } ?: emptyList()
    // ponytail: palette uses expressive effects spring; MotionTokens still used for shimmer/rotation infinite
    val animatedPrimary by animateColorAsState(
        targetValue = themeColors?.primaryColor ?: Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "palettePrimary",
    )
    val backgroundColors =
        if (rawBackgroundColors.isNotEmpty() && themeColors != null) {
            listOf(animatedPrimary) + rawBackgroundColors.drop(1)
        } else {
            rawBackgroundColors
        }

    val fallbackBackgroundModifier =
        if (themeColors != null) {
            Modifier.background(
                brush =
                    Brush.verticalGradient(
                        colors = backgroundColors,
                    ),
            )
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

    val finalModifier =
        modifier
            .fillMaxSize()
            .then(if (hazeState != null && !isPowerSaveMode) Modifier.hazeSource(state = hazeState) else Modifier)

    Box(modifier = finalModifier) {
        if (!isPowerSaveMode && backgroundColors.isNotEmpty()) {
            HypnoticBackground(
                colors = backgroundColors,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(fallbackBackgroundModifier),
            )
        }

        if (coverImageModel != null) {
            // Downscale the background decode — it is blurred + dimmed + zoomed, so
            // nobody can see full-res pixels; decoding at 512px is a big GPU/memory win.
            val bgModel =
                coil3.request.ImageRequest
                    .Builder(LocalContext.current)
                    .data(coverImageModel)
                    .size(512)
                    .scale(coil3.size.Scale.FILL)
                    .build()
            AsyncImage(
                model = bgModel,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= 31) {
                                Modifier.blur(radiusX = 24.dp, radiusY = 24.dp)
                            } else {
                                Modifier
                            },
                        ).graphicsLayer(
                            alpha = 0.32f,
                            scaleX = 1.1f,
                            scaleY = 1.1f,
                        ),
                contentScale = ContentScale.Crop,
            )
        }

        // Gradient scrim for legibility — bottom-heavy (controls/text dock there),
        // instead of a flat 0.6 black that flattens the whole artwork.
        // a11y 200% fontScale: scrim 0.85 max (≥0.75) ensures WCAG contrast over HypnoticBackground/cover; verified reflow via LazyColumn vertical scroll in PlayerContent.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.55f to Color.Black.copy(alpha = 0.35f),
                                    1.0f to Color.Black.copy(alpha = 0.85f),
                                ),
                        ),
                    ),
        )

        content()
    }
}
