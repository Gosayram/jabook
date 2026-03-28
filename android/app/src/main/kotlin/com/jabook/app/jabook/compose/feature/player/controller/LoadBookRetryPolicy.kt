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

internal object LoadBookRetryPolicy {
    private const val MAX_RETRIES: Int = 3
    private const val BASE_RETRY_DELAY_MS: Long = 1_200L

    fun shouldRetry(nextAttempt: Int): Boolean = nextAttempt in 1..MAX_RETRIES

    fun retryDelayMs(nextAttempt: Int): Long = BASE_RETRY_DELAY_MS * nextAttempt.coerceAtLeast(1)
}
