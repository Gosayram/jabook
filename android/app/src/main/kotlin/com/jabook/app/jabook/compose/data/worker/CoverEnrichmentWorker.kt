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
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.remote.cover.RemoteCoverProvider
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Silently looks up covers for books missing them via public APIs
 * ([RemoteCoverProvider]). Each processed book is persisted as attempted so it
 * is never retried; runs in small throttled batches to stay data/battery friendly.
 */
@HiltWorker
public class CoverEnrichmentWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val booksDao: BooksDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val coverProvider: RemoteCoverProvider,
        private val loggerFactory: LoggerFactory,
    ) : CoroutineWorker(appContext, params) {
        private val logger = loggerFactory.get(TAG)

        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                try {
                    val attempted = userPreferencesRepository.getAttemptedCoverLookupIds()
                    val batch =
                        booksDao
                            .getBooksWithoutCovers()
                            .filter { it.id !in attempted }
                            .take(BATCH_SIZE)
                    if (batch.isEmpty()) {
                        logger.i { "No books need cover enrichment" }
                        return@withContext Result.success()
                    }

                    var found = 0
                    batch.forEachIndexed { index, book ->
                        val url = coverProvider.lookup(book.title, book.author)
                        if (url != null) {
                            booksDao.updateCoverUrl(book.id, url)
                            found++
                        }
                        // Throttle between (not after) lookups: politeness to public APIs.
                        if (index < batch.lastIndex) delay(INTER_LOOKUP_DELAY_MS)
                    }
                    userPreferencesRepository.markCoverLookupAttempted(batch.map { it.id })
                    logger.i { "Cover enrichment found $found/${batch.size} covers (attempt=$runAttemptCount)" }
                    Result.success()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Cover enrichment failed" }, e)
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            }

        public companion object {
            private const val TAG = "CoverEnrichmentWorker"
            public const val WORK_NAME: String = "cover_enrichment"
            private const val BATCH_SIZE: Int = 20
            private const val INTER_LOOKUP_DELAY_MS: Long = 1_000L
            private const val MAX_ATTEMPTS: Int = 3
        }
    }
