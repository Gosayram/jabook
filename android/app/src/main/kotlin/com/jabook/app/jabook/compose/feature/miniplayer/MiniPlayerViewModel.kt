// Copyright 2025 Jabook Contributors
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
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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
class MiniPlayerViewModel
    @Inject
    constructor(
        private val audioPlayerController: AudioPlayerController,
    ) : ViewModel() {
        /**
         * Current playback state (playing/paused).
         */
        val isPlaying: StateFlow<Boolean> = audioPlayerController.isPlaying

        /**
         * Current playback position in milliseconds.
         */
        val currentPosition: StateFlow<Long> = audioPlayerController.currentPosition

        /**
         * Total duration of current track in milliseconds.
         */
        val duration: StateFlow<Long> = audioPlayerController.duration

        /**
         * Current chapter/track index in playlist.
         */
        val currentChapterIndex: StateFlow<Int> = audioPlayerController.currentChapterIndex

        /**
         * Play current track.
         */
        fun play() {
            audioPlayerController.play()
        }

        /**
         * Pause current track.
         */
        fun pause() {
            audioPlayerController.pause()
        }

        /**
         * Toggle play/pause.
         */
        fun togglePlayPause() {
            if (isPlaying.value) {
                pause()
            } else {
                play()
            }
        }
    }
