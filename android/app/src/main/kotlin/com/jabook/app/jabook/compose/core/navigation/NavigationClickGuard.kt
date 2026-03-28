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

package com.jabook.app.jabook.compose.core.navigation

import android.os.SystemClock

/**
 * Guards navigation callbacks from rapid repeated taps.
 *
 * This is intended to be used together with lifecycle guards (for example, dropUnlessResumed)
 * so we both avoid stale lifecycle calls and suppress accidental duplicate taps.
 */
public class NavigationClickGuard(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var lastAcceptedAtMs: Long = UNSET

    public fun run(action: () -> Unit) {
        val nowMs = nowMsProvider()
        if (lastAcceptedAtMs != UNSET && nowMs - lastAcceptedAtMs < minIntervalMs) {
            return
        }
        lastAcceptedAtMs = nowMs
        action()
    }

    public companion object {
        public const val DEFAULT_MIN_INTERVAL_MS: Long = 350L
        private const val UNSET: Long = Long.MIN_VALUE
    }
}
