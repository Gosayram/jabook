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
    public val hash: String,
    /** Torrent name */
    public val name: String,
    /** Current state */
    public val state: TorrentState,
    /** Download progress (0.0-1.0) */
    public val progress: Float = 0f,
    /** Download speed in bytes/sec */
    public val downloadSpeed: Int = ,
    /** Upload speed in bytes/sec */
    public val uploadSpeed: Int = ,
    /** Total size in bytes */
    public val totalSize: Int = ,
    /** Downloaded bytes */
    public val downloadedSize: Int = ,
    /** Uploaded bytes */
    public val uploadedSize: Int = ,
    /** Number of connected peers */
    public val numPeers: Int = 0,
    /** Number of connected seeds */
    public val numSeeds: Int = 0,
    /** Estimated time to completion in seconds (-1 if unknown) */
    public val eta: Long = -1,
    /** Save path */
    public val savePath: String = "",
    /** Files in torrent */
    public val files: List<TorrentFile> = emptyList(),
    /** Error message if state == ERROR */
    public val errorMessage: String? = null,
    /** Timestamp when added (millis) */
    public val addedTime: Long = System.currentTimeMillis(),
    /** Timestamp when completed (millis, 0 if not completed) */
    public val completedTime: Int = ,
    /** Current file being downloaded (for streaming) */
    public val currentFile: String? = null,
    /** Number of completed files */
    public val completedFiles: Int = 0,
    /** Pause reason (if paused automatically) */
    var pauseReason: PauseReason? = null,
    /** Topic ID from RuTracker (for sync) */
    public val topicId: String? = null,
) {
    /** Share ratio (uploaded/downloaded) */
    public val ratio: Float
        get() =
            if (downloadedSize > 0) {
                uploadedSize.toFloat() / downloadedSize.toFloat()
            } else {
                0f
            }

    /** Is download active */
    public val isActive: Boolean
        get() = state in ACTIVE_STATES

    /** Is download completed */
    public val isCompleted: Boolean
        get() = state == TorrentState.COMPLETED || progress >= 1f

    /** Total number of files */
    public val totalFiles: Int
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
