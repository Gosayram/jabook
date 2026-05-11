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

import androidx.media3.common.PlaybackParameters

internal object PitchCorrectionPolicy {
    private const val MIN_SPEED: Float = 0.5f
    private const val MAX_SPEED: Float = 4.0f

    fun buildPlaybackParameters(
        speed: Float,
        isPitchCorrectionEnabled: Boolean,
    ): PlaybackParameters {
        val clampedSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        // Media3 PlaybackParameters: pitch=1.0 keeps natural voice pitch while speed changes.
        val pitch = if (isPitchCorrectionEnabled) 1.0f else 1.0f / clampedSpeed
        return PlaybackParameters(clampedSpeed, pitch)
    }
}
