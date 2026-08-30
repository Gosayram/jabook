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
     * Flat (Raw) — no EQ applied, no preamp. All bands at 0 dB.
     * Legacy preset kept for backward compatibility.
     */
    FLAT_RAW(
        displayName = "Flat (Raw)",
        bandGainsMb = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),

    /**
     * Flat — neutral EQ with -3dB headroom to prevent clipping
     * when LUFS normalizer adds gain. All bands at 0 dB.
     */
    FLAT(
        displayName = "Flat",
        bandGainsMb = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        preampMillibels = -300,
    ),

    /**
     * Voice Clarity — boosts the speech frequency range (1kHz–4kHz),
     * cuts low-end rumble (31–62Hz) and reduces 250–500Hz muddiness,
     * with sibilance reduction at 16kHz.
     * Designed for noisy environments where speech pierces through.
     */
    VOICE_CLARITY(
        displayName = "Voice Clarity",
        bandGainsMb = intArrayOf(-300, -200, 0, -200, -150, 100, 250, 300, 150, 50),
        preampMillibels = -300,
    ),

    /**
     * Night Mode — late-night listening at low volume.
     * Cuts sub-bass (31–62Hz) to avoid disturbing others,
     * boosts 125Hz for audible warmth, cuts 250–500Hz muddiness,
     * boosts 1–4kHz for speech clarity at low volume,
     * cuts 16kHz to reduce ear fatigue during extended listening.
     */
    NIGHT(
        displayName = "Night",
        bandGainsMb = intArrayOf(-600, -400, 100, -100, -100, 200, 350, 250, -50, -200),
        preampMillibels = Int.MIN_VALUE + 1,
    ),

    /**
     * Headphones — enhances clarity for headphone listening.
     * Slight bass boost and treble presence for a more engaging sound.
     */
    HEADPHONES(
        displayName = "Headphones",
        bandGainsMb = intArrayOf(100, 50, 0, 100, 200, 260, 200, 150, 100, 50),
        preampMillibels = Int.MIN_VALUE + 1,
    ),

    /**
     * Car — compensates for road noise with boosted mids and presence.
     */
    CAR(
        displayName = "Car",
        bandGainsMb = intArrayOf(-100, 0, 100, 200, 300, 300, 250, 200, 100, 0),
        preampMillibels = Int.MIN_VALUE + 1,
    ),

    /**
     * Male Narrator — optimized for male-voiced narration.
     * Cuts sub-bass (31Hz) for rumble reduction,
     * light low-end boost at 125Hz for vocal warmth,
     * cuts 250–500Hz to reduce muddiness in male voices,
     * boosts 2–4kHz for articulation and presence.
     */
    MALE_NARRATOR(
        displayName = "Male Narrator",
        bandGainsMb = intArrayOf(-400, -100, 150, -250, -200, 50, 200, 300, 100, 0),
        preampMillibels = -300,
    ),

    /**
     * Female Narrator — optimized for female-voiced narration.
     * Aggressive low-end cut (31–125Hz) for thin-frame speakers,
     * neutral mids with no coloration,
     * boost at 2–4kHz for presence and clarity,
     * slight cut at 8kHz to tame sibilance.
     */
    FEMALE_NARRATOR(
        displayName = "Female Narrator",
        bandGainsMb = intArrayOf(-500, -400, -200, -100, 0, 150, 300, 200, -100, -50),
        preampMillibels = -400,
    ),

    /**
     * Car Mode — speech clarity against road and engine noise.
     * Road noise masks 200–500Hz, so those bands are cut to reduce
     * muddiness; bass is reduced to prevent boominess in vehicles;
     * boost 1–4kHz for speech articulation over engine drone.
     */
    CAR_MODE(
        displayName = "Car Mode",
        bandGainsMb = intArrayOf(-200, 200, 300, -300, -200, 100, 300, 400, 200, 100),
        preampMillibels = -400,
    ),

    /**
     * Night Listening — late-night listening at low volume.
     * Cuts sub-bass (31–62Hz) to avoid disturbing others,
     * boosts 125Hz for audible warmth, cuts 250–500Hz muddiness,
     * boosts 1–4kHz for speech clarity at low volume,
     * cuts 16kHz to reduce ear fatigue during extended listening.
     */
    NIGHT_LISTENING(
        displayName = "Night Listening",
        bandGainsMb = intArrayOf(-600, -400, 100, -100, -100, 200, 350, 250, -50, -200),
        preampMillibels = -300,
    ),
    HEADPHONES_BUDGET(
        displayName = "Budget Headphones",
        bandGainsMb = intArrayOf(-400, -300, -200, -150, -100, 200, 350, 300, -100, -300),
        preampMillibels = -300,
    ),
    SPEAKER_PHONE(
        displayName = "Speaker Phone",
        bandGainsMb = intArrayOf(-900, -800, -500, 300, 400, 300, -100, -200, -300, -600),
        preampMillibels = -800,
    ),
    CUSTOM(
        displayName = "Custom",
        bandGainsMb = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        preampMillibels = 0,
    ),
    ;

    /**
     * Computes the effective preamp value. If [preampMillibels] is [PREAMP_AUTO],
     * calculates the safe preamp as the negative of the maximum positive band gain,
     * ensuring the output signal never exceeds the input level (preventing clipping).
     *
     * Note: this preamp only accounts for EQ-internal gain. When this preset is
     * applied after a software gain stage such as [VolumeBoostProcessor] (which
     * runs pre-sink via ExoPlayer's [AudioProcessor] chain while the hardware
     * [android.media.audiofx.Equalizer] runs post-sink on the audio session),
     * the caller must subtract additional headroom for the boost gain
     * (see [AudioEqualizerManager.boostHeadroomMb] / [calculateBoostHeadroomMb]).
     * Otherwise a hot boost output near 0 dBFS followed by EQ would still clip
     * even though `effectivePreamp == -maxPositiveGain` guarantees `max(EQ_out)
     * == max(EQ_in)`.
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
         * Nominal center frequencies (Hz) matching [bandGainsMb] order.
         * Used for frequency-aware mapping of preset gains onto device EQ bands
         * whose count or frequency layout differs from the nominal 10-band grid.
         */
        public val BAND_CENTER_FREQS_HZ: IntArray =
            intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

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

        /**
         * Headroom in millibels required to compensate for [VolumeBoostProcessor]
         * software gain so that hardware EQ (post-sink) does not clip a hot
         * boost output. `Off` → 0 mB, `Boost50` (1.5x) → ~352 mB,
         * `Boost100` (2.0x) → ~602 mB, `Boost200` (3.0x) → ~954 mB.
         *
         * ponytail: 20*log10(gain)*100 — minimal, uses stdlib log10.
         */
        public fun calculateBoostHeadroomMb(boostLevelName: String): Int {
            val gain =
                when (boostLevelName) {
                    "Boost50" -> 1.5
                    "Boost100" -> 2.0
                    "Boost200" -> 3.0
                    "Auto" -> 1.5
                    else -> 1.0
                }
            if (gain <= 1.0) return 0
            return (20.0 * kotlin.math.log10(gain) * 100.0).toInt()
        }
    }
}
