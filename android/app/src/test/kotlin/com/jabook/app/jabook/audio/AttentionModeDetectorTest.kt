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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttentionModeDetectorTest {
    private lateinit var detector: AttentionModeDetector

    @Before
    fun setUp() {
        detector = AttentionModeDetector()
    }

    // --- Initially attentive ---

    @Test
    fun `initially attentive`() {
        val state = detector.evaluate()
        assertTrue(state.isAttentive)
        assertNull(state.reason)
    }

    // --- Position advance resets timer ---

    @Test
    fun `position advance keeps attentive`() {
        detector.onPositionAdvanced(10_000L)
        val state = detector.evaluate()
        assertTrue(state.isAttentive)
    }

    // --- Pause triggers inattention ---

    @Test
    fun `paused state triggers inattention`() {
        detector.onPaused()
        val state = detector.evaluate()
        assertFalse(state.isAttentive)
        assertEquals(AttentionModeDetector.InattentionReason.LONG_INACTIVITY, state.reason)
    }

    // --- Resume restores attention ---

    @Test
    fun `resume restores attention`() {
        detector.onPaused()
        detector.onResumed()
        val state = detector.evaluate()
        assertTrue(state.isAttentive)
    }

    // --- Skip tracking ---

    @Test
    fun `single skip keeps attentive`() {
        detector.onSkip()
        val state = detector.evaluate()
        assertTrue(state.isAttentive)
    }

    // --- Rapid skipping ---

    @Test
    fun `rapid skipping triggers inattention`() {
        repeat(5) { detector.onSkip() }
        val state = detector.evaluate()
        assertFalse(state.isAttentive)
        assertEquals(AttentionModeDetector.InattentionReason.RAPID_SKIPPING, state.reason)
    }

    // --- Reset ---

    @Test
    fun `reset clears all state`() {
        detector.onPaused()
        detector.onSkip()
        detector.reset()
        val state = detector.evaluate()
        assertTrue(state.isAttentive)
    }

    // --- Constants ---

    @Test
    fun `default inactivity threshold is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, AttentionModeDetector.DEFAULT_INACTIVITY_THRESHOLD_MS)
    }

    @Test
    fun `default skip threshold is 5`() {
        assertEquals(5, AttentionModeDetector.DEFAULT_SKIP_THRESHOLD)
    }
}
