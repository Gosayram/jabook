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

package com.jabook.app.jabook.compose.domain.model

/**
 * Information about an active download.
 *
 * Used to display download progress in UI and manage download queue.
 *
 * @param bookId ID of the book being downloaded
 * @param bookTitle Title of the book for display
 * @param torrentUrl URL of the torrent file
 * @param state Current download state
 * @param queuePosition Position in download queue (0 = downloading now, 1+ = queued)
 */
data class DownloadInfo(
    val bookId: String,
    val bookTitle: String,
    val torrentUrl: String,
    val state: DownloadState,
    val queuePosition: Int = 0,
) {
    /**
     * Whether this download is currently active (downloading).
     */
    val isActive: Boolean
        get() = state is DownloadState.Downloading

    /**
     * Whether this download is queued (waiting to start).
     */
    val isQueued: Boolean
        get() = queuePosition > 0
}
