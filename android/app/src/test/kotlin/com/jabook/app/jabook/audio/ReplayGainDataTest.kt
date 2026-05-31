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
import org.junit.Test

class ReplayGainDataTest {
    // --- fromTags ---

    @Test
    fun `fromTags parses track gain`() {
        val tags = mapOf("REPLAYGAIN_TRACK_GAIN" to "-6.5 dB")
        val data = ReplayGainData.fromTags(tags)
        assertEquals(-6.5f, data.trackGainDb!!, 0.001f)
        assertNull(data.albumGainDb)
    }

    @Test
    fun `fromTags parses album gain without dB suffix`() {
        val tags = mapOf("REPLAYGAIN_ALBUM_GAIN" to "-3.2")
        val data = ReplayGainData.fromTags(tags)
        assertEquals(-3.2f, data.albumGainDb!!, 0.001f)
    }

    @Test
    fun `fromTags parses peak values`() {
        val tags =
            mapOf(
                "REPLAYGAIN_TRACK_PEAK" to "0.9876",
                "REPLAYGAIN_ALBUM_PEAK" to "0.9999",
            )
        val data = ReplayGainData.fromTags(tags)
        assertEquals(0.9876f, data.trackPeak!!, 0.001f)
        assertEquals(0.9999f, data.albumPeak!!, 0.001f)
    }

    @Test
    fun `fromTags with empty map returns empty`() {
        val data = ReplayGainData.fromTags(emptyMap())
        assertFalse(data.hasData())
    }

    // --- bestGainDb ---

    @Test
    fun `bestGainDb prefers track over album`() {
        val data = ReplayGainData(trackGainDb = -6f, albumGainDb = -3f, trackPeak = null, albumPeak = null)
        assertEquals(-6f, data.bestGainDb()!!, 0.001f)
    }

    @Test
    fun `bestGainDb falls back to album`() {
        val data = ReplayGainData(trackGainDb = null, albumGainDb = -3f, trackPeak = null, albumPeak = null)
        assertEquals(-3f, data.bestGainDb()!!, 0.001f)
    }

    // --- adjustedGain ---

    @Test
    fun `adjustedGain applies preamp`() {
        val data = ReplayGainData(trackGainDb = -6f, albumGainDb = null, trackPeak = null, albumPeak = null)
        assertEquals(-4f, data.adjustedGain(preampDb = 2f)!!, 0.001f)
    }

    // --- EMPTY ---

    @Test
    fun `EMPTY has no data`() {
        assertFalse(ReplayGainData.EMPTY.hasData())
        assertNull(ReplayGainData.EMPTY.bestGainDb())
    }
}
