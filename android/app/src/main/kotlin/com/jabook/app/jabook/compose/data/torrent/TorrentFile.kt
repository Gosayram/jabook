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

package com.jabook.app.jabook.compose.data.torrent

/**
 * Individual file within a torrent
 */
public data class TorrentFile(
    /** File index in torrent */
    val index: Int,
    /** Relative path within torrent */
    val path: String,
    /** File size in bytes */
    val size: Long,
    /** Download priority (0-7, 0=don't download) */
    val priority: Int = 4,
    /** Download progress (0.0-1.0) */
    val progress: Float = 0f,
    /** Is this file selected for download */
    val isSelected: Boolean = true,
) {
    /** File name without path */
    val name: String
        get() = path.substringAfterLast('/')

    /** File extension */
    val extension: String
        get() = path.substringAfterLast('.', "")

    /** Is audio file */
    val isAudioFile: Boolean
        get() = extension.lowercase() in AUDIO_EXTENSIONS

    public companion object {
        private val AUDIO_EXTENSIONS =
            setOf(
                "mp3",
                "m4a",
                "m4b",
                "aac",
                "flac",
                "ogg",
                "opus",
                "wav",
                "wma",
            )
    }
}
