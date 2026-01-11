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

import android.animation.ValueAnimator
import android.util.Log
import androidx.core.animation.doOnEnd
import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides smooth volume fade-in and fade-out animations for audio playback.
 *
 * Inspired by RetroMusicPlayer's AudioFader implementation.
 *
 * Features:
 * - Fade-in when playback starts (volume 0 → 1)
 * - Fade-out when playback pauses (volume 1 → 0)
 * - Configurable duration
 * - Proper cleanup of running animations
 *
 * Usage:
 * ```
 * // On play
 * audioFader.fadeIn(player) { player.play() }
 *
 * // On pause
 * audioFader.fadeOut(player) { player.pause() }
 * ```
 */
@Singleton
public class AudioFader
    @Inject
    public constructor() {
        public companion object {
            private const val TAG = "AudioFader"

            /** Default fade duration in milliseconds */
            public const val DEFAULT_FADE_DURATION_MS: Long = 300L

            /** Minimum fade duration (instant) */
            public const val MIN_FADE_DURATION_MS: Long = 0L

            /** Maximum fade duration */
            public const val MAX_FADE_DURATION_MS: Long = 2000L
        }

        private var currentAnimator: ValueAnimator? = null

        /** Current fade duration in milliseconds. Can be configured via settings. */
        public var fadeDurationMs: Long = DEFAULT_FADE_DURATION_MS
            set(value) {
                field = value.coerceIn(MIN_FADE_DURATION_MS, MAX_FADE_DURATION_MS)
            }

        /**
         * Fades in the player volume from 0 to 1.
         *
         * Call this before starting playback for a smooth audio entrance.
         *
         * @param player The ExoPlayer instance
         * @param onComplete Optional callback when fade completes
         */
        public fun fadeIn(
            player: Player,
            onComplete: (() -> Unit)? = null,
        ): Unit {
            cancelCurrentAnimation()

            if (fadeDurationMs == 0L) {
                player.volume = 1f
                onComplete?.invoke()
                Log.v(TAG, "Fade-in skipped (duration=0)")
                return
            }

            player.volume = 0f

            currentAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = fadeDurationMs
                    addUpdateListener { animation ->
                        val volume = animation.animatedValue as Float
                        player.volume = volume
                    }
                    doOnEnd {
                        currentAnimator = null
                        onComplete?.invoke()
                        Log.d(TAG, "Fade-in completed")
                    }
                    start()
                }

            Log.d(TAG, "Fade-in started (duration=${fadeDurationMs}ms)")
        }

        /**
         * Fades out the player volume from 1 to 0.
         *
         * Call this before pausing playback for a smooth audio exit.
         * The callback should contain the actual pause() call.
         *
         * @param player The ExoPlayer instance
         * @param onComplete Callback when fade completes (typically call player.pause() here)
         */
        public fun fadeOut(
            player: Player,
            onComplete: (() -> Unit)? = null,
        ): Unit {
            cancelCurrentAnimation()

            if (fadeDurationMs == 0L) {
                player.volume = 0f
                onComplete?.invoke()
                player.volume = 1f // Reset for next play
                Log.v(TAG, "Fade-out skipped (duration=0)")
                return
            }

            val startVolume = player.volume

            currentAnimator =
                ValueAnimator.ofFloat(startVolume, 0f).apply {
                    duration = fadeDurationMs
                    addUpdateListener { animation ->
                        val volume = animation.animatedValue as Float
                        player.volume = volume
                    }
                    doOnEnd {
                        currentAnimator = null
                        onComplete?.invoke()
                        player.volume = 1f // Reset volume for next play
                        Log.d(TAG, "Fade-out completed")
                    }
                    start()
                }

            Log.d(TAG, "Fade-out started from volume=$startVolume (duration=${fadeDurationMs}ms)")
        }

        /**
         * Cancels any running fade animation immediately.
         *
         * Use this when playback state changes abruptly (e.g., user skips track).
         */
        public fun cancelCurrentAnimation(): Unit {
            currentAnimator?.let { animator ->
                animator.cancel()
                currentAnimator = null
                Log.v(TAG, "Animation cancelled")
            }
        }

        /**
         * Checks if a fade animation is currently running.
         */
        public fun isAnimating(): Boolean = currentAnimator?.isRunning == true

        /**
         * Cleans up resources. Call when service is destroyed.
         */
        public fun release(): Unit {
            cancelCurrentAnimation()
            Log.d(TAG, "AudioFader released")
        }
    }
