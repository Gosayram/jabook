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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates debounced/immediate notification invalidation in service-layer paths.
 */
internal class PlayerNotificationInvalidationCoordinator(
    private val scope: CoroutineScope,
    private val policy: PlayerNotificationInvalidatePolicy = PlayerNotificationInvalidatePolicy(),
    private val log: (String) -> Unit = {},
    private val invalidate: () -> Unit,
) {
    private var notificationUpdateJob: Job? = null

    internal fun onDebouncedSignal(event: String) {
        when (policy.onDebouncedSignal()) {
            PlayerNotificationInvalidatePolicy.DebouncedAction.COALESCED -> {
                log("$event: debounce coalesced")
            }
            PlayerNotificationInvalidatePolicy.DebouncedAction.SCHEDULE -> {
                val newJob =
                    scope.launch {
                        delay(policy.debounceDelayMs())
                        log("$event: invalidating notification (debounced)")
                        invalidate()
                        policy.onDebouncedInvalidateDelivered()
                        notificationUpdateJob = null
                    }
                newJob.invokeOnCompletion { throwable ->
                    if (throwable is CancellationException) {
                        policy.onDebouncedInvalidateCancelled()
                    }
                }
                notificationUpdateJob = newJob
            }
        }
    }

    internal fun onImmediateSignal(event: String) {
        val action = policy.onImmediateSignal()
        if (action.cancelPendingDebounced) {
            cancelPending(event)
        }
        log("$event: invalidating immediately")
        invalidate()
    }

    private fun cancelPending(reason: String) {
        val hadPendingJob = notificationUpdateJob?.isActive == true
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        if (hadPendingJob) {
            log("$reason: canceled pending debounced invalidate")
        }
    }
}
