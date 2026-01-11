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

package com.jabook.app.jabook.compose.feature.miniplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight ViewModel wrapper for mini-player state management.
 *
 * This ViewModel exists to bridge the gap between AudioPlayerController (@Singleton)
 * and Composable UI which expects ViewModels from hiltViewModel().
 *
 * It simply exposes the AudioPlayerController state flows without navigation dependencies,
 * making it safe to instantiate at the app root level.
 */
@HiltViewModel
public class MiniPlayerViewModel
    @Inject
    constructor(
        private val audioPlayerController: AudioPlayerController,
        private val playerPersistenceManager: com.jabook.app.jabook.audio.PlayerPersistenceManager,
        private val booksRepository: com.jabook.app.jabook.compose.data.repository.BooksRepository,
    ) : ViewModel() {
        /**
         * Current playback state (playing/paused).
         */
        public val isPlaying: StateFlow<Boolean> = audioPlayerController.isPlaying

        /**
         * Current playback position in milliseconds.
         */
        public val currentPosition: StateFlow<Long> = audioPlayerController.currentPosition

        /**
         * Total duration of current track in milliseconds.
         */
        public val duration: StateFlow<Long> = audioPlayerController.duration

        /**
         * Current chapter/track index in playlist.
         */
        public val currentChapterIndex: StateFlow<Int> = audioPlayerController.currentChapterIndex

        /**
         * Current book being played (from PlayerPersistenceManager + BooksRepository).
         * This is the last played book from persistence.
         */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        public val currentBook: StateFlow<Book?> =
            playerPersistenceManager.lastPlayedBookId
                .flatMapLatest { bookId ->
                    if (bookId != null) {
                        booksRepository.getBook(bookId)
                    } else {
                        flowOf(null)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        /**
         * Play current track.
         */
        public fun play() : Unit {
            audioPlayerController.play()
        }

        /**
         * Pause current track.
         */
        public fun pause() : Unit {
            audioPlayerController.pause()
        }

        /**
         * Toggle play/pause.
         */
        public fun togglePlayPause() : Unit {
            if (isPlaying.value) {
                pause()
            } else {
                play()
            }
        }

        /**
         * Skip to next track.
         */
        public fun skipToNext() : Unit {
            audioPlayerController.skipToNext()
        }

        /**
         * Skip to previous track.
         */
        public fun skipToPrevious() : Unit {
            audioPlayerController.skipToPrevious()
        }
    }
