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
    internal fun resolve(metadata: Map<String, String>?): PlaylistResolvedMetadataFields =
        PlaylistResolvedMetadataFields(
            title = metadata?.get("title") ?: metadata?.get("trackTitle"),
            artist = metadata?.get("artist") ?: metadata?.get("author"),
            album = metadata?.get("album") ?: metadata?.get("bookTitle"),
        )
}
