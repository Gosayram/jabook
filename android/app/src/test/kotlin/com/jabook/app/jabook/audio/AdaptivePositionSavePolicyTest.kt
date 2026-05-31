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
import org.junit.Before
import org.junit.Test

class AdaptivePositionSavePolicyTest {
    private lateinit var policy: AdaptivePositionSavePolicy

    @Before
    fun setUp() {
        policy = AdaptivePositionSavePolicy()
    }

    // --- 1x speed ---

    @Test
    fun `1x speed in middle returns maxContentLoss`() {
        val interval = policy.calculateIntervalMs(1.0f, 0.5f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_MAX_CONTENT_LOSS_MS, interval)
    }

    // --- 2x speed ---

    @Test
    fun `2x speed halves the interval`() {
        val interval = policy.calculateIntervalMs(2.0f, 0.5f)
        assertEquals(15_000L, interval)
    }

    // --- 3x speed ---

    @Test
    fun `3x speed gives 10 seconds`() {
        val interval = policy.calculateIntervalMs(3.0f, 0.5f)
        assertEquals(10_000L, interval)
    }

    // --- 4x speed ---

    @Test
    fun `4x speed gives 7_5 seconds`() {
        val interval = policy.calculateIntervalMs(4.0f, 0.5f)
        assertEquals(7_500L, interval)
    }

    // --- Edge positions use fixed interval ---

    @Test
    fun `near start uses edge interval`() {
        val interval = policy.calculateIntervalMs(2.0f, 0.05f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_EDGE_INTERVAL_MS, interval)
    }

    @Test
    fun `near end uses edge interval`() {
        val interval = policy.calculateIntervalMs(2.0f, 0.95f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_EDGE_INTERVAL_MS, interval)
    }

    // --- Minimum interval ---

    @Test
    fun `very high speed does not go below minimum`() {
        val interval = policy.calculateIntervalMs(100.0f, 0.5f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_MIN_INTERVAL_MS, interval)
    }

    // --- Zero speed ---

    @Test
    fun `zero speed returns edge interval as fallback`() {
        val interval = policy.calculateIntervalMs(0f, 0.5f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_EDGE_INTERVAL_MS, interval)
    }

    @Test
    fun `negative speed returns edge interval as fallback`() {
        val interval = policy.calculateIntervalMs(-1.0f, 0.5f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_EDGE_INTERVAL_MS, interval)
    }

    // --- calculateMiddleIntervalMs ---

    @Test
    fun `middle interval at 1x is maxContentLoss`() {
        val interval = policy.calculateMiddleIntervalMs(1.0f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_MAX_CONTENT_LOSS_MS, interval)
    }

    @Test
    fun `middle interval at 2x is 15 seconds`() {
        val interval = policy.calculateMiddleIntervalMs(2.0f)
        assertEquals(15_000L, interval)
    }

    @Test
    fun `middle interval for zero speed returns maxContentLoss`() {
        val interval = policy.calculateMiddleIntervalMs(0f)
        assertEquals(AdaptivePositionSavePolicy.DEFAULT_MAX_CONTENT_LOSS_MS, interval)
    }

    // --- Custom parameters ---

    @Test
    fun `custom maxContentLoss respected`() {
        val custom = AdaptivePositionSavePolicy(maxContentLossMs = 60_000L)
        val interval = custom.calculateIntervalMs(2.0f, 0.5f)
        assertEquals(30_000L, interval)
    }

    @Test
    fun `custom minInterval respected`() {
        val custom = AdaptivePositionSavePolicy(minIntervalMs = 10_000L)
        val interval = custom.calculateIntervalMs(100.0f, 0.5f)
        assertEquals(10_000L, interval)
    }
}
