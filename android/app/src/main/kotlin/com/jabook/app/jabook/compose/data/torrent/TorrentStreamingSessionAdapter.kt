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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that bridges [TorrentStreamingSession] operations to [TorrentSessionManager].
 *
 * Delegates all streaming-specific calls to the underlying libtorrent4j session
 * without adding behavior. This keeps the streaming contract testable while
 * reusing the existing session management infrastructure.
 *
 * All methods are safe to call even if the torrent handle is not found — they
 * will silently return defaults (false, empty array, null) as documented in
 * the interface contract.
 */
@Singleton
public class TorrentStreamingSessionAdapter
    @Inject
    constructor(
        private val sessionManager: TorrentSessionManager,
    ) : TorrentStreamingSession {
        override fun setSequentialDownload(
            hash: String,
            enabled: Boolean,
        ) {
            sessionManager.setSequentialDownload(hash, enabled)
        }

        override fun setSequentialRange(
            hash: String,
            range: TorrentStreamingSession.PieceRange?,
        ) {
            sessionManager.setSequentialRange(hash, range?.let { it.firstPiece to it.lastPiece })
        }

        override fun setFilePriorities(
            hash: String,
            priorities: List<Int>,
        ) {
            sessionManager.setFilePriorities(hash, priorities)
        }

        override fun setPieceDeadline(
            hash: String,
            pieceIndex: Int,
            deadlineMs: Int,
        ) {
            sessionManager.setPieceDeadline(hash, pieceIndex, deadlineMs)
        }

        override fun clearPieceDeadlines(hash: String) {
            sessionManager.clearPieceDeadlines(hash)
        }

        override fun havePiece(
            hash: String,
            pieceIndex: Int,
        ): Boolean = sessionManager.havePiece(hash, pieceIndex)

        override fun readPiece(
            hash: String,
            pieceIndex: Int,
        ): ByteArray = sessionManager.readPiece(hash, pieceIndex)

        override fun getFilePieceRange(
            hash: String,
            fileIndex: Int,
        ): TorrentStreamingSession.PieceRange? {
            val (first, last) = sessionManager.getFilePieceRange(hash, fileIndex) ?: return null
            return TorrentStreamingSession.PieceRange(firstPiece = first, lastPiece = last)
        }

        override fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
        ): Boolean = sessionManager.isFileReadyForStreaming(hash, fileIndex)

        override fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long = sessionManager.getDownloadedBytes(hash, fileIndex)
    }
