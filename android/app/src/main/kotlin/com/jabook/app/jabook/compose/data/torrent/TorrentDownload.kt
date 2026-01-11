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

package com.jabook.app.jabook.compose.data.torrent

/**
 * Torrent download information
 */
public data class TorrentDownload(
    /** Info hash (unique identifier) */
    val hash: String,
    /** Torrent name */
    val name: String,
    /** Current state */
    val state: TorrentState,
    /** Download progress (0.0-1.0) */
    val progress: Float = 0f,
    /** Download speed in bytes/sec */
    val downloadSpeed: Long = 0,
    /** Upload speed in bytes/sec */
    val uploadSpeed: Long = 0,
    /** Total size in bytes */
    val totalSize: Long = 0,
    /** Downloaded bytes */
    val downloadedSize: Long = 0,
    /** Uploaded bytes */
    val uploadedSize: Long = 0,
    /** Number of connected peers */
    val numPeers: Int = 0,
    /** Number of connected seeds */
    val numSeeds: Int = 0,
    /** Estimated time to completion in seconds (-1 if unknown) */
    val eta: Long = -1,
    /** Save path */
    val savePath: String = "",
    /** Files in torrent */
    val files: List<TorrentFile> = emptyList(),
    /** Error message if state == ERROR */
    val errorMessage: String? = null,
    /** Timestamp when added (millis) */
    val addedTime: Long = System.currentTimeMillis(),
    /** Timestamp when completed (millis, 0 if not completed) */
    val completedTime: Long = 0,
    /** Current file being downloaded (for streaming) */
    val currentFile: String? = null,
    /** Number of completed files */
    val completedFiles: Int = 0,
    /** Pause reason (if paused automatically) */
    var pauseReason: PauseReason? = null,
    /** Topic ID from RuTracker (for sync) */
    val topicId: String? = null,
) {
    /** Share ratio (uploaded/downloaded) */
    val ratio: Float
        get() =
            if (downloadedSize > 0) {
                uploadedSize.toFloat() / downloadedSize.toFloat()
            } else {
                0f
            }

    /** Is download active */
    val isActive: Boolean
        get() = state in ACTIVE_STATES

    /** Is download completed */
    val isCompleted: Boolean
        get() = state == TorrentState.COMPLETED || progress >= 1f

    /** Total number of files */
    val totalFiles: Int
        get() = files.size

    public companion object {
        private val ACTIVE_STATES =
            setOf(
                TorrentState.DOWNLOADING,
                TorrentState.STREAMING,
                TorrentState.SEEDING,
                TorrentState.CHECKING,
                TorrentState.DOWNLOADING_METADATA,
            )
    }
}

/**
 * Reason why torrent was paused
 */
public enum class PauseReason {
    USER_PAUSED,
    WAITING_FOR_WIFI,
    NO_NETWORK,
    LOW_BATTERY,
    STORAGE_FULL,
}
