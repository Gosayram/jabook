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
 * P-36: ReplayGain tag data read from audio file metadata.
 *
 * Many audiobooks already contain REPLAYGAIN_TRACK_GAIN and
 * REPLAYGAIN_ALBUM_GAIN tags. Reading these avoids expensive
 * EBU R128 analysis via LufsAnalysisWorker.
 *
 * @property trackGainDb Track gain adjustment in dB
 * @property albumGainDb Album gain adjustment in dB
 * @property trackPeak Track peak level (0.0–1.0)
 * @property albumPeak Album peak level (0.0–1.0)
 */
public data class ReplayGainData(
    val trackGainDb: Float?,
    val albumGainDb: Float?,
    val trackPeak: Float?,
    val albumPeak: Float?,
) {
    /**
     * Returns the best available gain value (track preferred over album).
     */
    public fun bestGainDb(): Float? = trackGainDb ?: albumGainDb

    /**
     * Returns the best available peak value.
     */
    public fun bestPeak(): Float? = trackPeak ?: albumPeak

    /**
     * Whether any ReplayGain data is available.
     */
    public fun hasData(): Boolean = trackGainDb != null || albumGainDb != null

    /**
     * Calculates the preamp-adjusted gain.
     *
     * @param preampDb Preamp adjustment in dB (default: 0.0)
     * @return Adjusted gain, or null if no data
     */
    public fun adjustedGain(preampDb: Float = 0.0f): Float? = bestGainDb()?.let { it + preampDb }

    public companion object {
        /** Empty result when no tags found. */
        public val EMPTY: ReplayGainData = ReplayGainData(null, null, null, null)

        /**
         * Parses ReplayGain tags from a map of tag name → value.
         *
         * @param tags Map of tag names to string values
         * @return Parsed ReplayGain data
         */
        public fun fromTags(tags: Map<String, String>): ReplayGainData =
            ReplayGainData(
                trackGainDb = tags["REPLAYGAIN_TRACK_GAIN"]?.parseGain(),
                albumGainDb = tags["REPLAYGAIN_ALBUM_GAIN"]?.parseGain(),
                trackPeak = tags["REPLAYGAIN_TRACK_PEAK"]?.toFloatOrNull(),
                albumPeak = tags["REPLAYGAIN_ALBUM_PEAK"]?.toFloatOrNull(),
            )

        private fun String.parseGain(): Float? =
            this
                .removeSuffix(" dB")
                .removeSuffix("dB")
                .trim()
                .toFloatOrNull()
    }
}
