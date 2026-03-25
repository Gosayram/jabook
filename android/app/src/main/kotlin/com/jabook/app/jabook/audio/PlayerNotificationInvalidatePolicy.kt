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
 * Coalesces notification invalidation bursts while keeping playback/metadata updates responsive.
 */
internal class PlayerNotificationInvalidatePolicy(
    private val debounceDelayMs: Long = DEFAULT_DEBOUNCE_DELAY_MS,
) {
    private var hasPendingDebouncedInvalidate: Boolean = false

    internal fun onDebouncedSignal(): DebouncedAction {
        if (hasPendingDebouncedInvalidate) {
            return DebouncedAction.COALESCED
        }
        hasPendingDebouncedInvalidate = true
        return DebouncedAction.SCHEDULE
    }

    internal fun onImmediateSignal(): ImmediateAction {
        val shouldCancelPendingDebounced = hasPendingDebouncedInvalidate
        hasPendingDebouncedInvalidate = false
        return ImmediateAction(cancelPendingDebounced = shouldCancelPendingDebounced)
    }

    internal fun onDebouncedInvalidateDelivered() {
        hasPendingDebouncedInvalidate = false
    }

    internal fun onDebouncedInvalidateCancelled() {
        hasPendingDebouncedInvalidate = false
    }

    internal fun debounceDelayMs(): Long = debounceDelayMs

    internal enum class DebouncedAction {
        SCHEDULE,
        COALESCED,
    }

    internal data class ImmediateAction(
        val cancelPendingDebounced: Boolean,
    )

    internal companion object {
        internal const val DEFAULT_DEBOUNCE_DELAY_MS: Long = 150L
    }
}
