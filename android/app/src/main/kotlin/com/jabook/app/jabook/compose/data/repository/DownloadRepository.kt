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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.domain.model.DownloadInfo
import com.jabook.app.jabook.compose.domain.model.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing book downloads.
 *
 * Coordinates WorkManager tasks, observes download progress,
 * and manages download queue.
 */
interface DownloadRepository {
    /**
     * Start downloading a book.
     *
     * Enqueues a WorkManager task and returns a Flow to observe progress.
     *
     * @param bookId ID of the book to download
     * @param torrentUrl URL of the torrent file
     * @return Flow of download state updates
     */
    fun startDownload(
        bookId: String,
        torrentUrl: String,
    ): Flow<DownloadState>

    /**
     * Pause an active download.
     *
     * @param bookId ID of the book download to pause
     */
    suspend fun pauseDownload(bookId: String)

    /**
     * Resume a paused download.
     *
     * @param bookId ID of the book download to resume
     */
    suspend fun resumeDownload(bookId: String)

    /**
     * Cancel a download and remove from queue.
     *
     * @param bookId ID of the book download to cancel
     */
    suspend fun cancelDownload(bookId: String)

    /**
     * Get download status for a specific book.
     *
     * @param bookId ID of the book
     * @return Flow of download state
     */
    fun getDownloadStatus(bookId: String): Flow<DownloadState>

    /**
     * Get all active downloads (in progress + queued).
     *
     * @return Flow of list of active downloads
     */
    fun getActiveDownloads(): Flow<List<DownloadInfo>>
}
