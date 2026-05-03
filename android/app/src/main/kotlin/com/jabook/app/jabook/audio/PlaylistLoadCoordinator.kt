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

internal class PlaylistLoadCoordinator(
    private val isLoading: () -> Boolean,
    private val setLoading: (Boolean) -> Unit,
    private val setCurrentLoadingPlaylist: (List<String>?) -> Unit,
    private val setLastLoadTimestampMs: (Long) -> Unit,
    private val cancelAndClearActiveLoadingJob: () -> Unit,
    private val nextGeneration: () -> Long,
) {
    internal fun beginOrSkip(filePaths: List<String>): Long? {
        if (isLoading()) {
            return null
        }
        cancelAndClearActiveLoadingJob()
        setLoading(true)
        setCurrentLoadingPlaylist(filePaths)
        setLastLoadTimestampMs(System.currentTimeMillis())
        return nextGeneration()
    }

    internal fun finish() {
        setLoading(false)
        setCurrentLoadingPlaylist(null)
        cancelAndClearActiveLoadingJob()
    }

    internal fun fail() {
        finish()
    }
}
