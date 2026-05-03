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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistLoadStabilityPolicyTest {
    @Test
    fun `evaluate reaches terminal success after stable expected count`() {
        var state = PlaylistLoadStabilityState(lastCount = 5)
        var terminal: Boolean? = null

        repeat(5) {
            val evaluation = PlaylistLoadStabilityPolicy.evaluate(state, currentCount = 5, expectedCount = 5)
            terminal = evaluation.terminalResult
            state = evaluation.nextState
        }

        assertThat(terminal).isTrue()
    }

    @Test
    fun `evaluate can terminal false on stable non-expected mock count`() {
        var state = PlaylistLoadStabilityState(lastCount = 3)
        var terminal: Boolean? = null

        repeat(11) {
            val evaluation = PlaylistLoadStabilityPolicy.evaluate(state, currentCount = 3, expectedCount = 5)
            terminal = evaluation.terminalResult
            state = evaluation.nextState
            if (terminal != null) return@repeat
        }

        assertThat(terminal).isFalse()
    }

    @Test
    fun `evaluate increments attempts and resets unchanged count when value changes`() {
        val initial =
            PlaylistLoadStabilityState(
                attempts = 3,
                stableCount = 1,
                lastCount = 7,
                unchangedCount = 4,
            )

        val evaluation = PlaylistLoadStabilityPolicy.evaluate(initial, currentCount = 8, expectedCount = 10)

        assertThat(evaluation.terminalResult).isNull()
        assertThat(evaluation.nextState.attempts).isEqualTo(4)
        assertThat(evaluation.nextState.lastCount).isEqualTo(8)
        assertThat(evaluation.nextState.unchangedCount).isEqualTo(0)
    }
}
