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

import com.jabook.app.jabook.audio.processors.SkipSilenceThresholdPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSilenceThresholdPolicyTest {
    @Test
    fun `toNormalizedAmplitude clamps db and returns finite positive value`() {
        val low = SkipSilenceThresholdPolicy.toNormalizedAmplitude(-100f)
        val mid = SkipSilenceThresholdPolicy.toNormalizedAmplitude(-32f)
        val high = SkipSilenceThresholdPolicy.toNormalizedAmplitude(-10f)

        assertTrue(low in 0f..1f)
        assertTrue(mid in 0f..1f)
        assertTrue(high in 0f..1f)
        assertTrue(low < mid)
        assertTrue(mid < high)
    }

    @Test
    fun `sanitizeMinSilenceMs clamps to supported range`() {
        assertEquals(250, SkipSilenceThresholdPolicy.sanitizeMinSilenceMs(0))
        assertEquals(150, SkipSilenceThresholdPolicy.sanitizeMinSilenceMs(20))
        assertEquals(250, SkipSilenceThresholdPolicy.sanitizeMinSilenceMs(250))
        assertEquals(300, SkipSilenceThresholdPolicy.sanitizeMinSilenceMs(500))
    }
}
