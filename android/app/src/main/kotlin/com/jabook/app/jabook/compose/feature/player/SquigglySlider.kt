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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jabook.app.jabook.compose.core.util.rememberReduceMotion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.math.sin

internal const val SQUIGGLY_SLIDER_TAG: String = "squiggly_slider_track"
internal const val SQUIGGLY_SLIDER_TOOLTIP_TAG: String = "squiggly_slider_tooltip"

// ponytail: M3 slider sizes per sliders/page.md — XS 16/44, S 24/44, M 40/52, L 56/68, XL 96/108 (track/handle, width 4dp, shape 8/8/12/16/28)
public enum class SliderSize(
    public val trackHeight: Dp,
    public val handleHeight: Dp,
    public val handleWidth: Dp,
) {
    XS(16.dp, 44.dp, 4.dp),
    S(24.dp, 44.dp, 4.dp),
    M(40.dp, 52.dp, 4.dp),
    L(56.dp, 68.dp, 4.dp),
    XL(96.dp, 108.dp, 4.dp),
}

@Stable
public fun interface ValueFormatter {
    public fun format(value: Float): String
}

/**
 * A Premium "Squiggly" Slider that shows a sine wave animation when active/playing.
 * The wave straightens out when the user interacts (drags/presses) for precision.
 *
 * The squiggle is a behavior port of AOSP SquigglyProgress (Android 13+ media squiggle):
 * asymmetric amplitude animation (800ms in with 60ms delay, CubicBezier(0.05, 0.7, 0.1, 1);
 * 550ms out, CubicBezier(0, 0, 0, 1)), phase advancing at 1 wavelength/second only while the
 * wave is visible, a 1.5λ linear taper starting at the playhead, and a dual-clip draw where
 * one full-width sine is drawn twice — active color up to the playhead, dimmed inactive color
 * past it.
 *
 * Gesture ownership: the embedded Material [Slider] is the single source of truth for
 * tap-to-jump and drag. The long-press bookmark detector below only fires after the
 * long-press timeout and consumes the remaining gesture, so it never competes with
 * normal taps or drags (and tolerates the Slider already consuming the down event).
 *
 * Smoothed playhead: the drawn position glides toward the external [value] over ~90% of
 * the observed update interval, so ~250ms position polls render as continuous motion
 * (Rhythm WaveSlider pattern). Gliding is skipped during drags and when reduce-motion is on.
 *
 * @param value Current value (0f..1f usually, but depends on valueRange)
 * @param onValueChange Callback for value change
 * @param modifier Modifier
 * @param enabled Whether slider is enabled
 * @param valueRange Range of values
 * @param isPlaying Whether media is playing (animates the wave)
 * @param squiggleAmplitude Max height of the wave
 * @param squiggleWavelength Width of one wave cycle
 * @param trackHeight Height of the track area (M3 XS = 4dp compat default; use SliderSize for XS-XL 16/24/40/56/96)
 * @param thumbRadius Radius of the thumb (XS 44dp handle per spec; use SliderSize for XL etc.)
 * @param waveformData Cached waveform window for seekbar visualization (0..1 amplitudes)
 * @param valueFormatter Optional stable formatter for the tooltip label. Prefer `remember { ValueFormatter { ... } }` to avoid unnecessary recompositions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onLongPress: ((Float) -> Unit)? = null,
    isPlaying: Boolean = false,
    squiggleAmplitude: Dp = 3.dp,
    squiggleWavelength: Dp = 20.dp,
    trackHeight: Dp = 16.dp, // M3 XS track (sliders/page.md); use SliderSize trackHeight for M3 XS-XL 16/24/40/56/96
    thumbRadius: Dp = 10.dp,
    chapterMarkersFractions: List<Float> = emptyList(),
    bookmarkMarkersFractions: List<Float> = emptyList(),
    abRepeatRange: Pair<Float, Float>? = null,
    waveformData: FloatArray = FloatArray(0),
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    abRepeatRangeColor: Color = activeTrackColor.copy(alpha = 0.35f),
    chapterMarkerColor: Color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f),
    bookmarkMarkerColor: Color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
    valueFormatter: ValueFormatter? = null,
    sliderSize: SliderSize? = null, // ponytail: when set, overrides trackHeight/handle per M3 tokens
    drawStopDot: Boolean? = null, // null = auto per NTC (<3:1 contrast)
) {
    val normalizedRange =
        remember(valueRange) {
            normalizeValueRange(valueRange)
        }
    val sanitizedChapterMarkers =
        remember(chapterMarkersFractions.toList()) {
            sanitizeChapterMarkersFractions(chapterMarkersFractions)
        }
    val sanitizedBookmarkMarkers =
        remember(bookmarkMarkersFractions.toList()) {
            sanitizeChapterMarkersFractions(bookmarkMarkersFractions)
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged
    var sliderWidthPx by remember { mutableStateOf(0) }
    var tooltipWidthDp by remember { mutableStateOf(56.dp) }
    val density = LocalDensity.current

    val reduceMotion = rememberReduceMotion()
    // ponytail: mirror custom Canvas track to match Material Slider's RTL thumb
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // ponytail: effective sizes — size token overrides explicit Dp when provided
    val effectiveTrackHeight = sliderSize?.trackHeight ?: trackHeight
    val effectiveThumbRadius = sliderSize?.let { it.handleHeight / 2 } ?: thumbRadius
    // M3 expressive vertical-line handle: 4dp x 44dp (XS); shrinks in width, grows in height on press/drag
    val handleWidth = sliderSize?.handleWidth ?: 4.dp
    val handleHeight = sliderSize?.handleHeight ?: 44.dp
    val animatedHandleWidth by animateDpAsState(
        targetValue = if (isInteracting) 3.dp else handleWidth,
        label = "handle_width",
    )
    val animatedHandleHeight by animateDpAsState(
        targetValue = if (isInteracting) handleHeight + 4.dp else handleHeight,
        label = "handle_height",
    )
    val handleColor =
        if (enabled) {
            activeTrackColor
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

    // AOSP SquigglyProgress amplitude:
    // - 0f when interacting (straight line for precision, like AOSP animate=false on touch)
    // - 0f when paused or reduceMotion
    // - 1f while playing
    // Asymmetric per AOSP: slow ease-in (800ms + 60ms delay) rising into the wave, quicker
    // settle (550ms) back to a straight track.
    val targetAmplitude =
        if (reduceMotion || isInteracting) {
            0f
        } else if (isPlaying) {
            1f
        } else {
            0f
        }
    val amplitudeInSpec =
        tween<Float>(durationMillis = 800, delayMillis = 60, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
    val amplitudeOutSpec =
        tween<Float>(durationMillis = 550, easing = CubicBezierEasing(0f, 0f, 0f, 1f))
    val animatedAmplitudeScale by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = if (targetAmplitude > 0.5f) amplitudeInSpec else amplitudeOutSpec,
        label = "amplitude",
    )

    // AOSP SquigglyProgress phase: 1 wavelength per second, advanced ONLY while the wave is
    // visible (amplitude > 0.01). When flat, the loop reads state without writing it, so no
    // frames are invalidated. reduceMotion forces amplitude to 0, which idles the loop too.
    var squigglePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastFrameNs = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (lastFrameNs > 0L && animatedAmplitudeScale > 0.01f) {
                    val deltaSec = (now - lastFrameNs) / 1_000_000_000f
                    squigglePhase = (squigglePhase + deltaSec) % 1f // phase in wavelengths
                }
                lastFrameNs = now
            }
        }
    }

    val coercedValue =
        if (value.isFinite()) {
            value.coerceIn(normalizedRange.start, normalizedRange.endInclusive)
        } else {
            normalizedRange.start
        }

    // SliderState overload instead of the value-overload: material3 1.4.0's value-overload
    // writes `state.value = value` on EVERY recomposition with no isDragging guard, which
    // stomps the in-flight drag state each time the player position ticks. Sync externally
    // only while the user is not dragging.
    val sliderState = rememberSliderState(valueRange = normalizedRange)

    // Smoothed playhead: glide toward the external value over ~90% of the observed update
    // interval so ~250ms position polls read as continuous motion (Rhythm WaveSlider pattern).
    // Runs a frame loop only per prop change (≤interval duration); skipped while dragging
    // and under reduce-motion. The SliderState sync below is driven from the rendered value.
    var renderedValue by remember { mutableFloatStateOf(coercedValue) }
    var lastTargetNs by remember { mutableLongStateOf(0L) }

    if (!sliderState.isDragging) {
        sliderState.value = renderedValue
    }
    sliderState.onValueChange = onValueChange
    sliderState.onValueChangeFinished = onValueChangeFinished
    LaunchedEffect(coercedValue, enabled) {
        val startNs = withFrameNanos { it }
        val intervalMs =
            if (lastTargetNs > 0L) {
                ((startNs - lastTargetNs) / 1_000_000L).coerceIn(16L, 1000L)
            } else {
                250L
            }
        lastTargetNs = startNs
        val from = renderedValue
        val to = coercedValue
        if (sliderState.isDragging || reduceMotion || from == to) {
            renderedValue = to
            return@LaunchedEffect
        }
        val durationMs = (intervalMs * 0.9f).toInt().coerceAtLeast(16)
        val start = withFrameNanos { it }
        while (isActive) {
            if (sliderState.isDragging) return@LaunchedEffect // hand off to the drag value
            val t = ((withFrameNanos { it } - start) / (durationMs * 1_000_000f)).coerceIn(0f, 1f)
            renderedValue = from + (to - from) * t
            if (t >= 1f) return@LaunchedEffect
        }
    }

    // Latest callback without restarting the pointerInput below (callers pass fresh
    // lambdas every recomposition; keyed restarts cancel in-progress gestures).
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier =
            modifier
                // Merge the inner Material Slider's semantics (Role.Slider, progress range,
                // setProgress) with any a11y semantics provided by callers into a single
                // TalkBack node — otherwise both are exposed as separate focusable elements.
                .semantics(mergeDescendants = true) {}
                // 48dp interactive touch target (the embedded Slider enforces its own minimum
                // interactive size); the visible track stays centered at thumbRadius*2.
                .heightIn(min = 48.dp)
                .onSizeChanged { sliderWidthPx = it.width }
                .pointerInput(enabled, normalizedRange, isRtl) {
                    awaitEachGesture {
                        val onLongPress = currentOnLongPress ?: return@awaitEachGesture
                        // The embedded Slider's tap detector consumes the down before this
                        // parent node sees it (main pass is child-first), so accept an
                        // already-consumed down — requireUnconsumed = false.
                        awaitFirstDown(requireUnconsumed = false)
                        var lastPressed: PointerInputChange? = null
                        var cancelled = false
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull()
                                    val pad = extendedTouchPadding
                                    val outOfBounds =
                                        change != null &&
                                            (
                                                change.position.x < -pad.width ||
                                                    change.position.x > size.width + pad.width ||
                                                    change.position.y < -pad.height ||
                                                    change.position.y > size.height + pad.height
                                            )
                                    if (change == null || change.isConsumed || !change.pressed || outOfBounds) {
                                        // Drag took over, finger lifted, or drifted away.
                                        cancelled = true
                                        break
                                    }
                                    lastPressed = change
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            // Held still past the timeout — long press detected.
                        }
                        val pressed = lastPressed
                        if (cancelled || pressed == null) return@awaitEachGesture
                        // Consume the remainder on the initial pass so descendants (the
                        // embedded Slider) see consumed events and cancel their tap-to-jump.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { !it.pressed }) break
                        }
                        val rawFraction = (pressed.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val fraction = if (isRtl) 1f - rawFraction else rawFraction
                        onLongPress(
                            normalizedRange.start +
                                fraction * (normalizedRange.endInclusive - normalizedRange.start),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Custom Track Drawing
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // ponytail: at least track + 8dp so waveform stays visible around the thicker M3 track
                    .height(maxOf(effectiveThumbRadius * 2, effectiveTrackHeight + 8.dp)),
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2

            // Calculate progress ratio (0..1) with protection against division by zero.
            // Drawn from the smoothed renderedValue so the playhead glides between polls.
            val range = normalizedRange.endInclusive - normalizedRange.start
            val fraction =
                if (range > 0 && range.isFinite()) {
                    ((renderedValue - normalizedRange.start) / range).coerceIn(0f, 1f)
                } else {
                    0f
                }

            // Ensure activeWidth is valid and finite
            val activeWidth = (width * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f).coerceAtMost(width)

            // Draw cached waveform behind the track for quick visual density preview.
            if (waveformData.isNotEmpty()) {
                val baseline = centerY
                // ponytail: track half-height + 4dp — waveform pokes past the 16dp track instead of hiding under it
                val availableHalfHeight = (effectiveTrackHeight.toPx() / 2f + 4.dp.toPx()).coerceAtLeast(2f)
                val stepX = width / waveformData.size.toFloat()
                var x = 0f
                for (sample in waveformData) {
                    val amplitude = sample.coerceIn(0f, 1f)
                    val yOffset = amplitude * availableHalfHeight
                    drawLine(
                        color = inactiveTrackColor.copy(alpha = 0.22f),
                        start = Offset(x, baseline - yOffset),
                        end = Offset(x, baseline + yOffset),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    x += stepX
                }
            }

            // Squiggle + track — AOSP SquigglyProgress draw: one full-width sine drawn twice
            // with clips (active color up to the playhead, dimmed inactive color past it), so
            // the wave continues PAST the playhead with a 1.5λ linear taper. When flat, fall
            // back to the plain two-segment track.
            val amplitudePx = squiggleAmplitude.toPx() * animatedAmplitudeScale
            val waveVisible = amplitudePx >= 1f && activeWidth > 0f
            if (waveVisible) {
                val wavelengthPx = squiggleWavelength.toPx()
                val strokePx = effectiveTrackHeight.toPx()
                val taperLength = wavelengthPx * 1.5f // ponytail: 1.5λ post-playhead fade (AOSP)
                val path = Path()
                path.moveTo(if (isRtl) width else 0f, centerY)
                val step = wavelengthPx / 8f
                var x = 0f
                while (x <= width) {
                    // Full amplitude up to the playhead, then linear fade to zero.
                    val coeff =
                        if (x <= activeWidth) {
                            1f
                        } else {
                            ((activeWidth + taperLength - x) / taperLength).coerceIn(0f, 1f)
                        }
                    val yOffset =
                        amplitudePx * coeff * sin(2 * Math.PI * (x / wavelengthPx - squigglePhase)).toFloat()
                    val drawX = if (isRtl) width - x else x
                    path.lineTo(drawX, centerY + yOffset)
                    x += step
                }
                val clipTop = amplitudePx + strokePx
                val playheadX = if (isRtl) width - activeWidth else activeWidth
                clipRect(
                    left = 0f,
                    top = centerY - clipTop,
                    right = playheadX,
                    bottom = centerY + clipTop,
                ) {
                    drawPath(path, activeTrackColor, style = Stroke(strokePx, cap = StrokeCap.Round))
                }
                clipRect(
                    left = playheadX,
                    top = centerY - clipTop,
                    right = size.width,
                    bottom = centerY + clipTop,
                ) {
                    drawPath(
                        path,
                        inactiveTrackColor.copy(alpha = 0.30f), // AOSP DISABLED_ALPHA 77
                        style = Stroke(strokePx, cap = StrokeCap.Round),
                    )
                }
            } else {
                // Draw Inactive Track — mirrored for RTL to match Material Slider thumb
                if (isRtl) {
                    drawLine(
                        color = inactiveTrackColor,
                        start = Offset(0f, centerY),
                        end = Offset(width - activeWidth, centerY),
                        strokeWidth = effectiveTrackHeight.toPx(),
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawLine(
                        color = inactiveTrackColor,
                        start = Offset(activeWidth, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = effectiveTrackHeight.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Flat played side while the wave is straightened (or animating in/out).
                if (activeWidth > 0f) {
                    if (isRtl) {
                        drawLine(
                            color = activeTrackColor,
                            start = Offset(width - activeWidth, centerY),
                            end = Offset(width, centerY),
                            strokeWidth = effectiveTrackHeight.toPx(),
                            cap = StrokeCap.Round,
                        )
                    } else {
                        drawLine(
                            color = activeTrackColor,
                            start = Offset(0f, centerY),
                            end = Offset(activeWidth, centerY),
                            strokeWidth = effectiveTrackHeight.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            // ponytail: stop dot 4dp diameter at inactive end when track contrast <3:1 (sliders/page.md, progress-indicators/page.md)
            val shouldDrawStopDot = drawStopDot ?: (inactiveTrackColor.alpha < 0.4f)
            if (shouldDrawStopDot) {
                val dotRadius = 2.dp.toPx()
                val stopX = if (isRtl) 0f else width
                drawCircle(
                    color = activeTrackColor.copy(alpha = 0.9f),
                    radius = dotRadius,
                    center = Offset(stopX, centerY),
                )
            }

            // Draw AB repeat range overlay (highlighted segment between A and B)
            if (abRepeatRange != null) {
                val aF = abRepeatRange.first.coerceIn(0f, 1f)
                val bF = abRepeatRange.second.coerceIn(0f, 1f)
                val aX = if (isRtl) width * (1f - bF) else width * aF
                val bX = if (isRtl) width * (1f - aF) else width * bF
                if (aX >= 0f && bX > aX) {
                    drawLine(
                        color = abRepeatRangeColor,
                        start = Offset(aX, centerY),
                        end = Offset(bX, centerY),
                        strokeWidth = effectiveTrackHeight.toPx() * 2.5f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Draw chapter markers over the track.
            val markerHalfHeight = (effectiveTrackHeight.toPx() * 1.5f).coerceAtLeast(3f)
            sanitizedChapterMarkers.forEach { markerFraction ->
                val markerX = if (isRtl) width * (1f - markerFraction) else width * markerFraction
                drawLine(
                    color = chapterMarkerColor,
                    start = Offset(markerX, centerY - markerHalfHeight),
                    end = Offset(markerX, centerY + markerHalfHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Draw bookmark markers over the track (taller, tertiary color).
            val bookmarkHalfHeight = (effectiveTrackHeight.toPx() * 2.5f).coerceAtLeast(5f)
            sanitizedBookmarkMarkers.forEach { markerFraction ->
                val markerX = if (isRtl) width * (1f - markerFraction) else width * markerFraction
                drawLine(
                    color = bookmarkMarkerColor,
                    start = Offset(markerX, centerY - bookmarkHalfHeight),
                    end = Offset(markerX, centerY + bookmarkHalfHeight),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Material Slider handles ALL pointer interactions (tap-to-jump, drag, a11y);
        // its own track/thumb are transparent so the Canvas below is the only visual.
        Slider(
            state = sliderState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SQUIGGLY_SLIDER_TAG),
            enabled = enabled,
            interactionSource = interactionSource,
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.Transparent, // Round thumb replaced by M3 vertical-line handle below
                    activeTrackColor = Color.Transparent, // Hidden standard track
                    inactiveTrackColor = Color.Transparent, // Hidden standard track
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                ),
            thumb = {
                // M3 expressive handle: vertical line 4x44dp, shrinks/grows while pressed or dragging
                Box(
                    modifier =
                        Modifier
                            .size(width = animatedHandleWidth, height = animatedHandleHeight)
                            .background(
                                color = handleColor,
                                shape = RoundedCornerShape(animatedHandleWidth / 2),
                            ),
                )
            },
        )

        if (valueFormatter != null && isInteracting && sliderWidthPx > 0) {
            val range = (normalizedRange.endInclusive - normalizedRange.start).takeIf { it > 0f && it.isFinite() } ?: 1f
            val fraction = ((coercedValue - normalizedRange.start) / range).coerceIn(0f, 1f)
            // ponytail: M3 centers the slot thumb at handleWidth/2 inset, so tooltip follows that
            val thumbRadiusPx = with(density) { (handleWidth / 2).toPx() }
            val xOffset = (thumbRadiusPx + fraction * (sliderWidthPx - 2 * thumbRadiusPx)).toInt()
            val xOffsetDp = with(density) { xOffset.toDp() }
            val sliderWidthDp = with(density) { sliderWidthPx.toDp() }
            val clampedOffset =
                clampSliderTooltipOffset(
                    xOffsetDp = xOffsetDp,
                    sliderWidthDp = sliderWidthDp,
                    tooltipWidthDp = tooltipWidthDp,
                )
            // ponytail: Popup offset is relative to this Box (was double-counting window coords)
            val popupOffset =
                IntOffset(
                    x = with(density) { clampedOffset.roundToPx() },
                    y = with(density) { (-30).dp.roundToPx() },
                )

            Popup(
                alignment = Alignment.TopStart,
                offset = popupOffset,
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                Text(
                    text = valueFormatter.format(coercedValue),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier =
                        Modifier
                            .testTag(SQUIGGLY_SLIDER_TOOLTIP_TAG)
                            .background(
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = RoundedCornerShape(6.dp),
                            ).padding(horizontal = 8.dp, vertical = 4.dp)
                            .onSizeChanged { tooltipWidthDp = with(density) { it.width.toDp() } },
                )
            }
        }
    }
}

internal fun sanitizeChapterMarkersFractions(markers: List<Float>): List<Float> =
    markers
        .asSequence()
        .filter { marker -> marker.isFinite() && marker > 0f && marker < 1f }
        .distinct()
        .sorted()
        .toList()

internal fun normalizeValueRange(valueRange: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
    val start =
        if (valueRange.start.isFinite()) {
            valueRange.start
        } else {
            0f
        }
    val end =
        if (valueRange.endInclusive.isFinite()) {
            valueRange.endInclusive
        } else {
            1f
        }
    return if (start < end) {
        start..end
    } else {
        0f..1f
    }
}

internal fun clampSliderTooltipOffset(
    xOffsetDp: Dp,
    sliderWidthDp: Dp,
    tooltipWidthDp: Dp = 56.dp,
): Dp {
    val rawOffset = xOffsetDp - tooltipWidthDp / 2
    val maxOffset = (sliderWidthDp - tooltipWidthDp).coerceAtLeast(0.dp)
    return rawOffset.coerceIn(0.dp, maxOffset)
}
