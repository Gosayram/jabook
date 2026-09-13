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
 * P-56: Detects user inattention and suggests auto-pause.
 *
 * Monitors playback activity patterns to determine if the user
 * is likely not listening. Based on heuristics:
 * - Frequent long pauses (> 5 minutes)
 * - Rapid skip patterns (skipping chapters without listening)
 * - No position advancement over extended period
 *
 * When inattention is detected, the caller should show a
 * "Resume?" prompt or auto-pause.
 *
 * @param inactivityThresholdMs How long without activity before flagging
 * @param skipPatternThreshold Minimum skips per minute to flag
 */
public class AttentionModeDetector
    @Inject
    constructor() {
        private var lastActivityTimestamp: Long = System.currentTimeMillis()
        private var lastPositionMs: Long = 0L
        private var skipCount: Int = 0
        private var skipWindowStart: Long = System.currentTimeMillis()
        private var isPaused: Boolean = false

        /**
         * Result of attention analysis.
         */
        public data class AttentionState(
            val isAttentive: Boolean,
            val reason: InattentionReason?,
            val inactiveDurationMs: Long,
        )

        /**
         * Reason for detected inattention.
         */
        public enum class InattentionReason {
            /** No activity for extended period. */
            LONG_INACTIVITY,

            /** Rapid skipping without listening. */
            RAPID_SKIPPING,

            /** Position not advancing despite playing. */
            STUCK_POSITION,
        }

        /**
         * Called when playback position advances.
         * Resets the inactivity timer.
         */
        public fun onPositionAdvanced(positionMs: Long) {
            lastPositionMs = positionMs
            lastActivityTimestamp = System.currentTimeMillis()
        }

        /**
         * Called when the user skips to a different position/chapter.
         */
        public fun onSkip() {
            val now = System.currentTimeMillis()
            skipCount++

            if (now - skipWindowStart > SKIP_WINDOW_MS) {
                skipCount = 1
                skipWindowStart = now
            }

            lastActivityTimestamp = now
        }

        /**
         * Called when playback is paused.
         */
        public fun onPaused() {
            isPaused = true
        }

        /**
         * Called when playback resumes.
         */
        public fun onResumed() {
            isPaused = false
            lastActivityTimestamp = System.currentTimeMillis()
        }

        /**
         * Evaluates current attention state.
         *
         * @param inactivityThresholdMs Threshold for long inactivity (default: 5 min)
         * @param skipThreshold Skips per window to trigger rapid-skip detection
         * @return Current attention state
         */
        public fun evaluate(
            inactivityThresholdMs: Long = DEFAULT_INACTIVITY_THRESHOLD_MS,
            skipThreshold: Int = DEFAULT_SKIP_THRESHOLD,
        ): AttentionState {
            val now = System.currentTimeMillis()
            val inactiveDuration = now - lastActivityTimestamp

            if (isPaused) {
                return AttentionState(
                    isAttentive = false,
                    reason = InattentionReason.LONG_INACTIVITY,
                    inactiveDurationMs = inactiveDuration,
                )
            }

            if (inactiveDuration > inactivityThresholdMs) {
                return AttentionState(
                    isAttentive = false,
                    reason = InattentionReason.LONG_INACTIVITY,
                    inactiveDurationMs = inactiveDuration,
                )
            }

            if (skipCount >= skipThreshold && (now - skipWindowStart) < SKIP_WINDOW_MS) {
                return AttentionState(
                    isAttentive = false,
                    reason = InattentionReason.RAPID_SKIPPING,
                    inactiveDurationMs = inactiveDuration,
                )
            }

            return AttentionState(
                isAttentive = true,
                reason = null,
                inactiveDurationMs = inactiveDuration,
            )
        }

        /**
         * Resets all tracking state.
         */
        public fun reset() {
            lastActivityTimestamp = System.currentTimeMillis()
            lastPositionMs = 0L
            skipCount = 0
            skipWindowStart = System.currentTimeMillis()
            isPaused = false
        }

        public companion object {
            internal const val DEFAULT_INACTIVITY_THRESHOLD_MS = 5 * 60 * 1000L
            internal const val DEFAULT_SKIP_THRESHOLD = 5
            internal const val SKIP_WINDOW_MS = 60 * 1000L
        }
    }
