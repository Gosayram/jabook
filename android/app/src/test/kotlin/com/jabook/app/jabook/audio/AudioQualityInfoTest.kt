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

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityInfoTest {
    private fun format(
        mime: String = "audio/mpeg",
        containerMime: String? = null,
        bitrate: Int = 0,
        sampleRate: Int = 0,
        channels: Int = 0,
    ): Format =
        Format
            .Builder()
            .setSampleMimeType(mime)
            .also { b -> containerMime?.let { b.setContainerMimeType(it) } }
            .setAverageBitrate(bitrate)
            .setSampleRate(sampleRate)
            .setChannelCount(channels)
            .build()

    // --- FLAC lossless ---

    @Test
    fun `FLAC format detected as lossless`() {
        val info = AudioQualityInfo.fromFormat(format(mime = "audio/flac", bitrate = 876_000, sampleRate = 44100, channels = 2))

        assertEquals("FLAC", info.format)
        assertTrue(info.isLossless)
        assertEquals(876, info.bitrateKbps)
        assertEquals(44100, info.sampleRateHz)
        assertEquals(2, info.channels)
    }

    // --- MP3 ---

    @Test
    fun `MP3 format detected correctly`() {
        val info = AudioQualityInfo.fromFormat(format(mime = "audio/mpeg", bitrate = 128_000))

        assertEquals("MP3", info.format)
        assertFalse(info.isLossless)
        assertEquals(128, info.bitrateKbps)
    }

    // --- Opus ---

    @Test
    fun `Opus format detected`() {
        val info = AudioQualityInfo.fromFormat(format(mime = "audio/opus", bitrate = 64_000))
        assertEquals("Opus", info.format)
    }

    // --- Zero bitrate ---

    @Test
    fun `zero bitrate returns null kbps`() {
        val info = AudioQualityInfo.fromFormat(format(mime = "audio/mpeg", bitrate = 0))
        assertNull(info.bitrateKbps)
    }

    // --- M4B by container ---

    @Test
    fun `M4B detected by container mime type`() {
        val info = AudioQualityInfo.fromFormat(format(mime = "audio/mp4a-latm", containerMime = "audio/mp4"))
        assertEquals("AAC", info.format)
    }

    // --- toLabel ---

    @Test
    fun `toLabel for FLAC with bitrate`() {
        val info =
            AudioQualityInfo(
                format = "FLAC",
                bitrateKbps = 876,
                sampleRateHz = 44100,
                channels = 2,
                isLossless = true,
            )

        assertEquals("FLAC · 876 кбит/с · Lossless", info.toLabel())
    }

    @Test
    fun `toLabel for MP3 without bitrate`() {
        val info =
            AudioQualityInfo(
                format = "MP3",
                bitrateKbps = null,
                sampleRateHz = null,
                channels = null,
                isLossless = false,
            )

        assertEquals("MP3", info.toLabel())
    }
}
