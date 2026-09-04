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

import com.jabook.app.jabook.compose.core.util.rethrowCancellation
import com.jabook.app.jabook.compose.data.repository.BookmarkRepository
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.abs

/**
 * P-57: Automatically creates bookmarks at key playback moments.
 *
 * Inspired by Audible's "Whispersync" — auto-bookmarks are created at:
 * - Chapter starts (for navigation history)
 * - Sleep timer stops (resume point)
 * - Phone call interruptions (resume point)
 * - Headphone disconnections (resume point)
 *
 * Deduplication: no auto-bookmark is created if another bookmark exists
 * within [DEDUPLICATE_WINDOW_MS] at the same position.
 *
 * @param bookmarkRepository Repository for persisting bookmarks
 */
public class AutoBookmarkTrigger
    @Inject
    constructor(
        private val bookmarkRepository: BookmarkRepository,
    ) {
        /**
         * Reason for auto-bookmark creation.
         */
        public enum class AutoBookmarkReason {
            CHAPTER_START,
            SLEEP_TIMER_STOP,
            PHONE_CALL_INTERRUPTED,
            HEADPHONES_REMOVED,
            LONG_PAUSE_RESUME,
            MANUAL,
        }

        // Bounded insertion-order cache: evicts the oldest entry past the cap.
        // Guarded by a Mutex because callers arrive from different coroutine contexts.
        private val recentBookmarksMutex = Mutex()
        private val recentBookmarks: LinkedHashMap<String, Long> =
            object : LinkedHashMap<String, Long>() {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_RECENT_BOOKMARKS
            }

        /**
         * Creates an auto-bookmark if no duplicate exists nearby.
         *
         * @param bookId Book identifier
         * @param positionMs Current playback position
         * @param chapterIndex Current chapter index
         * @param reason Why the bookmark is being created
         * @return true if bookmark was created, false if deduplicated
         */
        public suspend fun createAutoBookmark(
            bookId: String,
            positionMs: Long,
            chapterIndex: Int,
            reason: AutoBookmarkReason,
        ): Boolean =
            recentBookmarksMutex.withLock {
                val key = "$bookId:$chapterIndex"
                val lastPosition = recentBookmarks[key]

                if (lastPosition != null && abs(lastPosition - positionMs) < DEDUPLICATE_WINDOW_MS) {
                    LogUtils.d(TAG, "Auto-bookmark deduplicated: book=$bookId pos=${positionMs}ms reason=$reason")
                    return@withLock false
                }

                val label = generateAutoLabel(reason, chapterIndex)

                try {
                    bookmarkRepository.addBookmark(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        positionMs = positionMs,
                        noteText = label,
                    )
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    LogUtils.e(TAG, "Failed to create auto-bookmark: book=$bookId pos=${positionMs}ms reason=$reason", e)
                    return@withLock false
                }

                recentBookmarks[key] = positionMs
                LogUtils.d(TAG, "Auto-bookmark created: book=$bookId pos=${positionMs}ms reason=$reason label=$label")
                true
            }

        /**
         * Checks if a reason should trigger auto-bookmarking.
         */
        public fun shouldAutoBookmark(reason: AutoBookmarkReason): Boolean = reason != AutoBookmarkReason.MANUAL

        /**
         * Clears the deduplication cache.
         */
        public fun clearCache() {
            recentBookmarks.clear()
        }

        private fun generateAutoLabel(
            reason: AutoBookmarkReason,
            chapterIndex: Int,
        ): String =
            when (reason) {
                AutoBookmarkReason.CHAPTER_START -> "Глава ${chapterIndex + 1}"
                AutoBookmarkReason.SLEEP_TIMER_STOP -> "Остановился здесь"
                AutoBookmarkReason.PHONE_CALL_INTERRUPTED -> "Прервал звонок"
                AutoBookmarkReason.HEADPHONES_REMOVED -> "Снял наушники"
                AutoBookmarkReason.LONG_PAUSE_RESUME -> "Возобновление после паузы"
                AutoBookmarkReason.MANUAL -> "Закладка"
            }

        public companion object {
            private const val TAG = "AutoBookmarkTrigger"
            internal const val DEDUPLICATE_WINDOW_MS = 30_000L
            internal const val MAX_RECENT_BOOKMARKS = 256
        }
    }
