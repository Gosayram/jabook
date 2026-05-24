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

import org.junit.Assert.assertEquals
import org.junit.Test

class SteeringWheelActionPolicyTest {
    @Test
    fun `next goes to next chapter when another chapter exists`() {
        assertEquals(
            SteeringWheelActionPolicy.NextAction.NEXT_CHAPTER,
            SteeringWheelActionPolicy.resolveNextAction(
                currentChapterIndex = 0,
                totalChapters = 2,
            ),
        )
    }

    @Test
    fun `next falls back to forward on last chapter or empty playlist`() {
        assertEquals(
            SteeringWheelActionPolicy.NextAction.FORWARD_SECONDS,
            SteeringWheelActionPolicy.resolveNextAction(
                currentChapterIndex = 1,
                totalChapters = 2,
            ),
        )
        assertEquals(
            SteeringWheelActionPolicy.NextAction.FORWARD_SECONDS,
            SteeringWheelActionPolicy.resolveNextAction(
                currentChapterIndex = 0,
                totalChapters = 0,
            ),
        )
    }

    @Test
    fun `previous restarts current chapter after threshold`() {
        assertEquals(
            SteeringWheelActionPolicy.PreviousAction.RESTART_CHAPTER,
            SteeringWheelActionPolicy.resolvePreviousAction(currentPositionMs = 3_001L),
        )
    }

    @Test
    fun `previous goes to previous chapter at or before threshold`() {
        assertEquals(
            SteeringWheelActionPolicy.PreviousAction.PREVIOUS_CHAPTER,
            SteeringWheelActionPolicy.resolvePreviousAction(currentPositionMs = 3_000L),
        )
    }
}
