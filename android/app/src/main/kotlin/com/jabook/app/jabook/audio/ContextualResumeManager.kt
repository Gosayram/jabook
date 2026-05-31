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

import javax.inject.Inject

/**
 * P-53: Context-aware resume after long pauses.
 *
 * Instead of a fixed rewind (e.g., always 10 seconds), this class
 * calculates the optimal rewind based on how long the user was away:
 * - Short pause (< 5 min): no rewind
 * - Medium pause (5 min – 1 hour): rewind 10–30 seconds
 * - Long pause (1–24 hours): rewind 1–2 minutes
 * - Very long pause (> 24 hours): rewind 2–5 minutes + show recap
 *
 * Inspired by Audible's Smart Resume.
 */
public class ContextualResumeManager
    @Inject
    constructor() {
        /**
         * Resume context calculated from pause duration.
         */
        public data class ResumeContext(
            val pauseDurationMs: Long,
            val rewindMs: Long,
            val shouldShowRecap: Boolean,
            val recapDurationMs: Long,
        )

        /**
         * Calculates the optimal resume context.
         *
         * @param pauseDurationMs How long the user was away
         * @return Resume context with rewind and recap settings
         */
        public fun buildResumeContext(pauseDurationMs: Long): ResumeContext {
            val pauseMinutes = pauseDurationMs / (60 * 1000L)

            return when {
                pauseMinutes < SHORT_PAUSE_MINUTES -> {
                    ResumeContext(
                        pauseDurationMs = pauseDurationMs,
                        rewindMs = 0L,
                        shouldShowRecap = false,
                        recapDurationMs = 0L,
                    )
                }

                pauseMinutes < MEDIUM_PAUSE_MINUTES -> {
                    val rewindMs =
                        calculateLinearRewind(
                            pauseMinutes = pauseMinutes,
                            minRewindMs = SHORT_REWIND_MS,
                            maxRewindMs = MEDIUM_REWIND_MS,
                            minPause = SHORT_PAUSE_MINUTES,
                            maxPause = MEDIUM_PAUSE_MINUTES,
                        )
                    ResumeContext(
                        pauseDurationMs = pauseDurationMs,
                        rewindMs = rewindMs,
                        shouldShowRecap = false,
                        recapDurationMs = 0L,
                    )
                }

                pauseMinutes < LONG_PAUSE_MINUTES -> {
                    val rewindMs =
                        calculateLinearRewind(
                            pauseMinutes = pauseMinutes,
                            minRewindMs = MEDIUM_REWIND_MS,
                            maxRewindMs = LONG_REWIND_MS,
                            minPause = MEDIUM_PAUSE_MINUTES,
                            maxPause = LONG_PAUSE_MINUTES,
                        )
                    ResumeContext(
                        pauseDurationMs = pauseDurationMs,
                        rewindMs = rewindMs,
                        shouldShowRecap = false,
                        recapDurationMs = 0L,
                    )
                }

                else -> {
                    ResumeContext(
                        pauseDurationMs = pauseDurationMs,
                        rewindMs = VERY_LONG_REWIND_MS,
                        shouldShowRecap = true,
                        recapDurationMs = RECAP_DURATION_MS,
                    )
                }
            }
        }

        /**
         * Whether the pause is long enough to warrant special handling.
         */
        public fun isLongPause(pauseDurationMs: Long): Boolean = pauseDurationMs >= SHORT_PAUSE_MINUTES * 60 * 1000L

        private fun calculateLinearRewind(
            pauseMinutes: Long,
            minRewindMs: Long,
            maxRewindMs: Long,
            minPause: Long,
            maxPause: Long,
        ): Long {
            val fraction = ((pauseMinutes - minPause).toFloat() / (maxPause - minPause)).coerceIn(0f, 1f)
            return minRewindMs + ((maxRewindMs - minRewindMs) * fraction).toLong()
        }

        public companion object {
            private const val SHORT_PAUSE_MINUTES = 5L
            private const val MEDIUM_PAUSE_MINUTES = 60L
            private const val LONG_PAUSE_MINUTES = 24 * 60L

            internal const val SHORT_REWIND_MS = 10_000L
            internal const val MEDIUM_REWIND_MS = 30_000L
            internal const val LONG_REWIND_MS = 120_000L
            internal const val VERY_LONG_REWIND_MS = 300_000L
            internal const val RECAP_DURATION_MS = 120_000L
        }
    }
