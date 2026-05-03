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

import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal sealed class PlaylistPreloadExecutionResult {
    data object Attached : PlaylistPreloadExecutionResult()

    data object SkippedAlreadyAvailable : PlaylistPreloadExecutionResult()

    data class Failed(
        val error: Exception,
    ) : PlaylistPreloadExecutionResult()
}

internal class PlaylistPreloadExecutor(
    private val mainDispatcher: CoroutineDispatcher,
) {
    internal suspend fun execute(
        buildMediaSource: suspend () -> MediaSource,
        shouldAttachOnMain: () -> Boolean,
        attachOnMain: (MediaSource) -> Unit,
    ): PlaylistPreloadExecutionResult =
        try {
            val mediaSource = buildMediaSource()
            withContext(mainDispatcher) {
                if (shouldAttachOnMain()) {
                    attachOnMain(mediaSource)
                    PlaylistPreloadExecutionResult.Attached
                } else {
                    PlaylistPreloadExecutionResult.SkippedAlreadyAvailable
                }
            }
        } catch (e: Exception) {
            PlaylistPreloadExecutionResult.Failed(e)
        }
}
