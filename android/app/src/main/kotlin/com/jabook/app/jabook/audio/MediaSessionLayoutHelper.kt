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

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages MediaSession custom layout updates with debouncing.
 *
 * Extracted from AudioPlayerService to reduce its size and isolate
 * the responsibility of building and updating the MediaSession custom layout
 * (rewind/forward command buttons).
 *
 * Follows the Rhythm pattern for debounced layout updates to prevent flickering.
 *
 * @param scope Coroutine scope for async operations
 * @param getSession Function to get the current MediaSession (nullable)
 */
@OptIn(UnstableApi::class)
internal class MediaSessionLayoutHelper(
    private val scope: CoroutineScope,
    private val getSession: () -> MediaSession?,
) {
    private var lastRewindSeconds: Int? = null
    private var lastForwardSeconds: Int? = null
    private var updateLayoutJob: Job? = null

    /**
     * Updates MediaSession custom layout if rewind/forward durations changed.
     * Uses smart comparison to skip no-op updates.
     */
    fun updateSmart(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        val session = getSession() ?: return
        try {
            if (rewindSeconds == lastRewindSeconds && forwardSeconds == lastForwardSeconds) {
                LogUtils.d(TAG, "Custom layout state unchanged, skipping update")
                return
            }
            lastRewindSeconds = rewindSeconds
            lastForwardSeconds = forwardSeconds
            applyLayout(session, rewindSeconds, forwardSeconds)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error in smart custom layout update", e)
        }
    }

    /**
     * Schedules a debounced layout update (150ms delay by default).
     * Cancels any previously pending update.
     */
    fun scheduleUpdate(
        rewindSeconds: Int,
        forwardSeconds: Int,
        delayMs: Int = 150,
    ) {
        updateLayoutJob?.cancel()
        updateLayoutJob =
            scope.launch {
                delay(delayMs.toLong())
                updateSmart(rewindSeconds, forwardSeconds)
            }
    }

    /**
     * Forces an immediate layout update without debouncing.
     * Used for initial setup.
     */
    fun forceUpdate(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        scope.launch {
            updateSmart(rewindSeconds, forwardSeconds)
        }
    }

    /**
     * Sets the initial CustomLayout for MediaSession.
     * Called after MediaController initialization.
     */
    fun setInitialLayout() {
        val session = getSession() ?: return
        try {
            val defaultRewind = 10
            val defaultForward = 30
            lastRewindSeconds = defaultRewind
            lastForwardSeconds = defaultForward
            applyLayout(session, defaultRewind, defaultForward)
            LogUtils.d(TAG, "Initial CustomLayout set - Rewind: ${defaultRewind}s, Forward: ${defaultForward}s")
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error setting initial CustomLayout", e)
        }
    }

    /** Cancels any pending debounced update. Call during cleanup. */
    fun release() {
        updateLayoutJob?.cancel()
        updateLayoutJob = null
    }

    private fun applyLayout(
        session: MediaSession,
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        val rewindButton =
            CommandButton
                .Builder(CommandButton.ICON_SKIP_BACK)
                .setDisplayName("-$rewindSeconds")
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_REWIND,
                        android.os.Bundle.EMPTY,
                    ),
                ).build()

        val forwardButton =
            CommandButton
                .Builder(CommandButton.ICON_SKIP_FORWARD)
                .setDisplayName("+$forwardSeconds")
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_FORWARD,
                        android.os.Bundle.EMPTY,
                    ),
                ).build()

        session.setCustomLayout(listOf(rewindButton, forwardButton))
        LogUtils.d(TAG, "Updated custom layout - Rewind: ${rewindSeconds}s, Forward: ${forwardSeconds}s")
    }

    private companion object {
        private const val TAG = "MediaSessionLayout"
    }
}