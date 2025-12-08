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

    override fun onPlay(session: MediaSession): ListenableFuture<SessionResult> {
        onPlay()
        return super.onPlay(session)
    }

    override fun onPause(session: MediaSession): ListenableFuture<SessionResult> {
        onPause()
        return super.onPause(session)
    }

    override fun onSkipToNextMediaItem(session: MediaSession): ListenableFuture<SessionResult> {
        onSkipToNext()
        return super.onSkipToNextMediaItem(session)
    }

    override fun onSkipToPreviousMediaItem(session: MediaSession): ListenableFuture<SessionResult> {
        onSkipToPrevious()
        return super.onSkipToPreviousMediaItem(session)
    }

    override fun onSeekTo(
        session: MediaSession,
        positionMs: Long,
    ): ListenableFuture<SessionResult> {
        player.seekTo(positionMs)
        return super.onSeekTo(session, positionMs)
    }

    override fun onCustomCommand(
        session: MediaSession,
        command: SessionCommand,
        args: android.os.Bundle?,
    ): ListenableFuture<SessionResult> =
        when (command.customAction) {
            REWIND_COMMAND -> {
                onRewind?.invoke()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            FORWARD_COMMAND -> {
                onForward?.invoke()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            else -> super.onCustomCommand(session, command, args)
        }
}
