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

/**
 * Manages the hold-to-boost speed behaviour for the player.
 *
 * When the user presses and holds the boost button/area, playback speed
 * temporarily increases to [BOOST_SPEED]. When the hold is released or
 * cancelled, speed restores to the value that was active before the hold.
 *
 * This is a pure state-machine with no side-effects; the caller is
 * responsible for applying the returned speed to the actual player.
 *
 * ponytail: Media3 1.11 PlaybackSpeedState drives system UI (temporarilyOverrideSpeedWith /
 * restoreOverriddenSpeed). This policy remains the source-of-truth for boostSpeed (3.0×)
 * and delegates to PlaybackSpeedState when available; callers may also call
 * Player.setPlaybackSpeed via state.updatePlaybackSpeed for persistent changes.
 */
public class HoldToBoostPolicy(
    public val boostSpeed: Float = DEFAULT_BOOST_SPEED,
) {
    init {
        require(boostSpeed > 0f) { "boostSpeed must be positive, got $boostSpeed" }
    }

    private var savedSpeed: Float? = null

    /** Whether the boost is currently active. */
    public val isBoosting: Boolean get() = savedSpeed != null

    /** The speed that should be applied right now. */
    public val currentSpeed: Float?
        get() = if (isBoosting) boostSpeed else savedSpeed

    /**
     * Called on press-down (pointer/button down).
     *
     * @param currentPlaybackSpeed the speed that is active right now, before boost
     * @return the speed that should be applied (will be [boostSpeed])
     */
    public fun onPress(currentPlaybackSpeed: Float): Float {
        if (isBoosting) return boostSpeed // already boosting, ignore duplicate
        savedSpeed = currentPlaybackSpeed
        return boostSpeed
    }

    /**
     * Called on release (pointer/button up).
     *
     * @return the speed that should be restored, or null if no saved speed
     */
    public fun onRelease(): Float? {
        val restore = savedSpeed
        savedSpeed = null
        return restore
    }

    /**
     * Called when the hold is cancelled (e.g. focus loss, gesture cancellation).
     *
     * @return the speed that should be restored, or null if no saved speed
     */
    public fun onCancel(): Float? = onRelease()

    /**
     * ponytail: Media3 1.11 system-UI bridge — mirrors this policy onto PlaybackSpeedState.
     * Long-press FF in system UI calls temporarilyOverrideSpeedWith; release calls restore.
     */
    public fun bindToPlaybackSpeedState(state: androidx.media3.ui.compose.state.PlaybackSpeedState) {
        state.temporarilyOverrideSpeedWith(boostSpeed)
    }

    public fun restorePlaybackSpeedState(state: androidx.media3.ui.compose.state.PlaybackSpeedState) {
        state.restoreOverriddenSpeed()
    }

    public companion object {
        /** Default boost speed (3.0x). */
        public const val DEFAULT_BOOST_SPEED: Float = 3.0f
    }
}
