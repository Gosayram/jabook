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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlayerStatsTest {
    @Test
    fun `default PlayerStats has expected defaults`() {
        val stats = PlayerStats()
        assertEquals("Unknown", stats.audioFormat)
        assertEquals("Unknown", stats.bitrate)
        assertEquals("Unknown", stats.sampleRate)
        assertEquals("Unknown", stats.channelLayout)
        assertEquals("0s", stats.bufferHealth)
        assertEquals("Unknown", stats.audioSessionId)
        assertEquals("Unknown", stats.decoderName)
        assertEquals(0, stats.droppedFrames)
        assertFalse(stats.isStreaming)
        assertEquals(null, stats.audioQuality)
    }

    @Test
    fun `PlayerStats with custom values`() {
        val stats =
            PlayerStats(
                audioFormat = "audio/mpeg 128kbps",
                bitrate = "128 kbps",
                sampleRate = "44.1 kHz",
                channelLayout = "Stereo",
                bufferHealth = "15s",
                audioSessionId = "42",
                decoderName = "ExoPlayer Audio Decoder",
                droppedFrames = 3,
                isStreaming = true,
            )
        assertEquals("audio/mpeg 128kbps", stats.audioFormat)
        assertEquals("128 kbps", stats.bitrate)
        assertEquals("44.1 kHz", stats.sampleRate)
        assertEquals("Stereo", stats.channelLayout)
        assertEquals("15s", stats.bufferHealth)
        assertEquals("42", stats.audioSessionId)
        assertEquals(3, stats.droppedFrames)
        assertTrue(stats.isStreaming)
    }

    @Test
    fun `PlayerStats equality works`() {
        val a = PlayerStats(audioFormat = "FLAC", droppedFrames = 5)
        val b = PlayerStats(audioFormat = "FLAC", droppedFrames = 5)
        val c = PlayerStats(audioFormat = "MP3", droppedFrames = 5)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `PlayerStats copy preserves unmodified fields`() {
        val original =
            PlayerStats(
                audioFormat = "audio/mp4a-latm",
                bitrate = "256 kbps",
                sampleRate = "48.0 kHz",
                channelLayout = "5.1",
                droppedFrames = 0,
            )
        val modified = original.copy(droppedFrames = 10)
        assertEquals(10, modified.droppedFrames)
        assertEquals(original.audioFormat, modified.audioFormat)
        assertEquals(original.bitrate, modified.bitrate)
        assertEquals(original.sampleRate, modified.sampleRate)
        assertEquals(original.channelLayout, modified.channelLayout)
    }

    @Test
    fun `streaming buffer health text includes streaming suffix`() {
        val stats = PlayerStats(bufferHealth = "12s", isStreaming = true)
        val displayText = if (stats.isStreaming) stats.bufferHealth + " (streaming)" else stats.bufferHealth + " (local)"
        assertEquals("12s (streaming)", displayText)
    }

    @Test
    fun `local buffer health text includes local suffix`() {
        val stats = PlayerStats(bufferHealth = "8s", isStreaming = false)
        val displayText = if (stats.isStreaming) stats.bufferHealth + " (streaming)" else stats.bufferHealth + " (local)"
        assertEquals("8s (local)", displayText)
    }

    @Test
    fun `dropped frames zero means no dropped frames row shown`() {
        val stats = PlayerStats(droppedFrames = 0)
        val showDropped = stats.droppedFrames > 0
        assertFalse(showDropped)
    }

    @Test
    fun `dropped frames positive means dropped frames row shown`() {
        val stats = PlayerStats(droppedFrames = 42)
        val showDropped = stats.droppedFrames > 0
        assertTrue(showDropped)
        assertEquals("42", stats.droppedFrames.toString())
    }

    @Test
    fun `channel layout mapping`() {
        assertEquals("Mono", mapChannelLayout(1))
        assertEquals("Stereo", mapChannelLayout(2))
        assertEquals("5.1", mapChannelLayout(6))
        assertEquals("7.1", mapChannelLayout(8))
        assertEquals("Unknown", mapChannelLayout(0))
        assertEquals("Unknown", mapChannelLayout(3))
    }

    @Test
    fun `bitrate formatting`() {
        assertEquals("128 kbps", formatBitrate(128000))
        assertEquals("320 kbps", formatBitrate(320000))
        assertEquals("0 kbps", formatBitrate(0))
    }

    @Test
    fun `sample rate formatting`() {
        assertEquals("44.1 kHz", formatSampleRate(44100))
        assertEquals("48.0 kHz", formatSampleRate(48000))
        assertEquals("22.1 kHz", formatSampleRate(22050))
    }

    @Test
    fun `audio session id fallback for unset`() {
        assertEquals("None", formatAudioSessionId(-1))
        assertEquals("None", formatAudioSessionId(-2147483648))
        assertEquals("42", formatAudioSessionId(42))
    }

    private fun mapChannelLayout(channelCount: Int): String =
        when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "Unknown"
        }

    private fun formatBitrate(bitrate: Int): String = "${bitrate / 1000} kbps"

    private fun formatSampleRate(rate: Int): String = String.format(Locale.US, "%.1f kHz", rate / 1000.0)

    private fun formatAudioSessionId(sessionId: Int): String = if (sessionId < 0) "None" else sessionId.toString()

    // --- Full stats row mapping scenarios ---

    @Test
    fun `MP3 128 stereo stats rows`() {
        val stats =
            PlayerStats(
                audioFormat = "audio/mpeg 128kbps",
                bitrate = "128 kbps",
                sampleRate = "44.1 kHz",
                channelLayout = "Stereo",
                bufferHealth = "10s",
                audioSessionId = "42",
                decoderName = "ExoPlayer Audio Decoder",
                droppedFrames = 0,
                isStreaming = false,
            )
        assertEquals("audio/mpeg 128kbps", stats.audioFormat)
        assertEquals("Stereo", stats.channelLayout)
        assertEquals("10s (local)", stats.bufferHealth + " (local)")
        assertEquals("42", stats.audioSessionId)
        assertFalse(stats.droppedFrames > 0)
    }

    @Test
    fun `FLAC lossless stats rows`() {
        val stats =
            PlayerStats(
                audioFormat = "audio/flac 876kbps",
                bitrate = "876 kbps",
                sampleRate = "44.1 kHz",
                channelLayout = "Stereo",
                bufferHealth = "20s",
                audioSessionId = "7",
                decoderName = "ExoPlayer Audio Decoder",
                droppedFrames = 0,
                isStreaming = false,
            )
        assertEquals("audio/flac 876kbps", stats.audioFormat)
        assertEquals("876 kbps", stats.bitrate)
        assertEquals("20s (local)", stats.bufferHealth + " (local)")
    }

    @Test
    fun `M4B AAC streaming stats rows`() {
        val stats =
            PlayerStats(
                audioFormat = "audio/mp4 256kbps",
                bitrate = "256 kbps",
                sampleRate = "48.0 kHz",
                channelLayout = "Stereo",
                bufferHealth = "5s",
                audioSessionId = "15",
                decoderName = "ExoPlayer Audio Decoder",
                droppedFrames = 0,
                isStreaming = true,
            )
        assertEquals("audio/mp4 256kbps", stats.audioFormat)
        assertEquals("5s (streaming)", stats.bufferHealth + " (streaming)")
        assertTrue(stats.isStreaming)
    }

    @Test
    fun `mono low quality stats rows`() {
        val stats =
            PlayerStats(
                audioFormat = "audio/mpeg 32kbps",
                bitrate = "32 kbps",
                sampleRate = "22.1 kHz",
                channelLayout = "Mono",
                bufferHealth = "3s",
                audioSessionId = "3",
                decoderName = "ExoPlayer Audio Decoder",
                droppedFrames = 0,
                isStreaming = false,
            )
        assertEquals("Mono", stats.channelLayout)
        assertEquals("32 kbps", stats.bitrate)
        assertEquals("22.1 kHz", stats.sampleRate)
    }

    @Test
    fun `5 surround sound stats rows`() {
        val stats =
            PlayerStats(
                audioFormat = "audio/flac 1411kbps",
                bitrate = "1411 kbps",
                sampleRate = "48.0 kHz",
                channelLayout = "5.1",
                bufferHealth = "30s",
                audioSessionId = "99",
                decoderName = "ExoPlayer Audio Decoder",
                droppedFrames = 2,
                isStreaming = false,
            )
        assertEquals("5.1", stats.channelLayout)
        assertEquals("1411 kbps", stats.bitrate)
        assertTrue(stats.droppedFrames > 0)
        assertEquals("2", stats.droppedFrames.toString())
    }

    @Test
    fun `unknown format fallback stats rows`() {
        val stats = PlayerStats()
        assertEquals("Unknown", stats.audioFormat)
        assertEquals("Unknown", stats.bitrate)
        assertEquals("Unknown", stats.sampleRate)
        assertEquals("Unknown", stats.channelLayout)
        assertEquals("Unknown", stats.audioSessionId)
        assertEquals("Unknown", stats.decoderName)
        assertEquals("0s", stats.bufferHealth)
        assertEquals(0, stats.droppedFrames)
    }

    @Test
    fun `PlayerStats with audioQuality preserves quality info`() {
        val quality =
            com.jabook.app.jabook.audio.AudioQualityInfo(
                format = "FLAC",
                bitrateKbps = 876,
                sampleRateHz = 44100,
                channels = 2,
                isLossless = true,
            )
        val stats =
            PlayerStats(
                audioFormat = "FLAC",
                bitrate = "876 kbit/s",
                sampleRate = "44100 Hz",
                channelLayout = "Stereo",
                audioQuality = quality,
            )
        assertEquals(quality, stats.audioQuality)
        assertEquals("FLAC", stats.audioQuality!!.format)
        assertEquals(876, stats.audioQuality!!.bitrateKbps)
        assertTrue(stats.audioQuality!!.isLossless)
    }

    @Test
    fun `PlayerStats copy preserves audioQuality`() {
        val quality =
            com.jabook.app.jabook.audio.AudioQualityInfo(
                format = "MP3",
                bitrateKbps = 128,
                sampleRateHz = 44100,
                channels = 2,
                isLossless = false,
            )
        val stats = PlayerStats(audioQuality = quality)
        val copy = stats.copy(droppedFrames = 5)
        assertEquals(quality, copy.audioQuality)
        assertEquals(5, copy.droppedFrames)
    }

    @Test
    fun `PlayerStats equality includes audioQuality`() {
        val quality =
            com.jabook.app.jabook.audio.AudioQualityInfo(
                format = "MP3",
                bitrateKbps = 320,
                sampleRateHz = 44100,
                channels = 2,
                isLossless = false,
            )
        val a = PlayerStats(audioQuality = quality)
        val b = PlayerStats(audioQuality = quality)
        assertEquals(a, b)
        val c = PlayerStats(audioQuality = null)
        assertNotEquals(a, c)
    }
}
