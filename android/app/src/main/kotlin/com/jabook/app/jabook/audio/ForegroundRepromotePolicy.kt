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

/**
 * Guardrail for suppressing redundant foreground re-promote bursts for the same notification id.
 */
internal class ForegroundRepromotePolicy(
    private val nowMsProvider: () -> Long = { System.nanoTime() / 1_000_000L },
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {
    private val lastSuccessAtMsByNotificationId = mutableMapOf<Int, Long>()

    internal fun shouldAttempt(notificationId: Int): Boolean {
        val lastSuccessAtMs = lastSuccessAtMsByNotificationId[notificationId] ?: return true
        val deltaMs = nowMsProvider() - lastSuccessAtMs
        return deltaMs < 0 || deltaMs >= minIntervalMs
    }

    internal fun onPromotionSucceeded(notificationId: Int) {
        lastSuccessAtMsByNotificationId[notificationId] = nowMsProvider()
    }

    internal companion object {
        internal const val DEFAULT_MIN_INTERVAL_MS: Long = 500L
    }
}
