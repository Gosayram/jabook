// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.audio.session

import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Callback for handling MediaSession commands.
 *
 * Handles custom commands and delegates standard commands to the player.
 */
class MediaSessionCallback(
    private val player: Player,
    private val onPlay: () -> Unit = {},
    private val onPause: () -> Unit = {},
    private val onSkipToNext: () -> Unit = {},
    private val onSkipToPrevious: () -> Unit = {},
    private val onRewind: (() -> Unit)? = null,
    private val onForward: (() -> Unit)? = null,
) : MediaSession.Callback {
    companion object {
        const val REWIND_COMMAND = "com.jabook.app.jabook.audio.REWIND"
        const val FORWARD_COMMAND = "com.jabook.app.jabook.audio.FORWARD"
    }

    // Note: onPlay, onPause, onSkipToNextMediaItem, onSkipToPreviousMediaItem, and onSeekTo
    // are not available in MediaSession.Callback in Media3 1.8.0. MediaSession automatically
    // delegates these commands to the Player. We use Player listeners to intercept state
    // changes if needed. The onPlay and onPause callbacks are kept in the constructor for
    // API compatibility but are not used in MediaSession.Callback.

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        command: SessionCommand,
        args: android.os.Bundle,
    ): ListenableFuture<SessionResult> =
        when (command.customAction) {
            REWIND_COMMAND -> {
                onRewind?.invoke()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            FORWARD_COMMAND -> {
                onForward?.invoke()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> super.onCustomCommand(session, controller, command, args)
        }
}
