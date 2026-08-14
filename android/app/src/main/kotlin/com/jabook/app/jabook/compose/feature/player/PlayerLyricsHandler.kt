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

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine
import com.jabook.app.jabook.data.lyrics.LyricsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Loads and exposes sidecar lyrics for the currently playing chapter.
 *
 * @param bookId Current book identifier
 * @param getChaptersUseCase Use case for retrieving book chapters
 * @param lyricsRepository Repository for sidecar lyrics lookup
 * @param playerController Controller exposing the current chapter index
 * @param viewModelScope Coroutine scope for collectors
 * @param loggerFactory Logger factory
 */
internal class PlayerLyricsHandler(
    private val bookId: String,
    private val getChaptersUseCase: GetChaptersUseCase,
    private val lyricsRepository: LyricsRepository,
    private val playerController: AudioPlayerController,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
) {
    private val logger: Logger = loggerFactory.get("PlayerLyricsHandler")

    // Store lyrics in a separate flow to avoid re-parsing on every seeking
    private val _lyricsState = MutableStateFlow<ImmutableList<LyricLine>?>(null)

    /** Lyrics for the current chapter, or null when unavailable. */
    val lyricsState: StateFlow<ImmutableList<LyricLine>?> = _lyricsState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe() {
        viewModelScope.launch {
            combine(
                getChaptersUseCase(bookId).map(::sortChaptersForPlayback),
                playerController.currentChapterIndex,
            ) { chapters, index ->
                chapters.getOrNull(index)?.fileUrl
            }.distinctUntilChanged()
                .flatMapLatest { fileUrl ->
                    if (fileUrl.isNullOrBlank()) {
                        flowOf<ImmutableList<LyricLine>?>(null)
                    } else {
                        flow<ImmutableList<LyricLine>?> {
                            emit(loadLyricsOrNull(fileUrl))
                        }
                    }
                }.collect { lyrics ->
                    _lyricsState.value = lyrics
                }
        }
    }

    private suspend fun loadLyricsOrNull(audioPath: String): ImmutableList<LyricLine>? {
        try {
            // Sidecar lyrics are optional.
            val lyrics = lyricsRepository.getLyrics(audioPath)
            return if (lyrics.isNotEmpty()) lyrics.toImmutableList() else null
        } catch (e: Exception) {
            logger.e({ "Failed to load lyrics" }, e)
            return null
        }
    }
}
