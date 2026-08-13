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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compatibility worker for already-enqueued chapter detection requests.
 */
@HiltWorker
public class ChapterDetectionWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {
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

                // Existing queued work must not create duplicate ChapterEntity rows for one file.
                // The player has no segment-offset support yet, so keeping the scanner's one file =
                // one chapter representation is the only playback-safe fallback.
                Result.success()
            }

        public companion object {
            public const val WORK_NAME_PREFIX: String = "chapter_detection"
            public const val KEY_BOOK_ID: String = "book_id"
            public const val KEY_FILE_PATH: String = "file_path"
            public const val KEY_FILE_INDEX: String = "file_index"
            public const val KEY_DURATION_MS: String = "duration_ms"
            public const val KEY_FILE_LAST_MODIFIED_MS: String = "file_last_modified_ms"

            public const val KEY_RESULT_CHAPTERS_COUNT: String = "chapters_count"
            public const val KEY_RESULT_SIGNAL_WINDOWS: String = "signal_windows"
            public const val KEY_RESULT_ERROR: String = "error"
        }
    }
