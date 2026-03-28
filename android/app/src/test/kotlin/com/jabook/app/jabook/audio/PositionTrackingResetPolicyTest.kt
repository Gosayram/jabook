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

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTrackingResetPolicyTest {
    @Test
    fun `resets tracking on IDLE and BUFFERING states`() {
        assertTrue(PositionTrackingResetPolicy.shouldResetOnPlaybackStateChanged(Player.STATE_IDLE))
        assertTrue(PositionTrackingResetPolicy.shouldResetOnPlaybackStateChanged(Player.STATE_BUFFERING))
    }

    @Test
    fun `does not reset tracking on READY and ENDED states`() {
        assertFalse(PositionTrackingResetPolicy.shouldResetOnPlaybackStateChanged(Player.STATE_READY))
        assertFalse(PositionTrackingResetPolicy.shouldResetOnPlaybackStateChanged(Player.STATE_ENDED))
    }

    @Test
    fun `resets tracking when playWhenReady is false`() {
        assertTrue(PositionTrackingResetPolicy.shouldResetOnPlayWhenReadyChanged(playWhenReady = false))
        assertFalse(PositionTrackingResetPolicy.shouldResetOnPlayWhenReadyChanged(playWhenReady = true))
    }
}
