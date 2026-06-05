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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Deterministic fallback cover layouts when no real artwork is available.
 *
 * Generates visually distinct covers from book metadata so the same book
 * always produces the same cover.
 *
 * @param title Book title (used for initials overlay and layout seed)
 * @param author Book author (used for layout seed)
 * @param modifier Modifier for sizing
 * @param layout Which generative layout to use (auto-selected when null)
 * @param size Cover dimensions (square)
 */
@Composable
public fun GenerativeFallbackCover(
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    layout: FallbackLayout? = null,
    size: Dp = 120.dp,
) {
    val palette = rememberCoverPalette(title, author)
    val selectedLayout =
        layout
            ?: remember(title, author) {
                FallbackLayout.entries[(title.hashCode().absoluteValue) % FallbackLayout.entries.size]
            }

    val initials =
        remember(title) {
            title.take(2).uppercase()
        }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        when (selectedLayout) {
            FallbackLayout.RADIAL_ORB -> RadialOrbLayout(palette = palette)
            FallbackLayout.DIAGONAL_SPLIT -> DiagonalSplitLayout(palette = palette)
            FallbackLayout.HORIZONTAL_BANDS -> HorizontalBandsLayout(palette = palette)
            FallbackLayout.CONCENTRIC_RINGS -> ConcentricRingsLayout(palette = palette)
            FallbackLayout.CENTRAL_GLOW -> CentralGlowLayout(palette = palette)
            FallbackLayout.EQUALIZER_BARS -> EqualizerBarsLayout(title = title, palette = palette)
        }

        val textMeasurer = rememberTextMeasurer()
        val baseStyle = MaterialTheme.typography.labelLarge
        val style =
            remember(baseStyle) {
                baseStyle.copy(color = palette.onColor)
            }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val measured = textMeasurer.measure(initials, style)
            drawText(
                textLayoutResult = measured,
                topLeft =
                    Offset(
                        (size.toPx() - measured.size.width) / 2f,
                        (size.toPx() - measured.size.height) / 2f,
                    ),
            )
        }
    }
}

public enum class FallbackLayout {
    RADIAL_ORB,
    DIAGONAL_SPLIT,
    HORIZONTAL_BANDS,
    CONCENTRIC_RINGS,
    CENTRAL_GLOW,
    EQUALIZER_BARS,
}

internal data class CoverPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val onColor: Color,
)

@Composable
internal fun rememberCoverPalette(
    title: String,
    author: String,
): CoverPalette {
    val baseHue =
        remember(title, author) {
            ((title.hashCode() * 31 + author.hashCode()) % 360).absoluteValue.toFloat()
        }
    val primary = remember(baseHue) { Color(android.graphics.Color.HSVToColor(floatArrayOf(baseHue, 0.55f, 0.65f))) }
    val secondary =
        remember(baseHue) { Color(android.graphics.Color.HSVToColor(floatArrayOf((baseHue + 40) % 360, 0.4f, 0.5f))) }
    val tertiary =
        remember(baseHue) { Color(android.graphics.Color.HSVToColor(floatArrayOf((baseHue + 80) % 360, 0.35f, 0.45f))) }
    val background =
        remember(baseHue) { Color(android.graphics.Color.HSVToColor(floatArrayOf(baseHue, 0.15f, 0.18f))) }
    val onColor =
        remember(baseHue) { Color(android.graphics.Color.HSVToColor(floatArrayOf(baseHue, 0.1f, 0.92f))) }

    return remember(primary, secondary, tertiary, background) {
        CoverPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            onColor = onColor,
        )
    }
}

private val Int.absoluteValue: Int get() = if (this < 0) -this else this

@Composable
private fun RadialOrbLayout(palette: CoverPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.background)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.minDimension / 2f
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to palette.primary.copy(alpha = 0.9f),
                    0.7f to palette.secondary.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = maxR,
                ),
            radius = maxR,
            center = Offset(cx, cy),
        )
    }
}

@Composable
private fun DiagonalSplitLayout(palette: CoverPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.primary)
        val path =
            Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                close()
            }
        drawPath(path, palette.secondary, style = Fill)
    }
}

@Composable
private fun HorizontalBandsLayout(palette: CoverPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val bandHeight = size.height / 3f
        drawRect(palette.primary, topLeft = Offset.Zero, size = Size(size.width, bandHeight))
        drawRect(palette.secondary, topLeft = Offset(0f, bandHeight), size = Size(size.width, bandHeight))
        drawRect(palette.tertiary, topLeft = Offset(0f, bandHeight * 2), size = Size(size.width, bandHeight))
    }
}

@Composable
private fun ConcentricRingsLayout(palette: CoverPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.background)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.minDimension / 2f
        val ringCount = 4
        for (i in ringCount downTo 1) {
            val fraction = i.toFloat() / ringCount
            drawCircle(
                color = palette.primary.copy(alpha = 0.2f + fraction * 0.3f),
                radius = maxR * fraction,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun CentralGlowLayout(palette: CoverPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.background)
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to palette.primary.copy(alpha = 0.7f),
                    0.5f to palette.secondary.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = size.minDimension / 2f,
                ),
            radius = size.minDimension / 2f,
            center = Offset(cx, cy),
        )
    }
}

@Composable
private fun EqualizerBarsLayout(
    title: String,
    palette: CoverPalette,
) {
    val barHeights =
        remember(title) {
            val seed = title.hashCode()
            FloatArray(8) { i ->
                val v = ((seed * (i + 1) * 31) % 100).absoluteValue
                0.2f + v / 100f * 0.7f
            }
        }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.background)
        val barCount = barHeights.size
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (barCount + 1)) / barCount
        for (i in 0 until barCount) {
            val barHeight = size.height * barHeights[i] * 0.7f
            val x = gap + i * (barWidth + gap)
            val y = size.height - barHeight - gap
            drawRect(
                color = if (i % 2 == 0) palette.primary else palette.secondary,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
            )
        }
    }
}
