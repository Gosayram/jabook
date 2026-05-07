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

internal data class PlaylistResolvedMetadataFields(
    val title: String?,
    val artist: String?,
    val album: String?,
)

internal object PlaylistMetadataFieldPolicy {
    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun pick(
        metadata: Map<String, String>?,
        primary: String,
        fallback: String,
    ): String? = metadata?.get(primary).normalizedOrNull() ?: metadata?.get(fallback).normalizedOrNull()

    internal fun resolve(metadata: Map<String, String>?): PlaylistResolvedMetadataFields =
        PlaylistResolvedMetadataFields(
            title = pick(metadata, primary = "title", fallback = "trackTitle"),
            artist = pick(metadata, primary = "artist", fallback = "author"),
            album = pick(metadata, primary = "album", fallback = "bookTitle"),
        )
}
