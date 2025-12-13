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

package com.jabook.app.jabook.compose.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import com.jabook.app.jabook.compose.data.local.scanner.ScannedBook
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.jabook.app.jabook.compose.domain.model.Result as DomainResult

/**
 * WorkManager worker for scanning local audiobooks in background.
 */
@HiltWorker
class LibraryScanWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val bookScanner: LocalBookScanner,
        private val booksDao: BooksDao,
        private val chaptersDao: ChaptersDao,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): ListenableWorker.Result =
            try {
                setProgress(workDataOf("status" to "Scanning..."))

                when (val result = bookScanner.scanAudiobooks()) {
                    is DomainResult.Success -> {
                        val books = result.data
                        setProgress(workDataOf("status" to "Found ${books.size} books"))

                        // Insert books
                        for (book in books) {
                            insertScannedBook(book)
                        }

                        ListenableWorker.Result.success(
                            workDataOf("booksFound" to books.size),
                        )
                    }
                    is DomainResult.Error -> {
                        ListenableWorker.Result.failure(
                            workDataOf("error" to (result.message ?: "Unknown error")),
                        )
                    }
                    is DomainResult.Loading -> {
                        ListenableWorker.Result.retry()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryScanWorker", "Scan failed", e)
                ListenableWorker.Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error")),
                )
            }

        private suspend fun insertScannedBook(scannedBook: ScannedBook) {
            val bookId = "local-${scannedBook.directory.hashCode()}"

            val bookEntity =
                BookEntity(
                    id = bookId,
                    title = scannedBook.title,
                    author = scannedBook.author,
                    coverUrl = saveCoverArt(bookId, scannedBook.coverArt),
                    description = null,
                    totalDuration = scannedBook.totalDuration,
                    localPath = scannedBook.directory,
                    addedDate = System.currentTimeMillis(),
                    downloadStatus = "DOWNLOADED",
                    isDownloaded = true,
                )

            booksDao.insertBook(bookEntity)

            val chapterEntities =
                scannedBook.chapters.map { chapter ->
                    ChapterEntity(
                        id = "$bookId-chapter-${chapter.index}",
                        bookId = bookId,
                        title = chapter.title,
                        chapterIndex = chapter.index,
                        fileIndex = chapter.index,
                        duration = chapter.duration,
                        fileUrl = chapter.filePath,
                        isDownloaded = true,
                    )
                }

            chaptersDao.insertAll(chapterEntities)
        }

        private suspend fun saveCoverArt(
            bookId: String,
            coverArt: ByteArray?,
        ): String? {
            if (coverArt == null) return null

            return withContext(Dispatchers.IO) {
                try {
                    val coversDir = File(applicationContext.filesDir, "covers")
                    coversDir.mkdirs()

                    val coverFile = File(coversDir, "$bookId.jpg")
                    coverFile.writeBytes(coverArt)

                    coverFile.absolutePath
                } catch (e: Exception) {
                    android.util.Log.e("LibraryScanWorker", "Failed to save cover art", e)
                    null
                }
            }
        }
    }
