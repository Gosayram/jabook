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

internal data class PlaylistLoadStabilityState(
    val attempts: Int = 0,
    val stableCount: Int = 0,
    val lastCount: Int = 0,
    val unchangedCount: Int = 0,
)

internal data class PlaylistLoadStabilityEvaluation(
    val nextState: PlaylistLoadStabilityState,
    val terminalResult: Boolean? = null,
)

internal object PlaylistLoadStabilityPolicy {
    internal const val MAX_WAIT_ATTEMPTS = 200
    internal const val WAIT_POLL_DELAY_MS = 100L
    private const val STABLE_COUNT_REQUIRED = 5
    private const val MOCK_STABLE_UNCHANGED_ATTEMPTS = 10
    private const val MOCK_STABLE_MIN_TOTAL_ATTEMPTS = 10

    internal fun evaluate(
        state: PlaylistLoadStabilityState,
        currentCount: Int,
        expectedCount: Int,
    ): PlaylistLoadStabilityEvaluation {
        val unchangedCount = if (currentCount == state.lastCount) state.unchangedCount + 1 else 0

        if (unchangedCount >= MOCK_STABLE_UNCHANGED_ATTEMPTS &&
            state.attempts >= MOCK_STABLE_MIN_TOTAL_ATTEMPTS
        ) {
            if (currentCount == expectedCount) {
                return PlaylistLoadStabilityEvaluation(nextState = state, terminalResult = true)
            }
            if (currentCount > 0) {
                return PlaylistLoadStabilityEvaluation(
                    nextState = state,
                    terminalResult = currentCount >= expectedCount,
                )
            }
        }

        val stableCount =
            if (currentCount == expectedCount) {
                if (currentCount == state.lastCount) state.stableCount + 1 else 0
            } else {
                0
            }

        if (stableCount >= STABLE_COUNT_REQUIRED) {
            return PlaylistLoadStabilityEvaluation(nextState = state, terminalResult = true)
        }

        return PlaylistLoadStabilityEvaluation(
            nextState =
                state.copy(
                    attempts = state.attempts + 1,
                    stableCount = stableCount,
                    lastCount = currentCount,
                    unchangedCount = unchangedCount,
                ),
            terminalResult = null,
        )
    }
}
