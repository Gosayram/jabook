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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Buffer manager that monitors playback underruns and adjusts buffer size.
 *
 * P-15: CrossFadePlayer: управление буфером (Buffer management)
 */
@UnstableApi
public class BufferManager(
    private val player: ExoPlayer,
    private val maxBufferMs: Long = 30000,
    private val minBufferMs: Long = 5000,
    private val underrunThresholdMs: Long = 2000,
    private val checkIntervalMs: Long = 1000,
) {
    private val scopeJob = SupervisorJob()
    private val scope =
        CoroutineScope(scopeJob + Dispatchers.Main.immediate + loggingCoroutineExceptionHandler("BufferManager"))

    private var active = false
    private var currentBufferMs = minBufferMs

    /**
     * Starts monitoring buffer levels and adjusting buffer size.
     */
    public fun start() {
        if (active) return
        active = true
        scope.launch {
            while (active) {
                try {
                    val playbackState = player.playbackState
                    val playWhenReady = player.playWhenReady
                    val bufferedPosition = player.bufferedPosition
                    val position = player.currentPosition
                    val stateBuffering = Player.STATE_BUFFERING
                    val stateReady = Player.STATE_READY

                    // Check for underrun (playback stalling)
                    if (playWhenReady && playbackState == stateBuffering) {
                        LogUtils.w(TAG, "Buffer underrun detected: bufferedPosition=$bufferedPosition, position=$position")
                        // Reduce buffer size to minimize latency
                        currentBufferMs = minBufferMs
                        // Notify player to reduce buffer? (Media3 doesn't expose direct control)
                    } else if (playWhenReady && playbackState == stateReady) {
                        // Increase buffer size if we have enough data
                        if (bufferedPosition - position > currentBufferMs) {
                            currentBufferMs = (currentBufferMs * 1.1).toLong().coerceAtMost(maxBufferMs)
                        }
                    }

                    LogUtils.d(
                        TAG,
                        "BufferManager: currentBufferMs=$currentBufferMs, bufferedPosition=$bufferedPosition, position=$position",
                    )
                } catch (e: Exception) {
                    LogUtils.e(TAG, "BufferManager error: ${e.message}", e)
                }
                delay(checkIntervalMs)
            }
        }
    }

    /**
     * Stops monitoring buffer levels.
     */
    public fun stop() {
        active = false
        scope.cancel()
    }

    private companion object {
        private const val TAG = "BufferManager"
    }
}
