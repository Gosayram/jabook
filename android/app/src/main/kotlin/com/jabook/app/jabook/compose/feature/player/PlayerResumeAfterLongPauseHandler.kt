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

import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Detects resumes after a long pause (7+ days) and drives the recap dialog state (TASK-PLAYER-38).
 *
 * Threshold ordering invariant: this 7-day threshold sits far above the
 * InactivityTimer unload window (10–180 min). Any pause long enough to trigger
 * this dialog has already caused the inactivity unload (with position save), so
 * the dialog is only ever raised from a [PlayerState.Active] produced by a fresh
 * (cold) load — it never acts on an unloaded player.
 *
 * @param bookId Current book identifier
 * @param uiState Current player UI state
 * @param listeningSessionRepository Repository for the last listening timestamp
 * @param playerController Controller for chapter restart actions
 * @param viewModelScope Coroutine scope for collectors
 */
internal class PlayerResumeAfterLongPauseHandler(
    private val bookId: String,
    private val uiState: StateFlow<PlayerState>,
    private val listeningSessionRepository: ListeningSessionRepository,
    private val playerController: AudioPlayerController,
    private val viewModelScope: CoroutineScope,
) {
    private var hasShownResumeAfterLongPause: Boolean = false

    private val _resumeAfterLongPauseState = MutableStateFlow<PlayerViewModel.ResumeAfterLongPauseData?>(null)

    /** Dialog data for the resume-after-long-pause prompt, or null when hidden. */
    val resumeAfterLongPauseState: StateFlow<PlayerViewModel.ResumeAfterLongPauseData?> =
        _resumeAfterLongPauseState.asStateFlow()

    fun observe() {
        viewModelScope.launch {
            uiState.collect { state ->
                val activeState = state as? PlayerState.Active ?: return@collect
                if (hasShownResumeAfterLongPause) return@collect
                val lastTimestamp = listeningSessionRepository.getLastListeningTimestamp(bookId) ?: return@collect
                val daysAgo =
                    java.util.concurrent.TimeUnit.MILLISECONDS
                        .toDays(System.currentTimeMillis() - lastTimestamp)
                        .toInt()
                if (daysAgo < 7) return@collect
                hasShownResumeAfterLongPause = true
                val chapter = activeState.currentChapter
                val chapterName = chapter?.title ?: (chapter?.displayNumber?.toString() ?: "—")
                val positionFormatted = PlayerTimeFormatter.formatDuration(playerController.currentPosition.value)
                _resumeAfterLongPauseState.value =
                    PlayerViewModel.ResumeAfterLongPauseData(
                        chapterName = chapterName,
                        chapterPosition = positionFormatted,
                        daysAgo = daysAgo,
                    )
            }
        }
    }

    fun dismiss() {
        _resumeAfterLongPauseState.value = null
    }

    fun continuePlayback() {
        _resumeAfterLongPauseState.value = null
    }

    fun restartChapter() {
        _resumeAfterLongPauseState.value = null
        val state = uiState.value as? PlayerState.Active ?: return
        playerController.skipToChapter(state.currentChapterIndex, 0L)
        viewModelScope.launch {
            delay(100L)
            playerController.play()
        }
    }

    fun selectChapter() {
        _resumeAfterLongPauseState.value = null
    }
}
