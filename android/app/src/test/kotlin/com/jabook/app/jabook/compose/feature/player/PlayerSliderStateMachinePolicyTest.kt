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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSliderStateMachinePolicyTest {
    @Test
    fun `displayed progress prioritizes drag over pending over live`() {
        assertEquals(
            0.7f,
            PlayerSliderStateMachinePolicy.displayedProgress(
                liveProgress = 0.2f,
                dragProgress = 0.7f,
                pendingSeekProgress = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            PlayerSliderStateMachinePolicy.displayedProgress(
                liveProgress = 0.2f,
                dragProgress = null,
                pendingSeekProgress = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            0.2f,
            PlayerSliderStateMachinePolicy.displayedProgress(
                liveProgress = 0.2f,
                dragProgress = null,
                pendingSeekProgress = null,
            ),
            0.0001f,
        )
    }

    @Test
    fun `coalesce keeps previous value for tiny progress deltas`() {
        val result =
            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                previousProgress = 0.100f,
                incomingProgress = 0.1004f,
                totalDurationMs = 600_000L, // 10 minutes => dynamic step ~= 0.0004, min step 0.001
            )

        assertEquals(0.100f, result, 0.0001f)
    }

    @Test
    fun `coalesce accepts larger progress deltas`() {
        val result =
            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                previousProgress = 0.100f,
                incomingProgress = 0.120f,
                totalDurationMs = 600_000L,
            )

        assertEquals(0.120f, result, 0.0001f)
    }

    @Test
    fun `coalesce clamps and sanitizes invalid values`() {
        assertEquals(
            1f,
            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                previousProgress = 0.3f,
                incomingProgress = 1.4f,
                totalDurationMs = 1_000L,
            ),
            0.0001f,
        )

        assertEquals(
            0.3f,
            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                previousProgress = 0.3f,
                incomingProgress = Float.NaN,
                totalDurationMs = 1_000L,
            ),
            0.0001f,
        )
    }
}
