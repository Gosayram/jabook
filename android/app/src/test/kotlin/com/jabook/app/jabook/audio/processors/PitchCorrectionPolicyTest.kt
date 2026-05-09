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

package com.jabook.app.jabook.audio.processors

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchCorrectionPolicyTest {
    @Test
    fun `at 2x speed pitch becomes 0_5 when correction enabled`() {
        val params =
            PitchCorrectionPolicy.buildPlaybackParameters(
                speed = 2.0f,
                isPitchCorrectionEnabled = true,
            )

        assertEquals(2.0f, params.speed, 0.001f)
        assertEquals(0.5f, params.pitch, 0.001f)
    }

    @Test
    fun `without pitch correction pitch stays 1_0`() {
        val params =
            PitchCorrectionPolicy.buildPlaybackParameters(
                speed = 3.0f,
                isPitchCorrectionEnabled = false,
            )

        assertEquals(3.0f, params.speed, 0.001f)
        assertEquals(1.0f, params.pitch, 0.001f)
    }

    @Test
    fun `speed is clamped to 0_5 to 4_0 range`() {
        val tooSlow = PitchCorrectionPolicy.buildPlaybackParameters(0.1f, isPitchCorrectionEnabled = false)
        val tooFast = PitchCorrectionPolicy.buildPlaybackParameters(10.0f, isPitchCorrectionEnabled = false)

        assertEquals(0.5f, tooSlow.speed, 0.001f)
        assertEquals(4.0f, tooFast.speed, 0.001f)
    }
}
