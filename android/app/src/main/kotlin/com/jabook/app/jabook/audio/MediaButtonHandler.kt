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

package com.jabook.app.jabook.audio

import android.os.SystemClock
import android.view.KeyEvent
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles media button clicks, specifically detecting single, double, and triple clicks
 * on the headset hook or play/pause button, plus long press detection.
 *
 * Logic:
 * - 1 click: Play/Pause toggle
 * - 2 clicks: Skip to Next
 * - 3 clicks: Skip to Previous (or Rewind, configurable)
 * - Long press (500ms hold): Forward 30s (or configurable)
 */
@Singleton
public class MediaButtonHandler
    @Inject
    constructor() {
        public companion object {
            private const val TAB_TIMEOUT_MS = 400L
            private const val LONG_PRESS_MS = 500L
        }

        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("MediaButtonHandler"),
            )
        private var clickCount = 0
        private var clickJob: Job? = null
        private var longPressJob: Job? = null
        private var longPressFired = false
        private var downTime: Long = 0L

        /**
         * Handles a media button event (both DOWN and UP).
         *
         * @param keyCode The key code of the button pressed.
         * @param action KeyEvent.ACTION_DOWN or KeyEvent.ACTION_UP.
         * @param onSingleClick Action for single click.
         * @param onDoubleClick Action for double click.
         * @param onTripleClick Action for triple click.
         * @param onLongPress Action for long press.
         * @return true if the event was handled, false otherwise.
         */
        public fun onMediaButtonEvent(
            keyCode: Int,
            action: Int = KeyEvent.ACTION_DOWN,
            onSingleClick: () -> Unit,
            onDoubleClick: () -> Unit,
            onTripleClick: () -> Unit,
            onLongPress: () -> Unit,
        ): Boolean {
            if (!isRelevantKey(keyCode)) return false

            if (action == KeyEvent.ACTION_DOWN) {
                clickCount++
                downTime = SystemClock.uptimeMillis()

                // Cancel existing click timer and long press
                clickJob?.cancel()
                longPressJob?.cancel()
                longPressFired = false

                // Start long press timer (only fires if clickCount is exactly 1)
                if (clickCount == 1) {
                    longPressJob =
                        scope.launch {
                            delay(LONG_PRESS_MS)
                            longPressFired = true
                            onLongPress()
                            clickCount = 0
                            clickJob = null
                        }
                }

                // Start click timeout timer
                clickJob =
                    scope.launch {
                        delay(TAB_TIMEOUT_MS)

                        // Cancel long press timer first
                        longPressJob?.cancel()
                        longPressJob = null

                        if (longPressFired) {
                            clickCount = 0
                            clickJob = null
                            return@launch
                        }

                        when (clickCount) {
                            1 -> onSingleClick()
                            2 -> onDoubleClick()
                            else -> onTripleClick()
                        }

                        clickCount = 0
                        clickJob = null
                    }
            } else {
                // ACTION_UP: cancel long press timer if hold was short enough
                longPressJob?.cancel()
                longPressJob = null

                val holdDuration = SystemClock.uptimeMillis() - downTime

                // If long press already fired, reset and ignore UP
                if (longPressFired) {
                    return true
                }

                // If hold was short (< long press threshold), let the multi-click timer handle it
                if (holdDuration < LONG_PRESS_MS) {
                    // multi-click job is already running from ACTION_DOWN
                    return true
                }

                // Hold duration >= LONG_PRESS_MS but timer hasn't fired yet (edge case)
                // This can happen if the timer is delayed on main thread
                longPressFired = true
                clickJob?.cancel()
                clickJob = null
                clickCount = 0
                onLongPress()
            }

            return true
        }

        private fun isRelevantKey(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
    }
