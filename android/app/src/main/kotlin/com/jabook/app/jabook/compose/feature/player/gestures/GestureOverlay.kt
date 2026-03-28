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

package com.jabook.app.jabook.compose.feature.player.gestures

import android.app.Activity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * Overlay that intercepts touches for player gestures.
 *
 * @param onSeek Callback when seek gesture finishes (delta in ms)
 * @param modifier Modifier
 * @param enabled Whether gestures are enabled
 * @param content The content to overlay gestures on (usually PlayerScreen content)
 */
@Composable
public fun GestureOverlay(
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Handler and state
    // We remember handler to keep context reference, though in Compose context might leak if not careful.
    // Here we just use application context from LocalContext which is safe usually, but Activity is needed for window.
    val gestureHandler = remember(context) { SwipeGestureHandler(context) }

    var gestureState by remember { mutableStateOf(GestureState()) }

    // Initial values at start of gesture
    var initialBrightness by remember { mutableStateOf(0f) }
    var initialVolume by remember { mutableStateOf(0) }
    var initialTouchX by remember { mutableStateOf(0f) }
    var initialTouchY by remember { mutableStateOf(0f) }

    // Thresholds
    val touchSlop =
        SwipeGestureHandler.MIN_DRAG_THRESHOLD_DP *
            context.resources.displayMetrics.density

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()

                        initialTouchX = down.position.x
                        initialTouchY = down.position.y

                        // Capture initial values
                        activity?.window?.let { window ->
                            initialBrightness = gestureHandler.getCurrentBrightness(window)
                        }
                        initialVolume = gestureHandler.currentVolume

                        var activeGesture = GestureType.NONE
                        var accumulatedDragX = 0f
                        var accumulatedDragY = 0f

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed && change.previousPressed) {
                                // Gesture ended
                                if (activeGesture == GestureType.SEEK) {
                                    // Commit seek
                                    onSeek(gestureState.value.toLong() * 1000)
                                }
                                break
                            }

                            // Calculate drag
                            val dragX = change.position.x - change.previousPosition.x
                            val dragY = change.position.y - change.previousPosition.y

                            accumulatedDragX += dragX
                            accumulatedDragY += dragY

                            // Determine gesture type if not yet active
                            if (activeGesture == GestureType.NONE) {
                                if (abs(accumulatedDragX) > touchSlop) {
                                    activeGesture = GestureType.SEEK
                                } else if (abs(accumulatedDragY) > touchSlop) {
                                    // Vertical swipe - check zone
                                    val isLeftZone = initialTouchX < width * SwipeGestureHandler.SIDE_ZONE_RATIO
                                    val isRightZone = initialTouchX > width * (1 - SwipeGestureHandler.SIDE_ZONE_RATIO)

                                    if (isLeftZone) {
                                        activeGesture = GestureType.BRIGHTNESS
                                    } else if (isRightZone) {
                                        activeGesture = GestureType.VOLUME
                                    }
                                }
                            }

                            // Update active gesture
                            if (activeGesture != GestureType.NONE) {
                                gestureState = gestureState.copy(isActive = true, type = activeGesture)

                                when (activeGesture) {
                                    GestureType.BRIGHTNESS -> {
                                        // Normalize accumulated Y drag relative to screen height
                                        // We use total accumulated drag from start of gesture
                                        val totalDragY = change.position.y - initialTouchY
                                        val screenHeight = size.height.toFloat()
                                        // Normalize: full screen height drag = full range change
                                        val delta = totalDragY / screenHeight

                                        activity?.window?.let { window ->
                                            val newBrightness = gestureHandler.adjustBrightness(delta, window, initialBrightness)
                                            gestureState = gestureState.copy(value = newBrightness)
                                        }
                                    }
                                    GestureType.VOLUME -> {
                                        val totalDragY = change.position.y - initialTouchY
                                        val screenHeight = size.height.toFloat()
                                        val delta = totalDragY / screenHeight

                                        val newVolume = gestureHandler.adjustVolume(delta)
                                        // Set value as normalized volume (0.0 - 1.0)
                                        gestureState =
                                            gestureState.copy(
                                                value = newVolume.toFloat() / gestureHandler.maxVolume,
                                            )
                                    }
                                    GestureType.SEEK -> {
                                        val totalDragX = change.position.x - initialTouchX
                                        val seekMs = gestureHandler.calculateSeekDelta(totalDragX, width)
                                        // Value stored in seconds for display
                                        gestureState = gestureState.copy(value = seekMs / 1000f)
                                    }
                                }

                                // Consume event so underlying views don't scroll
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        // Reset on up/cancel
                        gestureState = GestureState() // Reset to NONE/inactive
                    }
                },
    ) {
        content()

        // Overlay indicator on top
        GestureIndicator(gestureState = gestureState)
    }
}
