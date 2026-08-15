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

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HoldToBoostControllerTest {
    private lateinit var player: ExoPlayer
    private lateinit var policy: HoldToBoostPolicy
    private lateinit var controller: HoldToBoostController

    @Before
    fun setUp() {
        player = mock()
        policy = HoldToBoostPolicy(boostSpeed = 3.0f)
        whenever(player.playbackParameters).thenReturn(PlaybackParameters(1.0f))
        controller =
            HoldToBoostController(
                player = player,
                policy = policy,
                rampUpMs = 0L,
                rampDownMs = 0L,
                rampSteps = 1,
            )
    }

    // --- onHoldCancel restores speed ---

    @Test
    fun `onHoldCancel restores saved speed`() {
        policy.onPress(1.5f)
        controller.onHoldCancel()

        verify(player).playbackParameters = PlaybackParameters(1.5f)
    }

    // --- onHoldCancel without press does nothing ---

    @Test
    fun `onHoldCancel without press does nothing`() {
        controller.onHoldCancel()
        // No interaction with player
    }

    // --- default constants ---

    @Test
    fun `default ramp up is 200ms`() {
        assertEquals(200L, HoldToBoostController.DEFAULT_RAMP_UP_MS)
    }

    @Test
    fun `default ramp down is 150ms`() {
        assertEquals(150L, HoldToBoostController.DEFAULT_RAMP_DOWN_MS)
    }

    @Test
    fun `default ramp steps is 10`() {
        assertEquals(10, HoldToBoostController.DEFAULT_RAMP_STEPS)
    }
}
