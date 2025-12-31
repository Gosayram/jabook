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

package com.jabook.app.jabook.compose.data.remote.model

/**
 * Structured MediaInfo data extracted from Rutracker topic details.
 *
 * Replaces raw unformatted MediaInfo text with structured, usable data.
 */
data class MediaInfo(
    /** General information about the media file */
    val general: GeneralInfo,
    /** List of video tracks */
    val video: List<VideoTrack> = emptyList(),
    /** List of audio tracks */
    val audio: List<AudioTrack> = emptyList(),
    /** List of subtitle/text tracks */
    val text: List<SubtitleTrack> = emptyList(),
)

/**
 * General information about a media file.
 */
data class GeneralInfo(
    /** Container format (e.g., "Matroska", "MP4") */
    val format: String?,
    /** Total duration (e.g., "9 м. 34 с.", "00:09:34") */
    val duration: String?,
    /** File size in human-readable format (e.g., "290 МБайт") */
    val fileSize: String?,
    /** Bitrate (e.g., "4 241 Кбит/сек") */
    val bitrate: String?,
    /** Container/file name */
    val title: String?,
)

/**
 * Video track information.
 */
data class VideoTrack(
    /** Video codec (e.g., "AVC", "H.264", "MPEG-4") */
    val codec: String?,
    /** Codec profile (e.g., "High@L4") */
    val profile: String?,
    /** Resolution (e.g., "1920 x 1080", "1080p") */
    val resolution: String?,
    /** Frame rate (e.g., "25.000 кадров/сек", "25 fps") */
    val frameRate: String?,
    /** Video bitrate (e.g., "4 000 Кбит/сек") */
    val bitrate: String?,
    /** Aspect ratio (e.g., "16:9") */
    val aspectRatio: String?,
    /** Color space (e.g., "YUV") */
    val colorSpace: String?,
)

/**
 * Audio track information.
 */
data class AudioTrack(
    /** Audio codec (e.g., "AAC", "MP3", "AC3") */
    val codec: String?,
    /** Codec info (e.g., "Advanced Audio Codec Low Complexity") */
    val codecInfo: String?,
    /** Audio bitrate (e.g., "241 Кбит/сек") */
    val bitrate: String?,
    /** Number of channels (e.g., "2 канала", "Stereo", "5.1") */
    val channels: String?,
    /** Sampling rate (e.g., "48 Гц", "48000 Hz") */
    val samplingRate: String?,
    /** Audio language (e.g., "Русский", "English") */
    val language: String?,
    /** Track title/name */
    val title: String?,
)

/**
 * Subtitle/text track information.
 */
data class SubtitleTrack(
    /** Subtitle format (e.g., "UTF-8", "SRT") */
    val format: String?,
    /** Subtitle language (e.g., "Русский", "English") */
    val language: String?,
    /** Track title/name */
    val title: String?,
    /** Whether subtitles are forced */
    val forced: Boolean = false,
)
