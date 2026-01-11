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
public data class MediaInfo(
    /** General information about the media file */
    public val general: GeneralInfo,
    /** List of video tracks */
    public val video: List<VideoTrack> = emptyList(),
    /** List of audio tracks */
    public val audio: List<AudioTrack> = emptyList(),
    /** List of subtitle/text tracks */
    public val text: List<SubtitleTrack> = emptyList(),
)

/**
 * General information about a media file.
 */
public data class GeneralInfo(
    /** Container format (e.g., "Matroska", "MP4") */
    public val format: String?,
    /** Total duration (e.g., "9 м. 34 с.", "00:09:34") */
    public val duration: String?,
    /** File size in human-readable format (e.g., "290 МБайт") */
    public val fileSize: String?,
    /** Bitrate (e.g., "4 241 Кбит/сек") */
    public val bitrate: String?,
    /** Container/file name */
    public val title: String?,
)

/**
 * Video track information.
 */
public data class VideoTrack(
    /** Video codec (e.g., "AVC", "H.264", "MPEG-4") */
    public val codec: String?,
    /** Codec profile (e.g., "High@L4") */
    public val profile: String?,
    /** Resolution (e.g., "1920 x 1080", "1080p") */
    public val resolution: String?,
    /** Frame rate (e.g., "25.000 кадров/сек", "25 fps") */
    public val frameRate: String?,
    /** Video bitrate (e.g., "4 000 Кбит/сек") */
    public val bitrate: String?,
    /** Aspect ratio (e.g., "16:9") */
    public val aspectRatio: String?,
    /** Color space (e.g., "YUV") */
    public val colorSpace: String?,
)

/**
 * Audio track information.
 */
public data class AudioTrack(
    /** Audio codec (e.g., "AAC", "MP3", "AC3") */
    public val codec: String?,
    /** Codec info (e.g., "Advanced Audio Codec Low Complexity") */
    public val codecInfo: String?,
    /** Audio bitrate (e.g., "241 Кбит/сек") */
    public val bitrate: String?,
    /** Number of channels (e.g., "2 канала", "Stereo", "5.1") */
    public val channels: String?,
    /** Sampling rate (e.g., "48 Гц", "48000 Hz") */
    public val samplingRate: String?,
    /** Audio language (e.g., "Русский", "English") */
    public val language: String?,
    /** Track title/name */
    public val title: String?,
)

/**
 * Subtitle/text track information.
 */
public data class SubtitleTrack(
    /** Subtitle format (e.g., "UTF-8", "SRT") */
    public val format: String?,
    /** Subtitle language (e.g., "Русский", "English") */
    public val language: String?,
    /** Track title/name */
    public val title: String?,
    /** Whether subtitles are forced */
    public val forced: Boolean = false,
)
