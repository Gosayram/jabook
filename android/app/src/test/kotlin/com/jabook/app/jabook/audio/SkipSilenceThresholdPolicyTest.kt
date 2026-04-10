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
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
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

    @Test
    fun `sanitizeRetainWindowMs returns default for non-positive values`() {
        assertEquals(65, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(0))
        assertEquals(65, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(-1))
        assertEquals(65, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(-100))
    }

    @Test
    fun `sanitizeRetainWindowMs clamps to 50-80 ms range`() {
        assertEquals(50, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(10))
        assertEquals(50, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(50))
        assertEquals(65, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(65))
        assertEquals(80, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(80))
        assertEquals(80, SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(200))
    }

    @Test
    fun `property - normalized amplitude is finite and bounded for wide db range`() {
        runTest {
            checkAll(Arb.float(min = -500f, max = 500f)) { db ->
                val amplitude = SkipSilenceThresholdPolicy.toNormalizedAmplitude(db)
                assertTrue(amplitude.isFinite())
                assertTrue(amplitude in 0f..1f)
            }
        }
    }

    @Test
    fun `property - sanitizeMinSilenceMs always returns supported range or default`() {
        runTest {
            checkAll(Arb.int(min = -10_000, max = 10_000)) { value ->
                val sanitized = SkipSilenceThresholdPolicy.sanitizeMinSilenceMs(value)
                assertTrue(sanitized in 150..300)
            }
        }
    }

    @Test
    fun `property - sanitizeRetainWindowMs always returns supported range or default`() {
        runTest {
            checkAll(Arb.int(min = -10_000, max = 10_000)) { value ->
                val sanitized = SkipSilenceThresholdPolicy.sanitizeRetainWindowMs(value)
                assertTrue(sanitized in 50..80)
            }
        }
    }
}
