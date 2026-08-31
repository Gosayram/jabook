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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
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
 * Wavy (M3 Expressive) is opt-in via [amplitude] > 0 or [wavy] = true.
 * Uses [LinearWavyProgressIndicator] (1.5.0-alpha19) with amplitude/wavelength
 * tokens; falls back to flat [LinearProgressIndicator] when wavy not requested.
 * Height maps to expressive tokens (flat 2-4dp, wavy min 10dp for visibility
 * per page.md "At very small sizes, the wavy shape may not be as visible").
 * XS-XL expressive container 16-96dp can be driven via [height] directly.
 *
 * @param progress Progress fraction 0..1
 * @param modifier Modifier for width/height
 * @param trackColor Background track color (defaults suit overlays on artwork;
 * pass `colorScheme`-derived colors when placed on themed surfaces)
 * @param progressColor Filled progress color
 * @param height Bar height (default 3dp for card strips; wavy coerces to >=10dp)
 * @param drawStopIndicator override NTC gate; null = auto (enable on White alpha <3:1, disable on onSurface 0.12f)
 * @param wavy enable expressive wavy shape (alias for amplitude>0)
 * @param amplitude wave amplitude; 0 = flat fallback, >0 = wavy (default token 12dp-ish via [WavyProgressIndicatorDefaults])
 * @param wavelength wave wavelength (0 = default token)
 * @param waveSpeed wave animation speed (0 = default)
 */
// ponytail: 7-shape morph LoadingIndicator requires graphics-shapes — keep CircularProgressIndicator for 200ms-5s, add when graphics-shapes proven needed
@Composable
public fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    progressColor: Color = Color.White.copy(alpha = 0.8f),
    height: Dp = 3.dp,
    drawStopIndicator: Boolean? = null,
    wavy: Boolean = false,
    amplitude: Dp = 0.dp,
    wavelength: Dp = 0.dp,
    waveSpeed: Dp = 0.dp,
) {
    // ponytail: auto gate — White 0.2f on artwork needs dot (<3:1), onSurface 0.12f on surfaceContainer already has contrast
    val shouldDrawStop = drawStopIndicator ?: (trackColor.alpha < 0.25f)
    val useWavy = wavy || amplitude > 0.dp
    // ponytail: height coerced for wavy visibility (page.md: wavy not visible at very small sizes)
    val effectiveHeight = if (useWavy) height.coerceAtLeast(10.dp) else height

    if (useWavy) {
        // Resolve tokens: 0 => defaults (keeps call minimal, M3 tokens handle XS-XL 16-96 via height)
        val amplitudeFn =
            if (amplitude > 0.dp) {
                val ampPx = amplitude.value
                { _: Float -> ampPx }
            } else {
                WavyProgressIndicatorDefaults.indicatorAmplitude
            }
        if (shouldDrawStop) {
            when {
                wavelength > 0.dp && waveSpeed > 0.dp ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        amplitude = amplitudeFn,
                        wavelength = wavelength,
                        waveSpeed = waveSpeed,
                    )
                wavelength > 0.dp ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        amplitude = amplitudeFn,
                        wavelength = wavelength,
                    )
                waveSpeed > 0.dp ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        amplitude = amplitudeFn,
                        waveSpeed = waveSpeed,
                    )
                else ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        amplitude = amplitudeFn,
                    )
            }
        } else {
            when {
                wavelength > 0.dp && waveSpeed > 0.dp ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        stopSize = 0.dp,
                        amplitude = amplitudeFn,
                        wavelength = wavelength,
                        waveSpeed = waveSpeed,
                    )
                wavelength > 0.dp ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        stopSize = 0.dp,
                        amplitude = amplitudeFn,
                        wavelength = wavelength,
                    )
                waveSpeed > 0.dp ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        stopSize = 0.dp,
                        amplitude = amplitudeFn,
                        waveSpeed = waveSpeed,
                    )
                else ->
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = modifier.fillMaxWidth().height(effectiveHeight),
                        color = progressColor,
                        trackColor = trackColor,
                        gapSize = 0.dp,
                        stopSize = 0.dp,
                        amplitude = amplitudeFn,
                    )
            }
        }
    } else {
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
}
