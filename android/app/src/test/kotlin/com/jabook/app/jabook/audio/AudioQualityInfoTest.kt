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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    // --- Quality tier classification ---

    @Test
    fun `lossless format always HIGH tier`() {
        val info = AudioQualityInfo(format = "FLAC", bitrateKbps = 876, sampleRateHz = 44100, channels = 2, isLossless = true)
        assertEquals(QualityTier.HIGH, info.tier)
    }

    @Test
    fun `lossless format with null bitrate still HIGH tier`() {
        val info = AudioQualityInfo(format = "FLAC", bitrateKbps = null, sampleRateHz = null, channels = null, isLossless = true)
        assertEquals(QualityTier.HIGH, info.tier)
    }

    @Test
    fun `high bitrate 320 kbps is HIGH tier`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 320, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals(QualityTier.HIGH, info.tier)
    }

    @Test
    fun `bitrate 256 kbps is HIGH tier`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 256, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals(QualityTier.HIGH, info.tier)
    }

    @Test
    fun `standard bitrate 128 kbps is STANDARD tier`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 128, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals(QualityTier.STANDARD, info.tier)
    }

    @Test
    fun `bitrate 192 kbps is STANDARD tier`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 192, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals(QualityTier.STANDARD, info.tier)
    }

    @Test
    fun `low bitrate 64 kbps is LOW tier`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 64, sampleRateHz = 22050, channels = 1, isLossless = false)
        assertEquals(QualityTier.LOW, info.tier)
    }

    @Test
    fun `null bitrate is LOW tier`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = null, sampleRateHz = null, channels = null, isLossless = false)
        assertEquals(QualityTier.LOW, info.tier)
    }

    @Test
    fun `very low bitrate 32 kbps is LOW tier`() {
        val info = AudioQualityInfo(format = "Opus", bitrateKbps = 32, sampleRateHz = 24000, channels = 1, isLossless = false)
        assertEquals(QualityTier.LOW, info.tier)
    }

    // --- Short label ---

    @Test
    fun `short label with bitrate`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 320, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals("MP3 320", info.toShortLabel())
    }

    @Test
    fun `short label without bitrate`() {
        val info = AudioQualityInfo(format = "FLAC", bitrateKbps = null, sampleRateHz = null, channels = null, isLossless = true)
        assertEquals("FLAC", info.toShortLabel())
    }

    @Test
    fun `short label for M4B`() {
        val info = AudioQualityInfo(format = "M4B", bitrateKbps = 128, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals("M4B 128", info.toShortLabel())
    }

    // --- Full label ---

    @Test
    fun `full label includes all fields for stereo MP3`() {
        val info = AudioQualityInfo(format = "MP3", bitrateKbps = 256, sampleRateHz = 44100, channels = 2, isLossless = false)
        assertEquals("MP3 · 256 кбит/с · 44.1 кГц · стерео", info.toFullLabel())
    }

    @Test
    fun `full label for lossless FLAC with mono`() {
        val info = AudioQualityInfo(format = "FLAC", bitrateKbps = 876, sampleRateHz = 96000, channels = 1, isLossless = true)
        assertEquals("FLAC · 876 кбит/с · Lossless · 96.0 кГц · моно", info.toFullLabel())
    }

    @Test
    fun `full label with multi-channel`() {
        val info = AudioQualityInfo(format = "AAC", bitrateKbps = 192, sampleRateHz = 48000, channels = 6, isLossless = false)
        assertEquals("AAC · 192 кбит/с · 48.0 кГц · 6 кан.", info.toFullLabel())
    }

    @Test
    fun `full label minimal fields`() {
        val info = AudioQualityInfo(format = "OGG", bitrateKbps = null, sampleRateHz = null, channels = null, isLossless = false)
        assertEquals("OGG", info.toFullLabel())
    }
}
