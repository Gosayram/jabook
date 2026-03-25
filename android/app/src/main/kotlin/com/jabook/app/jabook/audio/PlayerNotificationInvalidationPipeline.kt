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

import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope

/**
 * Service-level integration of callback routing and notification invalidation orchestration.
 */
internal class PlayerNotificationInvalidationPipeline(
    scope: CoroutineScope,
    policy: PlayerNotificationInvalidatePolicy = PlayerNotificationInvalidatePolicy(),
    private val logTag: String = LOG_TAG,
    private val logDebug: (String, String) -> Unit = { tag, message -> LogUtils.d(tag, message) },
    private val invalidate: () -> Unit,
) {
    private val coordinator =
        PlayerNotificationInvalidationCoordinator(
            scope = scope,
            policy = policy,
            log = { message ->
                logDebug(logTag, message)
            },
            invalidate = invalidate,
        )

    private val listener = PlayerNotificationInvalidationListener(signals = coordinator)

    internal fun register(player: Player) {
        player.addListener(listener)
    }

    internal fun forceInitialStateInvalidate() {
        coordinator.onImmediateSignal(event = EVENT_FORCE_INITIAL_STATE)
    }

    internal companion object {
        const val LOG_TAG: String = "PlayerNotification"
        const val EVENT_FORCE_INITIAL_STATE: String = "force_initial_state"
    }
}
