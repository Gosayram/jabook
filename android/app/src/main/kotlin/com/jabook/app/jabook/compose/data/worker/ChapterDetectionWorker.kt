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

package com.jabook.app.jabook.compose.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.audio.ChapterDetectionPolicy
import com.jabook.app.jabook.audio.ChapterDetectionResultPolicy
import com.jabook.app.jabook.audio.ChapterSignalExtractor
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager job that detects chapter boundaries from silence and persists
 * synthetic chapter metadata for single-file audiobooks.
 */
@HiltWorker
public class ChapterDetectionWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val chapterSignalExtractor: ChapterSignalExtractor,
        private val chaptersDao: ChaptersDao,
        loggerFactory: LoggerFactory,
    ) : CoroutineWorker(appContext, params) {
        private val logger = loggerFactory.get("ChapterDetectionWorker")

        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val bookId = inputData.getString(KEY_BOOK_ID).orEmpty()
                val filePath = inputData.getString(KEY_FILE_PATH).orEmpty()
                val fileIndex = inputData.getInt(KEY_FILE_INDEX, 0)
                val totalDurationMs = inputData.getLong(KEY_DURATION_MS, 0L).coerceAtLeast(0L)

                if (bookId.isBlank() || filePath.isBlank() || totalDurationMs <= 0L || fileIndex < 0) {
                    return@withContext Result.failure(
                        workDataOf(
                            KEY_RESULT_CHAPTERS_COUNT to 0,
                            KEY_RESULT_ERROR to "invalid_input",
                        ),
                    )
                }

                try {
                    val rmsDbValues =
                        chapterSignalExtractor.extractRmsDb(
                            filePath = filePath,
                            windowStepMs = ChapterDetectionPolicy.DEFAULT_WINDOW_STEP_MS,
                        )

                    if (rmsDbValues.isEmpty()) {
                        return@withContext Result.failure(
                            workDataOf(
                                KEY_RESULT_CHAPTERS_COUNT to 0,
                                KEY_RESULT_SIGNAL_WINDOWS to 0,
                                KEY_RESULT_ERROR to "empty_signal",
                            ),
                        )
                    }

                    val rawCandidates =
                        ChapterDetectionPolicy.detectCandidates(
                            rmsDbValues = rmsDbValues,
                        )
                    val candidates = ChapterDetectionResultPolicy.normalizeCandidates(rawCandidates)
                    val chapters =
                        synthesizeChapters(
                            bookId = bookId,
                            filePath = filePath,
                            fileIndex = fileIndex,
                            totalDurationMs = totalDurationMs,
                            boundariesMs = candidates.map { it.startMs },
                        )

                    val existingCount = chaptersDao.getTotalCount(bookId)
                    if (existingCount > 1) {
                        logger.d {
                            "Skip auto-chapter persistence for multi-file book=$bookId (chapters=$existingCount)"
                        }
                        return@withContext Result.success(
                            workDataOf(
                                KEY_RESULT_CHAPTERS_COUNT to 0,
                                KEY_RESULT_SIGNAL_WINDOWS to rmsDbValues.size,
                            ),
                        )
                    }
                    chaptersDao.deleteByBookId(bookId)
                    chaptersDao.insertAll(chapters)
                    Result.success(
                        workDataOf(
                            KEY_RESULT_CHAPTERS_COUNT to chapters.size,
                            KEY_RESULT_SIGNAL_WINDOWS to rmsDbValues.size,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Chapter detection failed for book=$bookId" }, e)
                    Result.failure(
                        workDataOf(
                            KEY_RESULT_CHAPTERS_COUNT to 0,
                            KEY_RESULT_ERROR to (e.message ?: "unknown"),
                        ),
                    )
                }
            }

        private fun synthesizeChapters(
            bookId: String,
            filePath: String,
            fileIndex: Int,
            totalDurationMs: Long,
            boundariesMs: List<Long>,
        ): List<ChapterEntity> {
            val starts =
                (listOf(0L) + boundariesMs)
                    .map { it.coerceIn(0L, totalDurationMs) }
                    .distinct()
                    .sorted()
            if (starts.isEmpty()) return emptyList()

            return starts.mapIndexed { chapterIndex, startMs ->
                val nextStartMs = starts.getOrNull(chapterIndex + 1)
                val endTime = nextStartMs ?: totalDurationMs
                ChapterEntity(
                    id = "${bookId}_auto_$chapterIndex",
                    bookId = bookId,
                    title = "Auto Chapter ${chapterIndex + 1}",
                    chapterIndex = chapterIndex,
                    fileIndex = fileIndex,
                    duration = (endTime - startMs).coerceAtLeast(0L),
                    fileUrl = filePath,
                    position = 0L,
                    isCompleted = false,
                    isDownloaded = true,
                )
            }
        }

        public companion object {
            public const val WORK_NAME_PREFIX: String = "chapter_detection"
            public const val KEY_BOOK_ID: String = "book_id"
            public const val KEY_FILE_PATH: String = "file_path"
            public const val KEY_FILE_INDEX: String = "file_index"
            public const val KEY_DURATION_MS: String = "duration_ms"

            public const val KEY_RESULT_CHAPTERS_COUNT: String = "chapters_count"
            public const val KEY_RESULT_SIGNAL_WINDOWS: String = "signal_windows"
            public const val KEY_RESULT_ERROR: String = "error"
        }
    }
