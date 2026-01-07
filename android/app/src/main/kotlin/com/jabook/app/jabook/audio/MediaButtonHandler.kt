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

import android.view.KeyEvent
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
 * on the headset hook or play/pause button.
 *
 * Logic:
 * - 1 click: Play/Pause toggle
 * - 2 clicks: Skip to Next
 * - 3 clicks: Skip to Previous (or Rewind, configurable)
 */
@Singleton
class MediaButtonHandler
    @Inject
    constructor() {
        companion object {
            private const val TAB_TIMEOUT_MS = 400L
            private const val TAG = "MediaButtonHandler"
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var clickCount = 0
        private var clickJob: Job? = null

        /**
         * Handles a media button click.
         *
         * @param keyCode The key code of the button pressed (e.g. KEYCODE_HEADSETHOOK, KEYCODE_MEDIA_PLAY_PAUSE)
         * @param onSingleClick Action for single click (Play/Pause)
         * @param onDoubleClick Action for double click (Next)
         * @param onTripleClick Action for triple click (Previous)
         * @return true if the event was handled (always true for relevant keys), false otherwise.
         */
        fun onMediaButtonEvent(
            keyCode: Int,
            onSingleClick: () -> Unit,
            onDoubleClick: () -> Unit,
            onTripleClick: () -> Unit,
        ): Boolean {
            if (!isRelevantKey(keyCode)) return false

            clickCount++

            // Cancel existing job to reset timer window
            clickJob?.cancel()

            clickJob =
                scope.launch {
                    delay(TAB_TIMEOUT_MS)

                    // Timeout reached, execute action based on accumulated clicks
                    when (clickCount) {
                        1 -> onSingleClick()
                        2 -> onDoubleClick()
                        else -> onTripleClick() // 3 or more
                    }

                    // Reset state
                    clickCount = 0
                    clickJob = null
                }

            return true
        }

        private fun isRelevantKey(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
    }
