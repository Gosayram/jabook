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

import android.content.Context
import android.content.Intent
import com.jabook.app.jabook.util.LogUtils

/**
 * Coordinates timeout expiration side effects: logging, broadcasting and unload callback.
 */
internal class InactivityUnloadOrchestrator(
    private val context: Context,
    private val onTimerExpired: () -> Unit,
) {
    fun handleTimeout(inactivityTimeoutSeconds: Long) {
        LogUtils.i(
            "InactivityTimer",
            "Inactivity timer expired after ${inactivityTimeoutSeconds}s (${inactivityTimeoutSeconds / 60} minutes), unloading player",
        )
        LogUtils.d("InactivityTimer", "Releasing resources: MediaSession, ExoPlayer, Notification")
        broadcastTimerExpired()
        onTimerExpired()
    }

    private fun broadcastTimerExpired() {
        val intent = Intent(InactivityTimer.ACTION_INACTIVITY_TIMER_EXPIRED)
        context.sendBroadcast(intent)
        LogUtils.d("InactivityTimer", "Broadcasted inactivity timer expiration")
    }
}
