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

package com.jabook.app.jabook.compose.feature.player.controller

/** Bounded retry policy for connecting the UI controller to the media session. */
internal object MediaControllerRetryPolicy {
    const val MAX_RETRIES: Int = 3

    private const val INITIAL_DELAY_MS: Long = 250L
    private const val MAX_DELAY_MS: Long = 1_000L

    /** Returns the delay before retry number [retryCount], where the first retry is one. */
    fun delayMs(retryCount: Int): Long =
        (INITIAL_DELAY_MS shl (retryCount - 1).coerceAtLeast(0)).coerceAtMost(MAX_DELAY_MS)
}
