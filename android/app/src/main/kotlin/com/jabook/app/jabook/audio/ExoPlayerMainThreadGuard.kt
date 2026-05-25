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

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Ensures all ExoPlayer calls happen on the main looper thread.
 *
 * ExoPlayer is @Singleton and NOT thread-safe — all calls must occur on the
 * application's main looper thread. Calling from IO dispatcher (e.g. PlaylistManager,
 * PositionSaver) causes race conditions and ANRs.
 *
 * P-28: This guard wraps ExoPlayer access with automatic thread switching:
 * - [runOnMain] blocks the caller until the action runs on main (use sparingly)
 * - [postToMain] fires and forgets — no blocking (preferred for most cases)
 *
 * @param player The ExoPlayer instance to guard
 * @param mainHandler Handler for the main looper (injectable for testing)
 */
internal class ExoPlayerMainThreadGuard(
    private val player: Player,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    /**
     * Runs [block] on the main thread and waits for the result (blocking).
     *
     * Use only when the return value is needed synchronously (e.g. reading
     * player state for a decision). Prefer [postToMain] for fire-and-forget.
     *
     * @param timeoutMs Maximum wait time in milliseconds (default: 5s)
     * @param block Lambda receiving the player, executed on main thread
     * @return Result of [block], or null if timed out or interrupted
     */
    fun <T> runOnMain(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: Player.() -> T,
    ): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try {
                player.block()
            } catch (e: Exception) {
                LogUtils.e(TAG, "runOnMain failed on main thread", e)
                null
            }
        }

        val resultRef = AtomicReference<T>(null)
        val errorRef = AtomicReference<Throwable>(null)
        val latch = CountDownLatch(1)

        val posted =
            mainHandler.post {
                try {
                    resultRef.set(player.block())
                } catch (e: Throwable) {
                    errorRef.set(e)
                } finally {
                    latch.countDown()
                }
            }

        if (!posted) {
            LogUtils.e(TAG, "Failed to post to main handler (handler shutting down?)")
            return null
        }

        try {
            val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                LogUtils.e(TAG, "runOnMain timed out after ${timeoutMs}ms")
                return null
            }
        } catch (e: InterruptedException) {
            LogUtils.e(TAG, "runOnMain interrupted", e)
            Thread.currentThread().interrupt()
            return null
        }

        errorRef.get()?.let { error ->
            LogUtils.e(TAG, "runOnMain block threw exception", error)
            return null
        }

        return resultRef.get()
    }

    /**
     * Posts [block] to the main thread without blocking the caller.
     *
     * Preferred over [runOnMain] when no return value is needed (e.g. seekTo,
     * playWhenReady, pause). Non-blocking and avoids potential deadlocks.
     *
     * @param block Lambda receiving the player, executed on main thread
     */
    fun postToMain(block: Player.() -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                player.block()
            } catch (e: Exception) {
                LogUtils.e(TAG, "postToMain failed on main thread", e)
            }
            return
        }

        val posted =
            mainHandler.post {
                try {
                    player.block()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "postToMain block threw exception", e)
                }
            }

        if (!posted) {
            LogUtils.e(TAG, "Failed to post to main handler (handler shutting down?)")
        }
    }

    companion object {
        private const val TAG = "ExoPlayerMainThreadGuard"
        private const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
