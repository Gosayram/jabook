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
 * Streaming-specific contract for real-time audio playback over torrent data.
 *
 * Provides piece-level operations needed for time-critical streaming that go
 * beyond the basic download-oriented [TorrentSession] contract. This includes
 * sequential ranges, piece deadlines, priority scheduling, and piece-level
 * read/access checks required by a streaming transport layer.
 *
 * Implementations should delegate to libtorrent4j handle-level APIs while
 * isolating consumers from native library details.
 *
 * Lifecycle:
 * - The session must be initialized ([TorrentSession.initSession]) before use.
 * - Streaming operations are per-torrent, identified by info-hash.
 * - Call [clearPieceDeadlines] and [resetSequentialRange] when switching files
 *   or stopping streaming to avoid stale state in the libtorrent session.
 */
public interface TorrentStreamingSession {
    /**
     * Piece range for a file within a torrent.
     *
     * @property firstPiece Index of the first piece belonging to the file.
     * @property lastPiece Index of the last piece belonging to the file.
     * @property totalPieces Total number of pieces in this file's range (inclusive).
     */
    public data class PieceRange(
        val firstPiece: Int,
        val lastPiece: Int,
    ) {
        /** Total number of pieces in this range (inclusive). */
        val totalPieces: Int get() = lastPiece - firstPiece + 1

        init {
            require(firstPiece >= 0) { "firstPiece must be >= 0, got $firstPiece" }
            require(
                lastPiece >= firstPiece,
            ) { "lastPiece must be >= firstPiece, got last=$lastPiece first=$firstPiece" }
        }
    }

    /**
     * Enable or disable sequential download for an entire torrent.
     *
     * When enabled, libtorrent downloads pieces in order from the beginning.
     * For streaming, prefer [setSequentialRange] to limit sequential behavior
     * to the relevant file's piece range.
     *
     * @param hash Info-hash of the torrent.
     * @param enabled `true` to enable sequential piece ordering.
     */
    public fun setSequentialDownload(
        hash: String,
        enabled: Boolean,
    )

    /**
     * Limit sequential download to a specific piece range within a torrent.
     *
     * This is more efficient than enabling sequential for the whole torrent
     * when only a single audio file is being streamed.
     *
     * @param hash Info-hash of the torrent.
     * @param range The piece range to download sequentially, or `null` to reset
     *   to whole-torrent sequential mode.
     */
    public fun setSequentialRange(
        hash: String,
        range: PieceRange?,
    )

    /**
     * Reset sequential range back to whole-torrent behavior.
     *
     * Convenience wrapper for `setSequentialRange(hash, null)`.
     *
     * @param hash Info-hash of the torrent.
     */
    public fun resetSequentialRange(hash: String) {
        setSequentialRange(hash, null)
    }

    /**
     * Set download priorities for all files in a torrent.
     *
     * Use this to skip unwanted files and prioritize the target audio file.
     *
     * @param hash Info-hash of the torrent.
     * @param priorities Priority per file (0 = skip, 1..7 = increasing priority).
     */
    public fun setFilePriorities(
        hash: String,
        priorities: List<Int>,
    )

    /**
     * Set a deadline for a specific piece.
     *
     * Time-critical pieces get higher priority in libtorrent's download queue.
     * Pieces with sooner deadlines are fetched before pieces with later ones.
     *
     * @param hash Info-hash of the torrent.
     * @param pieceIndex Zero-based piece index.
     * @param deadlineMs Deadline in milliseconds from now. Use 0 for immediate priority.
     */
    public fun setPieceDeadline(
        hash: String,
        pieceIndex: Int,
        deadlineMs: Int,
    )

    /**
     * Clear all piece deadlines for a torrent.
     *
     * Should be called when switching streaming targets or stopping playback
     * to remove stale deadline constraints.
     *
     * @param hash Info-hash of the torrent.
     */
    public fun clearPieceDeadlines(hash: String)

    /**
     * Check whether a specific piece has been fully downloaded and is available.
     *
     * Used by the streaming transport to verify data availability before reading.
     *
     * @param hash Info-hash of the torrent.
     * @param pieceIndex Zero-based piece index.
     * @return `true` if the piece is fully downloaded and verified.
     */
    public fun havePiece(
        hash: String,
        pieceIndex: Int,
    ): Boolean

    /**
     * Read the data of a downloaded piece.
     *
     * The caller must ensure the piece is available via [havePiece] before reading.
     * Implementations may return partial or empty data if the piece is not ready.
     *
     * @param hash Info-hash of the torrent.
     * @param pieceIndex Zero-based piece index.
     * @return The piece data as a byte array, or an empty array if unavailable.
     */
    public fun readPiece(
        hash: String,
        pieceIndex: Int,
    ): ByteArray

    /**
     * Compute the piece range for a specific file within a torrent.
     *
     * Uses torrent metadata to map a file index to its piece boundaries.
     * Essential for determining which pieces to prioritize for streaming.
     *
     * @param hash Info-hash of the torrent.
     * @param fileIndex Zero-based file index.
     * @return The [PieceRange] for the file, or `null` if metadata is unavailable.
     */
    public fun getFilePieceRange(
        hash: String,
        fileIndex: Int,
    ): PieceRange?

    /**
     * Check whether enough data has been downloaded to start streaming a file.
     *
     * Implementations define the readiness threshold (e.g., first N pieces
     * or a percentage of the file's initial portion).
     *
     * @param hash Info-hash of the torrent.
     * @param fileIndex Zero-based file index.
     * @return `true` if the file's initial buffer is available for streaming.
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
}
