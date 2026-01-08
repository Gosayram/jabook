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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.mikepenz.hypnoticcanvas.shaders.BlackCherryCosmos
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Premium animated background component using Shaders (Android 13+) or Gradient fallback.
 */
@Composable
fun PremiumPlayerBackground(
    themeColors: PlayerThemeColors?,
    hazeState: HazeState? = null,
    isPowerSaveMode: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val useShader = !isPowerSaveMode && themeColors != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val fallbackBackgroundModifier =
        if (themeColors != null) {
            Modifier.background(
                brush =
                    Brush.verticalGradient(
                        colors =
                            themeColors.gradientColors.ifEmpty {
                                listOf(themeColors.containerColor, themeColors.surfaceColor)
                            },
                    ),
            )
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

    // Apply Haze if state is provided AND not in power save mode
    val finalModifier =
        modifier
            .fillMaxSize()
            .then(if (hazeState != null && !isPowerSaveMode) Modifier.hazeSource(state = hazeState) else Modifier)

    Box(modifier = finalModifier) {
        if (useShader) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .shaderBackground(BlackCherryCosmos),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().then(fallbackBackgroundModifier),
            )
        }

        // Darkening overlay for text legibility
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
        )

        content()
    }
}
