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
 * Represents the state of a download operation.
 *
 * Used to track download progress and display appropriate UI
 * for different download states.
 */
sealed interface DownloadState {
    /**
     * No active download.
     */
    data object Idle : DownloadState

    /**
     * Download in progress.
     *
     * @param progress Download progress (0.0 to 1.0)
     * @param downloadedBytes Bytes downloaded so far
     * @param totalBytes Total bytes to download (null if unknown)
     * @param speedBytesPerSecond Download speed in bytes per second
     */
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSecond: Long,
    ) : DownloadState {
        /**
         * Formatted speed string (e.g., "1.5 MB/s").
         */
        val formattedSpeed: String
            get() {
                val mbps = speedBytesPerSecond / 1_000_000.0
                return String.format("%.1f MB/s", mbps)
            }
    }

    /**
     * Download completed successfully.
     *
     * @param localPath Path to downloaded file
     */
    data class Completed(
        val localPath: String,
    ) : DownloadState

    /**
     * Download paused by user.
     *
     * @param progress Progress when paused (0.0 to 1.0)
     */
    data class Paused(
        val progress: Float,
    ) : DownloadState

    /**
     * Download failed.
     *
     * @param error Error message
     * @param isRetryable Whether download can be retried
     */
    data class Failed(
        val error: String,
        val isRetryable: Boolean = true,
    ) : DownloadState
}
