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

import kotlinx.coroutines.flow.StateFlow

/**
 * Testable abstraction over torrent session operations.
 *
 * Decouples high-level consumers (e.g. [TorrentManager], ViewModels) from
 * the concrete libtorrent4j implementation in [TorrentSessionManager],
 * enabling unit tests with lightweight fakes instead of native library calls.
 *
 * Lifecycle contract:
 * - Call [initSession] before any other operation.
 * - Call [stopSession] to release native resources.
 * - [downloadsFlow] remains empty before initialization.
 */
public interface TorrentSession {
    /** Real-time map of active torrent downloads keyed by info-hash. */
    public val downloadsFlow: StateFlow<Map<String, TorrentDownload>>

    /**
     * Initialize the underlying libtorrent session.
     *
     * Must be called once before any torrent operation.
     * Implementations should be idempotent.
     *
     * @throws IllegalStateException if native initialization fails irrecoverably.
     */
    public fun initSession()

    /**
     * Add a torrent by magnet URI and start downloading.
     *
     * @param magnetUri Magnet link or 40-character hex info-hash.
     * @param savePath Absolute directory where files will be stored.
     * @param selectedFileIndices Optional zero-based indices of files to download.
     * @param topicId Optional RuTracker topic ID for metadata linking.
     * @return [Result.success] with the info-hash on success, or failure.
     */
    public fun addTorrent(
        magnetUri: String,
        savePath: String,
        selectedFileIndices: List<Int>? = null,
        topicId: String? = null,
    ): Result<String>

    /**
     * Remove a torrent from the session.
     *
     * @param hash Info-hash of the torrent.
     * @param deleteFiles If `true`, downloaded files are deleted from disk.
     */
    public fun removeTorrent(
        hash: String,
        deleteFiles: Boolean = false,
    )

    /** Pause a single torrent. */
    public fun pauseTorrent(hash: String)

    /** Resume a single torrent. */
    public fun resumeTorrent(hash: String)

    /** Pause all active torrents. */
    public fun pauseAll()

    /** Resume all paused torrents. */
    public fun resumeAll()

    /**
     * Move torrent storage to a new path.
     *
     * @param hash Info-hash of the torrent.
     * @param newPath Absolute directory path.
     */
    public fun moveTorrentStorage(
        hash: String,
        newPath: String,
    )

    /**
     * Enable or disable sequential download for a torrent.
     *
     * @param hash Info-hash of the torrent.
     * @param enabled `true` to enable sequential piece ordering.
     */
    public fun setSequentialDownload(
        hash: String,
        enabled: Boolean,
    )

    /**
     * Set download priority for a specific file within a torrent.
     *
     * @param hash Info-hash of the torrent.
     * @param fileIndex Zero-based file index.
     * @param priority Priority value (0 = skip, 1..7 = increasing priority).
     */
    public fun prioritizeFile(
        hash: String,
        fileIndex: Int,
        priority: Int,
    )

    /**
     * Set download priorities for all files in a torrent.
     *
     * @param hash Info-hash of the torrent.
     * @param priorities List of priority values (one per file).
     */
    public fun setFilePriorities(
        hash: String,
        priorities: List<Int>,
    )

    /**
     * Check whether enough data has been downloaded to start streaming a file.
     *
     * @param hash Info-hash of the torrent.
     * @param fileIndex Zero-based file index.
     * @return `true` if the file's initial buffer is available.
     */
    public fun isFileReadyForStreaming(
        hash: String,
        fileIndex: Int,
    ): Boolean

    /**
     * Get the number of downloaded bytes for a specific file.
     *
     * @param hash Info-hash of the torrent.
     * @param fileIndex Zero-based file index.
     * @return Downloaded bytes, or `0` if the torrent/file is not found.
     */
    public fun getDownloadedBytes(
        hash: String,
        fileIndex: Int,
    ): Long

    /**
     * Retrieve a snapshot of a specific download.
     *
     * @param hash Info-hash of the torrent.
     * @return The [TorrentDownload], or `null` if not found.
     */
    public fun getDownload(hash: String): TorrentDownload?

    /**
     * Shut down the underlying session and release all resources.
     *
     * After calling this, the session must be re-initialized via [initSession]
     * before any further operations.
     */
    public fun stopSession()
}
