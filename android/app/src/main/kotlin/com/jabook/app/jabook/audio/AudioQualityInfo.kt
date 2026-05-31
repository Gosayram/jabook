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

/**
 * P-65: Audio quality metadata extracted from ExoPlayer [Format].
 *
 * Displayed to the user as a chip in PlayerScreen so they know
 * whether they're listening to 64 kbit/s MP3 or 320 kbit/s FLAC.
 */
public data class AudioQualityInfo(
    val format: String,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val isLossless: Boolean,
) {
    /**
     * Human-readable label for UI display.
     * E.g. "FLAC · 876 кбит/с · Lossless" or "MP3 · 128 кбит/с"
     */
    public fun toLabel(): String =
        buildString {
            append(format)
            bitrateKbps?.let { append(" · $it кбит/с") }
            if (isLossless) append(" · Lossless")
        }

    public companion object {
        /**
         * Extracts quality info from an ExoPlayer [Format].
         */
        public fun fromFormat(format: Format): AudioQualityInfo {
            val mime = format.sampleMimeType.orEmpty()
            val containerMime = format.containerMimeType.orEmpty()
            val fmt =
                when {
                    mime.contains("flac", ignoreCase = true) -> "FLAC"
                    mime.contains("vorbis", ignoreCase = true) -> "OGG"
                    mime.contains("opus", ignoreCase = true) -> "Opus"
                    mime.contains("aac", ignoreCase = true) -> "AAC"
                    mime.contains("mp4a", ignoreCase = true) -> "AAC"
                    containerMime.contains("mp4", ignoreCase = true) -> "M4B"
                    mime.contains("mpeg", ignoreCase = true) ||
                        mime.contains("mp3", ignoreCase = true) -> "MP3"
                    else -> "Audio"
                }
            val lossless = mime.contains("flac", ignoreCase = true)
            return AudioQualityInfo(
                format = fmt,
                bitrateKbps = format.bitrate.takeIf { it > 0 }?.let { it / 1000 },
                sampleRateHz = format.sampleRate.takeIf { it > 0 },
                channels = format.channelCount.takeIf { it > 0 },
                isLossless = lossless,
            )
        }
    }
}
