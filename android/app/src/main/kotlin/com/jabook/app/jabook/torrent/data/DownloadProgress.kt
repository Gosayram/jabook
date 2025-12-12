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

package com.jabook.app.jabook.torrent.data

/**
 * Download progress data for a torrent.
 */
data class DownloadProgress(
    val infoHash: String,
    val percentage: Float,
    val downloadRate: Long, // bytes/sec
    val uploadRate: Long, // bytes/sec
    val downloaded: Long, // bytes
    val uploaded: Long, // bytes
    val totalSize: Long, // bytes
    val numPeers: Int,
    val numSeeds: Int,
    val state: TorrentState,
)

/**
 * Torrent download state.
 */
enum class TorrentState {
    QUEUED,
    CHECKING,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    FINISHED,
    SEEDING,
    PAUSED,
    ERROR,
}
