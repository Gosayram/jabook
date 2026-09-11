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

import android.net.Uri

internal data class PlaylistResolvedUri(
    val uri: Uri,
    val shouldWarnMissingLocalPath: Boolean,
)

/**
 * Resolves a playlist file path into a playback [Uri].
 *
 * Spotube-style pre-resolution/warming of the first remote stream before
 * `player.prepare()` is NOT needed here: resolution is synchronous and local-only.
 * [buildPlaybackUri] maps file/http/content strings to a [Uri] with no network I/O,
 * and both playlist load paths fully build the first MediaSource before calling
 * `prepare()` (PlaylistManager.preparePlaybackSynchronous / preparePlaybackAsync).
 * There is no async remote-URL resolution stage that could leave the player
 * preparing against an unresolved URI.
 */
internal object PlaylistUriResolutionPolicy {
    internal fun resolve(
        path: String,
        localPathExists: (String) -> Boolean,
    ): PlaylistResolvedUri {
        val uri = buildPlaybackUri(path)
        val shouldWarnMissingLocalPath =
            PlaylistUriValidationPolicy.shouldWarnMissingLocalPath(
                scheme = uri.scheme,
                localPath = uri.path,
                pathExists = localPathExists,
            )
        return PlaylistResolvedUri(
            uri = uri,
            shouldWarnMissingLocalPath = shouldWarnMissingLocalPath,
        )
    }
}
