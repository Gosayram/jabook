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

internal enum class PreloadDecision {
    PRELOAD,
    SKIP_NO_PATHS,
    SKIP_OUT_OF_BOUNDS,
    SKIP_ALREADY_LOADED,
}

internal object PlaylistPreloadPolicy {
    internal fun decide(
        playlistSize: Int?,
        targetIndex: Int,
        alreadyLoaded: Boolean,
    ): PreloadDecision {
        val size = playlistSize ?: return PreloadDecision.SKIP_NO_PATHS
        if (targetIndex < 0 || targetIndex >= size) {
            return PreloadDecision.SKIP_OUT_OF_BOUNDS
        }
        if (alreadyLoaded) {
            return PreloadDecision.SKIP_ALREADY_LOADED
        }
        return PreloadDecision.PRELOAD
    }

    internal fun shouldAttachAfterBuild(stillNeeded: Boolean): Boolean = stillNeeded
}
