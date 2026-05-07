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
    /**
     * Preamp gain in millibels applied before EQ bands.
     * Automatically calculated to prevent clipping when [PREAMP_AUTO] is used.
     */
    public val preampMillibels: Int = 0,
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
     * Preamp: -4 dB to compensate for +4 dB peak in speech band.
     */
    VOICE_CLARITY(
        displayName = "Voice Clarity",
        bandGainsMb = intArrayOf(-200, -100, 0, 200, 300, 400, 300, 200, 0, -100),
        preampMillibels = Int.MIN_VALUE + 1,
    ),

    /**
     * Night Mode — gentle bass rolloff + slightly boosted mids.
     * Designed for late-night listening at low volume where speech
     * intelligibility matters more than bass impact.
     * Preamp: -3 dB to compensate for +3 dB peak in midrange.
     */
    NIGHT(
        displayName = "Night",
        bandGainsMb = intArrayOf(-300, -200, -100, 0, 200, 300, 200, 100, 0, -100),
        preampMillibels = Int.MIN_VALUE + 1,
    ),
    ;

    /**
     * Computes the effective preamp value. If [preampMillibels] is [PREAMP_AUTO],
     * calculates the safe preamp as the negative of the maximum positive band gain,
     * ensuring the output signal never exceeds the input level (preventing clipping).
     */
    public fun effectivePreamp(): Int =
        if (preampMillibels == PREAMP_AUTO) {
            calculateSafePreamp(bandGainsMb)
        } else {
            preampMillibels
        }

    public companion object {
        /** Default preset used on first launch. */
        public val DEFAULT: EqualizerPreset = FLAT

        /**
         * Returns the number of bands each preset defines.
         * Must match the device EQ capability; shorter arrays are padded with 0.
         */
        public const val BAND_COUNT: Int = 10

        /**
         * Sentinel value indicating preamp should be auto-calculated
         * from the maximum positive band gain to prevent clipping.
         */
        public const val PREAMP_AUTO: Int = Int.MIN_VALUE + 1

        /**
         * Calculates a safe preamp value (in millibels) that prevents clipping.
         *
         * The algorithm: if any band has a positive gain, the preamp is set to
         * the negative of the maximum positive gain. This ensures the total
         * gain at any frequency never exceeds 0 dB.
         *
         * If all bands are ≤ 0 dB, no preamp adjustment is needed (returns 0).
         *
         * @param bandGainsMb array of band gains in millibels
         * @return safe preamp value in millibels (0 or negative)
         */
        public fun calculateSafePreamp(bandGainsMb: IntArray): Int {
            val maxPositiveGain = bandGainsMb.maxOrNull() ?: 0
            return if (maxPositiveGain > 0) -maxPositiveGain else 0
        }

        /**
         * Calculates the headroom in decibels given band gains and applied preamp.
         * Positive headroom means there is no risk of clipping.
         * Negative headroom means clipping may occur.
         *
         * @param bandGainsMb band gains in millibels
         * @param preampMb applied preamp in millibels
         * @return headroom in decibels
         */
        public fun calculateHeadroomDb(
            bandGainsMb: IntArray,
            preampMb: Int,
        ): Double {
            val totalGains = bandGainsMb.map { it + preampMb }
            val maxTotalGainMb = totalGains.maxOrNull() ?: 0
            // Convert mB to dB: 1 dB = 100 mB
            return -maxTotalGainMb / 100.0
        }
    }
}
