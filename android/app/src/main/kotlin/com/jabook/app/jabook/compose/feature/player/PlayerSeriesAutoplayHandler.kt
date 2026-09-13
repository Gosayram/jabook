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

import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

internal data class SeriesAutoplayDecision(
    val shouldTriggerAutoplay: Boolean,
    val shouldResetAutoplay: Boolean,
)

internal const val SERIES_AUTOPLAY_END_TOLERANCE_MS: Long = 750L

internal fun evaluateSeriesAutoplayDecision(
    isLastChapter: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasTriggeredSeriesAutoplay: Boolean,
): SeriesAutoplayDecision {
    val isTrackEnded = durationMs > 0L && positionMs >= (durationMs - SERIES_AUTOPLAY_END_TOLERANCE_MS)
    return SeriesAutoplayDecision(
        shouldTriggerAutoplay = isLastChapter && !isPlaying && isTrackEnded && !hasTriggeredSeriesAutoplay,
        shouldResetAutoplay = !isLastChapter || isPlaying || (hasTriggeredSeriesAutoplay && !isTrackEnded),
    )
}

/**
 * Series autoplay: detects end-of-book, finds the next book in the series, and drives the countdown.
 *
 * @param uiState Current player UI state
 * @param playerController Controller exposing playback flows
 * @param userPreferencesRepository Repository for the autoPlayNext preference
 * @param booksRepository Repository for series book lookup
 * @param viewModelScope Coroutine scope for collectors
 * @param navigateToBook Callback to navigate to the next book
 */
internal class PlayerSeriesAutoplayHandler(
    private val uiState: StateFlow<PlayerState>,
    private val playerController: AudioPlayerController,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val booksRepository: BooksRepository,
    private val viewModelScope: CoroutineScope,
    private val navigateToBook: (String) -> Unit,
) {
    private var hasTriggeredSeriesAutoplay: Boolean = false
    private var autoplayDismissedUntilChapterChange: Boolean = false
    private var seriesAutoplayJob: Job? = null

    private val _nextBookAutoplayState = MutableStateFlow<PlayerViewModel.NextBookAutoplayState?>(null)

    /** Countdown state for the next-book autoplay prompt, or null when hidden. */
    val nextBookAutoplayState: StateFlow<PlayerViewModel.NextBookAutoplayState?> =
        _nextBookAutoplayState.asStateFlow()

    private companion object {
        private const val POSITION_AUTOPLAY_EVAL_BUCKET_MS: Long = 250L
        private const val AUTOPLAY_COUNTDOWN_SECONDS: Int = 10
    }

    fun observeTrigger() {
        viewModelScope.launch {
            val throttledPositionFlow =
                playerController.currentPosition
                    .map { positionMs ->
                        val bucket = positionMs.coerceAtLeast(0L) / POSITION_AUTOPLAY_EVAL_BUCKET_MS
                        bucket * POSITION_AUTOPLAY_EVAL_BUCKET_MS
                    }.distinctUntilChanged()

            val autoPlayNextFlow = userPreferencesRepository.userData.map { it.autoPlayNext }

            combine(
                uiState,
                playerController.isPlaying,
                throttledPositionFlow,
                playerController.duration,
                autoPlayNextFlow,
            ) { state, isPlaying, positionMs, durationMs, autoPlayNext ->
                TriggerSeriesAutoplaySnapshot(
                    state = state,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    autoPlayNext = autoPlayNext,
                    hasTriggeredSeriesAutoplay = hasTriggeredSeriesAutoplay,
                )
            }.collect { snapshot ->
                val activeState = snapshot.state as? PlayerState.Active ?: return@collect
                val isLastChapter = activeState.currentChapterIndex >= (activeState.chapters.size - 1).coerceAtLeast(0)
                val autoplayDecision =
                    evaluateSeriesAutoplayDecision(
                        isLastChapter = isLastChapter,
                        isPlaying = snapshot.isPlaying,
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                        hasTriggeredSeriesAutoplay = snapshot.hasTriggeredSeriesAutoplay,
                    )

                if (autoplayDecision.shouldTriggerAutoplay && !autoplayDismissedUntilChapterChange && snapshot.autoPlayNext) {
                    hasTriggeredSeriesAutoplay = true
                    maybeStartSeriesAutoplay(activeState.book)
                } else if (autoplayDecision.shouldResetAutoplay || !snapshot.autoPlayNext) {
                    // Explicit dismiss should survive play/pause and near-end jitter
                    // until the user leaves the last chapter.
                    // Also reset if autoplay is disabled.
                    if (!isLastChapter) {
                        autoplayDismissedUntilChapterChange = false
                        hasTriggeredSeriesAutoplay = false
                    } else if (!autoplayDismissedUntilChapterChange && autoplayDecision.shouldResetAutoplay) {
                        hasTriggeredSeriesAutoplay = false
                    }
                    seriesAutoplayJob?.cancel()
                    seriesAutoplayJob = null
                    _nextBookAutoplayState.value = null
                }
            }
        }
    }

    fun continueNow() {
        val nextBook = _nextBookAutoplayState.value?.nextBook ?: return
        seriesAutoplayJob?.cancel()
        seriesAutoplayJob = null
        _nextBookAutoplayState.value = null
        autoplayDismissedUntilChapterChange = false
        navigateToBook(nextBook.id)
    }

    fun dismiss() {
        seriesAutoplayJob?.cancel()
        seriesAutoplayJob = null
        _nextBookAutoplayState.value = null
        hasTriggeredSeriesAutoplay = true
        autoplayDismissedUntilChapterChange = true
    }

    private fun maybeStartSeriesAutoplay(currentBook: Book) {
        seriesAutoplayJob?.cancel()
        seriesAutoplayJob =
            viewModelScope.launch {
                val allBooks = booksRepository.getAllBooks().first()
                val nextBook = findNextBookInSeries(currentBook, allBooks) ?: return@launch
                startAutoplayCountdown(nextBook)
            }
    }

    private suspend fun startAutoplayCountdown(nextBook: Book) {
        for (seconds in AUTOPLAY_COUNTDOWN_SECONDS downTo 0) {
            if (!currentCoroutineContext().isActive) return
            _nextBookAutoplayState.value =
                PlayerViewModel.NextBookAutoplayState(
                    nextBook = nextBook,
                    secondsLeft = seconds,
                    totalSeconds = AUTOPLAY_COUNTDOWN_SECONDS,
                )
            if (seconds > 0) delay(1_000L)
        }
        if (!currentCoroutineContext().isActive) return
        _nextBookAutoplayState.value = null
        navigateToBook(nextBook.id)
    }

    private fun findNextBookInSeries(
        currentBook: Book,
        allBooks: List<Book>,
    ): Book? {
        val currentDescriptor = parseSeriesDescriptor(currentBook) ?: return null
        return allBooks
            .asSequence()
            .filter { it.id != currentBook.id }
            .mapNotNull { candidate ->
                val descriptor = parseSeriesDescriptor(candidate) ?: return@mapNotNull null
                if (descriptor.seriesKey != currentDescriptor.seriesKey) return@mapNotNull null
                if (!candidate.author.equals(currentBook.author, ignoreCase = true)) return@mapNotNull null
                if (descriptor.order <= currentDescriptor.order) return@mapNotNull null
                descriptor.order to candidate
            }.minByOrNull { (order, _) -> order }
            ?.second
    }

    private data class SeriesDescriptor(
        val seriesKey: String,
        val order: Int,
    )

    private fun parseSeriesDescriptor(book: Book): SeriesDescriptor? {
        val normalizedTitle = book.title.trim()
        val patterns =
            listOf(
                Regex("""(?i)^(.*?)[\s\-–—:]*\b(?:book|книга|том|часть)\s*([0-9]{1,4})\b"""),
                Regex("""(?i)^(.*?)[\s\-–—:]*[#№]\s*([0-9]{1,4})\b"""),
            )
        for (pattern in patterns) {
            val match = pattern.find(normalizedTitle) ?: continue
            val rawKey =
                match.groupValues
                    .getOrNull(1)
                    .orEmpty()
                    .trim()
            val order = match.groupValues.getOrNull(2)?.toIntOrNull() ?: continue
            if (rawKey.isBlank()) continue
            return SeriesDescriptor(seriesKey = rawKey.lowercase(Locale.ROOT), order = order)
        }
        return null
    }

    private data class TriggerSeriesAutoplaySnapshot(
        val state: PlayerState,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val autoPlayNext: Boolean,
        val hasTriggeredSeriesAutoplay: Boolean,
    )
}
