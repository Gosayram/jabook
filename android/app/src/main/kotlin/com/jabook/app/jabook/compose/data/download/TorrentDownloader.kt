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

package com.jabook.app.jabook.compose.data.download

/**
 * Abstraction for torrent download operations.
 *
 * This is a stub implementation for MVP. Real torrent integration
 * will be added in a future phase.
 */
interface TorrentDownloader {
    /**
     * Start downloading a torrent.
     *
     * @param torrentUrl URL to the .torrent file or magnet link
     * @param savePath Directory where files should be saved
     * @param onProgress Callback for progress updates (0.0 to 1.0)
     * @return Path to downloaded file
     */
    suspend fun download(
        torrentUrl: String,
        savePath: String,
        onProgress: (Float) -> Unit,
    ): String

    /**
     * Pause a download.
     *
     * @param downloadId Unique ID of the download
     */
    suspend fun pause(downloadId: String)

    /**
     * Resume a paused download.
     *
     * @param downloadId Unique ID of the download
     */
    suspend fun resume(downloadId: String)

    /**
     * Cancel a download and cleanup.
     *
     * @param downloadId Unique ID of the download
     */
    suspend fun cancel(downloadId: String)
}
