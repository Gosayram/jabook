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
 * Adapter that bridges [TorrentSession] to the concrete [TorrentSessionManager].
 *
 * This thin wrapper delegates every call to the real libtorrent4j-backed session
 * manager without adding behavior. It exists so that production code depends on
 * the [TorrentSession] abstraction while tests inject fakes/mocks instead.
 *
 * Thread-safety: inherits the thread model of [TorrentSessionManager] (mainly
 * callbacks from libtorrent's alert-dispatch thread).
 */
@Singleton
public class TorrentSessionAdapter
    @Inject
    constructor(
        private val delegate: TorrentSessionManager,
    ) : TorrentSession {
        override val downloadsFlow: kotlinx.coroutines.flow.StateFlow<Map<String, TorrentDownload>>
            get() = delegate.downloadsFlow

        override fun initSession() {
            delegate.initSession()
        }

        override fun addTorrent(
            magnetUri: String,
            savePath: String,
            selectedFileIndices: List<Int>?,
            topicId: String?,
        ): Result<String> = delegate.addTorrent(magnetUri, savePath, selectedFileIndices, topicId)

        override fun removeTorrent(
            hash: String,
            deleteFiles: Boolean,
        ) {
            delegate.removeTorrent(hash, deleteFiles)
        }

        override fun pauseTorrent(hash: String) {
            delegate.pauseTorrent(hash)
        }

        override fun resumeTorrent(hash: String) {
            delegate.resumeTorrent(hash)
        }

        override fun pauseAll() {
            delegate.pauseAll()
        }

        override fun resumeAll() {
            delegate.resumeAll()
        }

        override fun moveTorrentStorage(
            hash: String,
            newPath: String,
        ) {
            delegate.moveTorrentStorage(hash, newPath)
        }

        override fun setSequentialDownload(
            hash: String,
            enabled: Boolean,
        ) {
            delegate.setSequentialDownload(hash, enabled)
        }

        override fun prioritizeFile(
            hash: String,
            fileIndex: Int,
            priority: Int,
        ) {
            delegate.prioritizeFile(hash, fileIndex, priority)
        }

        override fun setFilePriorities(
            hash: String,
            priorities: List<Int>,
        ) {
            delegate.setFilePriorities(hash, priorities)
        }

        override fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
        ): Boolean = delegate.isFileReadyForStreaming(hash, fileIndex)

        override fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long = delegate.getDownloadedBytes(hash, fileIndex)

        override fun getDownload(hash: String): TorrentDownload? = delegate.getDownload(hash)

        override fun stopSession() {
            delegate.stopSession()
        }
    }
