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

package com.jabook.app.jabook.compose.data.offline

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-chapter progress snapshot for a pre-cached book.
 *
 * @property downloaded chapters whose download reached STATE_COMPLETED (media3 Download)
 * @property failed chapters whose download reached STATE_FAILED (media3 Download)
 * @property total chapters tracked for the book
 * @property finished true when every chapter is in a terminal state (downloaded or failed)
 */
public data class OfflineBookProgress(
    public val downloaded: Int,
    public val failed: Int,
    public val total: Int,
    public val finished: Boolean,
)

/**
 * Downloads whole audiobooks into the *same* [androidx.media3.datasource.cache.SimpleCache] the
 * player streams through, so a pre-cached book plays offline with zero extra plumbing.
 *
 * Cache-hit guarantee: [PlaylistManager] builds its CacheDataSource with
 * `CacheKeyFactory.DEFAULT` (key = request URI), and [DownloadManager]'s internal
 * CacheDataSource.Factory uses the same default — as long as the caller passes the exact
 * chapter URLs the player will later request, the player hits the cache.
 *
 * Process-death behavior (why no WorkManager here): media3's [DownloadManager] persists every
 * download (partial progress included) in the file-backed [DefaultDownloadIndex], and pauses
 * itself when connectivity is lost. On next app start, simply touching [progress] or calling
 * [prepareBook] re-creates the manager and calls `resumeDownloads()`, which picks up where it
 * stopped. Retries use media3's default min-retry-count (5).
 *
 * All [DownloadManager] calls are funnelled to the main thread (its documented threading model);
 * index reads go through the thread-safe [androidx.media3.exoplayer.offline.DownloadIndex].
 */
@OptIn(UnstableApi::class)
@Singleton
public class OfflineCacheDownloader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val cache: Cache,
        private val okHttpClient: OkHttpClient,
        private val databaseProvider: DatabaseProvider,
    ) {
        // Lazy: constructing DownloadManager starts a handler thread + SQLite index load, and it
        // needs a main looper — never instantiate during DI graph creation.
        private val manager: DownloadManager by lazy { createManager() }

        private fun createManager(): DownloadManager {
            // Same HTTP stack as playback (DoH, Brotli, UA) but with the OkHttp response cache
            // disabled — the media3 SimpleCache is the cache that matters (mirrors PlaylistManager).
            val upstream: DataSource.Factory =
                OkHttpDataSource.Factory(
                    okHttpClient
                        .newBuilder()
                        .cache(null)
                        .build(),
                )
            // Convenience ctor wires DefaultDownloadIndex(databaseProvider) +
            // DefaultDownloaderFactory(CacheDataSource on OUR shared cache). One download thread
            // per task is fine for sequential chapters; media3 caps parallelism at 3 anyway.
            return DownloadManager(
                context,
                databaseProvider,
                cache,
                upstream,
                // Run each download task on its own thread (media3's documented default).
                Executor { command -> command.run() },
            ).also { it.resumeDownloads() } // starts paused by default
        }

        /**
         * Enqueue every chapter of [bookId] for download into the shared playback cache.
         * Idempotent: chapters already tracked (any state) are left untouched.
         *
         * @param bookId stable id used to namespace the download ids (e.g. database book id)
         * @param chapterUrls remote http(s) chapter URLs, exactly as the player will request them
         * @return the deterministic download ids, in chapter order
         * @throws IllegalArgumentException on blank id, empty list, or non-http(s) URLs
         */
        @MainThread
        public fun prepareBook(
            bookId: String,
            chapterUrls: List<String>,
        ): List<String> {
            require(validBookId(bookId) && chapterUrls.isNotEmpty() && chapterUrls.all(::validUrl)) {
                "bookId must be non-blank and chapterUrls must be a non-empty list of http(s) URLs"
            }
            val ids = chapterUrls.indices.map { downloadId(bookId, it) }
            val index = manager.downloadIndex
            chapterUrls.forEachIndexed { i, url ->
                if (index.getDownload(ids[i]) == null) {
                    manager.addDownload(DownloadRequest.Builder(ids[i], Uri.parse(url)).build())
                }
            }
            LogUtils.d("OfflineCacheDownloader", "pre-cached book=$bookId chapters=${chapterUrls.size}")
            return ids
        }

        /** Cancel and forget every download tracked for [bookId] (already-cached bytes stay;
         * they are plain LRU cache entries and will be reused or evicted normally). */
        @MainThread
        public fun cancel(
            bookId: String,
            chapterCount: Int,
        ) {
            require(validBookId(bookId) && chapterCount > 0) { "bookId must be non-blank; chapterCount > 0" }
            (0 until chapterCount).forEach { manager.removeDownload(downloadId(bookId, it)) }
        }

        /**
         * Poll the persisted index for [bookId] every [pollIntervalMs] until all chapters are
         * terminal, then the flow completes. Collecting this also re-attaches after an app kill
         * (lazy manager creation + resumeDownloads).
         */
        public fun progress(
            bookId: String,
            chapterCount: Int,
            pollIntervalMs: Long = 500L,
        ): Flow<OfflineBookProgress> =
            flow {
                require(validBookId(bookId) && chapterCount > 0) { "bookId must be non-blank; chapterCount > 0" }
                // Index read on main: DefaultDownloadIndex is synchronized and this is a handful
                // of rows — fine for the slice; move behind a dispatcher if ever it polls big lists.
                while (true) {
                    val states =
                        withContext(Dispatchers.Main) {
                            val index = manager.downloadIndex
                            (0 until chapterCount).map { index.getDownload(downloadId(bookId, it))?.state }
                        }
                    val p = aggregate(states)
                    emit(p)
                    if (p.finished) break
                    delay(pollIntervalMs)
                }
            }

        public companion object {
            // Mirrors Download.STATE_* ints (0..4) so pure-JVM tests need not load media3.
            private const val STATE_QUEUED = 0
            private const val STATE_STOPPED = 1
            private const val STATE_DOWNLOADING = 2
            private const val STATE_COMPLETED = 3
            private const val STATE_FAILED = 4

            /** Deterministic id so a book's downloads are re-findable without extra app state. */
            public fun downloadId(
                bookId: String,
                chapterIndex: Int,
            ): String = "$bookId#$chapterIndex"

            public fun validBookId(bookId: String): Boolean = bookId.isNotBlank()

            public fun validUrl(url: String): Boolean = url.startsWith("http://", true) || url.startsWith("https://", true)

            /** Fold per-chapter download states (null = not enqueued) into a snapshot. */
            public fun aggregate(states: List<Int?>): OfflineBookProgress {
                val done = states.count { it == STATE_COMPLETED }
                val failed = states.count { it == STATE_FAILED }
                val pending = states.count { it != null && it != STATE_COMPLETED && it != STATE_FAILED }
                return OfflineBookProgress(
                    downloaded = done,
                    failed = failed,
                    total = states.size,
                    finished = pending == 0 && states.none { it == null },
                )
            }
        }
    }
