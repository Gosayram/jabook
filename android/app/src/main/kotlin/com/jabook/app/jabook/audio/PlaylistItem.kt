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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A playable source; a chapter in one M4B is represented by its clip window. */
@Serializable
public data class PlaylistItem(
    @SerialName("path") val path: String,
    @SerialName("mediaId") val mediaId: String = path,
    @SerialName("clipStartPositionMs") val clipStartPositionMs: Long? = null,
    @SerialName("clipEndPositionMs") val clipEndPositionMs: Long? = null,
) {
    init {
        require(path.isNotBlank()) { "path must not be blank" }
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        require(clipStartPositionMs == null || clipStartPositionMs >= 0L) { "clip start must be non-negative" }
        require(clipEndPositionMs == null || clipEndPositionMs > (clipStartPositionMs ?: 0L)) {
            "clip end must be after clip start"
        }
    }
}
