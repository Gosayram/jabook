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

package com.jabook.app.jabook.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debouncer for preventing double clicks and rapid button presses.
 *
 * Inspired by Easybook implementation for better UX.
 * Prevents accidental double-taps and rapid-fire button presses.
 */
class ClickDebouncer(
    private val scope: CoroutineScope,
    private val debounceTimeMs: Long = 300L, // Default: 300ms debounce
) {
    private var lastClickJob: Job? = null

    /**
     * Executes the action with debouncing.
     * If called multiple times within debounceTimeMs, only the last call will execute.
     *
     * @param action Action to execute
     */
    fun debounce(action: () -> Unit) {
        lastClickJob?.cancel()
        lastClickJob =
            scope.launch {
                delay(debounceTimeMs)
                action()
            }
    }

    /**
     * Executes the action immediately if not debounced, otherwise cancels previous and schedules new.
     *
     * @param action Action to execute
     */
    fun debounceImmediate(action: () -> Unit) {
        lastClickJob?.cancel()
        action()
        lastClickJob =
            scope.launch {
                delay(debounceTimeMs)
            }
    }
}

/**
 * Remembers a ClickDebouncer instance.
 *
 * @param debounceTimeMs Debounce time in milliseconds (default: 300ms)
 * @return ClickDebouncer instance
 */
@Composable
fun rememberClickDebouncer(debounceTimeMs: Long = 300L): ClickDebouncer {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(scope, debounceTimeMs) {
        ClickDebouncer(scope, debounceTimeMs)
    }
}
