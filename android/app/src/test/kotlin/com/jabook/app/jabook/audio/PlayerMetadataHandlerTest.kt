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

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class PlayerMetadataHandlerTest {
    private val handler =
        PlayerMetadataHandler(
            context = mock(Context::class.java),
            setEmbeddedArtworkPath = {},
        )

    @Test
    fun `extractReplayGain chooses track gain when present`() {
        val gain =
            handler.extractReplayGainDbFromEntries(
                listOf(
                    "TXXX: description=REPLAYGAIN_TRACK_GAIN, value=-7.20 dB",
                    "TXXX: description=REPLAYGAIN_ALBUM_GAIN, value=-5.00 dB",
                ),
            )

        assertNotNull(gain)
        assertEquals(-7.2f, gain ?: 0f, 0.0001f)
    }

    @Test
    fun `extractReplayGain falls back to album gain`() {
        val gain =
            handler.extractReplayGainDbFromEntries(
                listOf("TXXX: description=REPLAYGAIN_ALBUM_GAIN, value=-4.50 dB"),
            )

        assertNotNull(gain)
        assertEquals(-4.5f, gain ?: 0f, 0.0001f)
    }

    @Test
    fun `extractReplayGain supports inline key value format`() {
        val gain =
            handler.extractReplayGainDbFromEntries(
                listOf("REPLAYGAIN_TRACK_GAIN: -6.10 dB"),
            )

        assertNotNull(gain)
        assertEquals(-6.1f, gain ?: 0f, 0.0001f)
    }

    @Test
    fun `extractReplayGain returns null for malformed values`() {
        val gain =
            handler.extractReplayGainDbFromEntries(
                listOf(
                    "REPLAYGAIN_TRACK_GAIN: no_number",
                    "TXXX: description=REPLAYGAIN_ALBUM_GAIN, value=?? dB",
                ),
            )

        assertNull(gain)
    }
}
