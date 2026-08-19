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

package com.jabook.app.jabook.compose.feature.player

import com.jabook.app.jabook.audio.PlaylistItem
import com.jabook.app.jabook.audio.processors.SpeedMemoryHierarchy
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Loads the current book into the player controller and resolves the hierarchical playback speed.
 *
 * @param bookId Current book identifier
 * @param playerController Controller receiving the loaded book
 * @param userPreferencesRepository Repository for the global speed preference
 * @param booksRepository Repository for per-book speed resolution
 * @param restoredBootstrapSnapshot Bootstrap snapshot for restored-speed detection
 * @param viewModelScope Coroutine scope for async operations
 * @param loggerFactory Logger factory
 * @param applyPlaybackSpeed Callback applying a resolved speed to the player
 */
internal class PlayerPlaybackBootstrapHandler(
    private val bookId: String,
    private val playerController: AudioPlayerController,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val booksRepository: BooksRepository,
    private val restoredBootstrapSnapshot: StateFlow<RestoredBootstrapSnapshot?>,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
    private val applyPlaybackSpeed: (Float) -> Unit,
) {
    private val logger: Logger = loggerFactory.get("PlayerPlaybackBootstrapHandler")

    /**
     * Loads [state]'s chapters into the controller; when [resolveHierarchicalSpeed] is set,
     * also applies the per-book/global speed hierarchy (initialization path).
     */
    fun loadBookForPlayback(
        state: PlayerState.Active,
        autoPlay: Boolean,
        resolveHierarchicalSpeed: Boolean,
    ) {
        val playlistItems =
            state.chapters.mapNotNull { chapter ->
                chapter.fileUrl?.let { path ->
                    PlaylistItem(path, chapter.id, chapter.startMs, chapter.endMs)
                }
            }
        val filePaths = playlistItems.map(PlaylistItem::path)
        if (filePaths.isEmpty()) return

        val currentPositionMs = playerController.currentPosition.value

        if (resolveHierarchicalSpeed) {
            // Single source-of-truth: initialize from unified uiState (controller/service-driven
            // when bound, DB-restored only as bootstrap fallback before controller binds).
            logger.d {
                "Initializing player: chapter=${state.currentChapterIndex}, position=${currentPositionMs}ms"
            }
        }

        playerController.loadBook(
            filePaths = filePaths,
            playlistItems = playlistItems,
            initialChapterIndex = state.currentChapterIndex,
            initialPosition = currentPositionMs,
            autoPlay = autoPlay,
            metadata =
                mapOf(
                    "title" to state.book.title,
                    "author" to state.book.author,
                    "bookTitle" to state.book.title, // For fallback
                    "artist" to state.book.author, // For fallback
                ),
            bookId = bookId,
        )

        if (resolveHierarchicalSpeed) {
            val shouldSkipHierarchicalSpeedApply = restoredBootstrapSnapshot.value?.hasRestoredSpeed ?: false
            if (!shouldSkipHierarchicalSpeedApply) {
                viewModelScope.launch {
                    runCatching {
                        val globalSpeed = userPreferencesRepository.userData.first().playbackSpeed
                        val resolvedSpeed =
                            booksRepository.resolvePreferredPlaybackSpeed(
                                bookId = bookId,
                                globalSpeed = globalSpeed,
                            )
                        if (SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(globalSpeed, resolvedSpeed)) {
                            applyPlaybackSpeed(resolvedSpeed)
                        }
                    }.onFailure { error ->
                        logger.w(error) { "Failed to resolve hierarchical playback speed for book" }
                    }
                }
            }
        }
    }
}
