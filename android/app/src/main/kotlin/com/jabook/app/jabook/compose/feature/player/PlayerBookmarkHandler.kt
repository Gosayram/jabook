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

import android.content.Context
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.repository.BookmarkRepository
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * P-92: Bookmark operations extracted from PlayerViewModel.
 *
 * Handles add/update/delete bookmark operations with error reporting.
 *
 * @param bookmarkRepository Repository for bookmark persistence
 * @param uiState Current player UI state
 * @param bookmarks Current bookmarks list
 * @param viewModelScope Coroutine scope for async operations
 * @param logger Logger instance
 * @param reportError Callback to report errors to the UI
 */
internal class PlayerBookmarkHandler(
    private val bookmarkRepository: BookmarkRepository,
    private val uiState: StateFlow<PlayerState>,
    private val bookmarks: StateFlow<List<BookmarkItem>>,
    private val playerController: com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController,
    private val viewModelScope: CoroutineScope,
    private val loggerFactory: LoggerFactory,
    private val context: Context,
    private val reportError: (String) -> Unit,
) {
    private val logger: Logger = loggerFactory.get("PlayerBookmarkHandler")

    fun addBookmarkAtCurrentPosition(noteText: String? = null) {
        val state = uiState.value as? PlayerState.Active ?: return
        val chapterDurationMs = state.currentChapter?.duration?.inWholeMilliseconds ?: 0L
        val positionMs = playerController.currentPosition.value
        viewModelScope.launch {
            bookmarkRepository
                .addBookmark(
                    bookId = state.book.id,
                    chapterIndex = state.currentChapterIndex,
                    positionMs = positionMs,
                    noteText = noteText,
                    chapterDurationMs = chapterDurationMs,
                ).onFailure { error ->
                    logger.e({ "Failed to add bookmark" }, error)
                    reportError(context.getString(R.string.failed_to_add_bookmark))
                }
        }
    }

    fun addBookmarkAtPosition(
        chapterIndex: Int,
        positionMs: Long,
        noteText: String? = null,
        onCreated: (BookmarkItem?) -> Unit = {},
    ) {
        val state = uiState.value as? PlayerState.Active ?: return
        val chapterDurationMs =
            state.chapters
                .getOrNull(chapterIndex)
                ?.duration
                ?.inWholeMilliseconds ?: 0L
        viewModelScope.launch {
            val result =
                bookmarkRepository.addBookmark(
                    bookId = state.book.id,
                    chapterIndex = chapterIndex,
                    positionMs = positionMs,
                    noteText = noteText,
                    chapterDurationMs = chapterDurationMs,
                )
            result
                .onSuccess { bookmark -> onCreated(bookmark) }
                .onFailure { error ->
                    logger.e({ "Failed to add bookmark at custom position" }, error)
                    reportError(context.getString(R.string.failed_to_add_bookmark))
                    onCreated(null)
                }
        }
    }

    fun updateBookmarkContent(
        bookmarkId: String,
        noteText: String?,
        noteAudioPath: String? = null,
    ) {
        val existing = bookmarks.value.firstOrNull { it.id == bookmarkId } ?: return
        viewModelScope.launch {
            bookmarkRepository
                .updateBookmark(
                    existing.copy(
                        noteText = noteText?.takeIf { it.isNotBlank() },
                        noteAudioPath = noteAudioPath ?: existing.noteAudioPath,
                    ),
                ).onFailure { error ->
                    logger.e({ "Failed to update bookmark note" }, error)
                    reportError(context.getString(R.string.failed_to_update_bookmark))
                }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            val deleteResult = bookmarkRepository.deleteBookmark(bookmarkId)
            val deleteFailureReason = resolveDeleteBookmarkFailureReason(deleteResult)
            if (deleteFailureReason != null) {
                logger.e({ deleteFailureReason }, deleteResult.exceptionOrNull())
                reportError(deleteFailureReason)
            }
        }
    }

    private fun resolveDeleteBookmarkFailureReason(result: Result<Unit>): String? =
        result.exceptionOrNull()?.let { "${context.getString(R.string.failed_to_delete_bookmark)}: ${it.message}" }
}
