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

/**
 * Named equalizer presets optimized for audiobook listening.
 *
 * Each preset defines gain values in millibels (mB) for up to 10 frequency bands.
 * 1 dB = 100 mB. The Android `Equalizer` API expects millibel values.
 *
 * Band center frequencies (approximate, 10-band):
 *   31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
 */
public enum class EqualizerPreset(
    public val displayName: String,
    /** Band gains in millibels. Length must match the number of equalizer bands. */
    public val bandGainsMb: IntArray,
) {
    /**
     * Flat — no EQ applied. All bands at 0 dB.
     */
    FLAT(
        displayName = "Flat",
        bandGainsMb = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),

    /**
     * Voice Clarity — boosts the speech frequency range (250Hz–4kHz),
     * cuts very low bass and high treble to reduce rumble and sibilance.
     */
    VOICE_CLARITY(
        displayName = "Voice Clarity",
        bandGainsMb = intArrayOf(-200, -100, 0, 200, 300, 400, 300, 200, 0, -100),
    ),

    /**
     * Night Mode — gentle bass rolloff + slightly boosted mids.
     * Designed for late-night listening at low volume where speech
     * intelligibility matters more than bass impact.
     */
    NIGHT(
        displayName = "Night",
        bandGainsMb = intArrayOf(-300, -200, -100, 0, 200, 300, 200, 100, 0, -100),
    ),
    ;

    public companion object {
        /** Default preset used on first launch. */
        public val DEFAULT: EqualizerPreset = FLAT

        /**
         * Returns the number of bands each preset defines.
         * Must match the device EQ capability; shorter arrays are padded with 0.
         */
        public const val BAND_COUNT: Int = 10
    }
}
