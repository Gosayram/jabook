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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for visualizer enablement policy.
 *
 * The visualizer should be enabled/disabled based on:
 * 1. User preference toggle
 * 2. Audio offload mode (disable when offloaded)
 * 3. Battery saver / safe mode
 * 4. Audio session availability
 */
class VisualizerEnablementPolicyTest {
    data class VisualizerState(
        val userPreferenceEnabled: Boolean,
        val isAudioOffloaded: Boolean,
        val isBatterySaverActive: Boolean,
        val hasAudioSession: Boolean,
        val isSafeMode: Boolean,
    )

    private fun shouldEnableVisualizer(state: VisualizerState): Boolean {
        if (!state.userPreferenceEnabled) return false
        if (state.isAudioOffloaded) return false
        if (state.isBatterySaverActive) return false
        if (state.isSafeMode) return false
        return state.hasAudioSession
    }

    @Test
    fun `visualizer disabled when user preference is off`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = false,
                isAudioOffloaded = false,
                isBatterySaverActive = false,
                hasAudioSession = true,
                isSafeMode = false,
            )
        assertFalse(shouldEnableVisualizer(state))
    }

    @Test
    fun `visualizer enabled when all conditions met`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = true,
                isAudioOffloaded = false,
                isBatterySaverActive = false,
                hasAudioSession = true,
                isSafeMode = false,
            )
        assertTrue(shouldEnableVisualizer(state))
    }

    @Test
    fun `visualizer disabled during audio offload`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = true,
                isAudioOffloaded = true,
                isBatterySaverActive = false,
                hasAudioSession = true,
                isSafeMode = false,
            )
        assertFalse(shouldEnableVisualizer(state))
    }

    @Test
    fun `visualizer disabled during battery saver`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = true,
                isAudioOffloaded = false,
                isBatterySaverActive = true,
                hasAudioSession = true,
                isSafeMode = false,
            )
        assertFalse(shouldEnableVisualizer(state))
    }

    @Test
    fun `visualizer disabled in safe mode`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = true,
                isAudioOffloaded = false,
                isBatterySaverActive = false,
                hasAudioSession = true,
                isSafeMode = true,
            )
        assertFalse(shouldEnableVisualizer(state))
    }

    @Test
    fun `visualizer disabled without audio session`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = true,
                isAudioOffloaded = false,
                isBatterySaverActive = false,
                hasAudioSession = false,
                isSafeMode = false,
            )
        assertFalse(shouldEnableVisualizer(state))
    }

    @Test
    fun `user preference off takes priority over all other conditions`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = false,
                isAudioOffloaded = false,
                isBatterySaverActive = false,
                hasAudioSession = true,
                isSafeMode = false,
            )
        assertFalse(shouldEnableVisualizer(state))
    }

    @Test
    fun `visualizer disabled when both offload and battery saver active`() {
        val state =
            VisualizerState(
                userPreferenceEnabled = true,
                isAudioOffloaded = true,
                isBatterySaverActive = true,
                hasAudioSession = true,
                isSafeMode = false,
            )
        assertFalse(shouldEnableVisualizer(state))
    }
}
