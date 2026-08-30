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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages periodic position saving during playback.
 *
 * Extracts position persistence logic from AudioPlayerService.
 * Saves position every [intervalMs] while playback is active.
 *
 * @param scope Coroutine scope for launching save jobs
 * @param repository Repository for persisting playback positions
 * @param getActivePlayer Returns the current ExoPlayer instance
 * @param getCurrentBookId Returns the current book/group path (null if no book)
 * @param intervalMs Interval between saves in milliseconds (default 5s)
 */
internal class PeriodicPositionSaver(
    private val scope: CoroutineScope,
    private val repository: com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository,
    private val updateCanonicalProgress: suspend (bookId: String, position: Long, chapterIndex: Int) -> Unit,
    private val getActivePlayer: () -> androidx.media3.exoplayer.ExoPlayer,
    private val getCurrentBookId: () -> String?,
    private val intervalMs: Long = 5_000L,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private var saveJob: Job? = null
    private var lastSavedBookId: String? = null
    private var lastSavedTrackIndex: Int = -1
    private var lastSavedPositionMs: Long = -1L

    /** Starts periodic position saving. Cancels any previous job. */
    fun start() {
        saveJob?.cancel()
        saveJob =
            scope.launch {
                while (isActive) {
                    delay(intervalMs)
                    save(force = false)
                }
            }
    }

    /** Stops periodic position saving. */
    fun stop() {
        saveJob?.cancel()
        saveJob = null
    }

    /** Saves current position to repository immediately. */
    fun save() {
        save(force = true)
    }

    private fun save(force: Boolean) {
        val player = getActivePlayer()
        val bookId = getCurrentBookId()
        if (player.mediaItemCount > 0 && !bookId.isNullOrBlank()) {
            val trackIndex = player.currentMediaItemIndex
            val position = player.currentPosition
            // Paused/stalled playback reports the same spot every tick — skip the no-op write.
            if (!force &&
                bookId == lastSavedBookId &&
                trackIndex == lastSavedTrackIndex &&
                position == lastSavedPositionMs
            ) {
                return
            }
            lastSavedBookId = bookId
            lastSavedTrackIndex = trackIndex
            lastSavedPositionMs = position
            // The service cancels its scope immediately after onDestroy. This final
            // database write must survive that cancellation to retain progress.
            scope.launch(ioDispatcher + NonCancellable) {
                repository.savePosition(
                    bookId = bookId,
                    trackIndex = trackIndex,
                    position = position,
                )
                updateCanonicalProgress(bookId, position, trackIndex)
            }
        }
    }
}
