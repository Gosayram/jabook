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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TorrentSession] abstraction contract.
 *
 * Uses a lightweight [FakeTorrentSession] to verify the interface
 * contract without libtorrent4j native dependencies.
 */
class TorrentSessionTest {
    private lateinit var fakeSession: FakeTorrentSession

    @Before
    fun setUp() {
        fakeSession = FakeTorrentSession()
    }

    // --- Lifecycle ---

    @Test
    fun `initSession sets initialized flag`() {
        fakeSession.initSession()
        assertTrue(fakeSession.isInitialized)
    }

    @Test
    fun `stopSession clears initialized flag`() {
        fakeSession.initSession()
        fakeSession.stopSession()
        assertFalse(fakeSession.isInitialized)
    }

    @Test
    fun `initSession is idempotent`() {
        fakeSession.initSession()
        fakeSession.initSession()
        assertTrue(fakeSession.isInitialized)
        assertEquals(1, fakeSession.initCount)
    }

    // --- Add / Remove ---

    @Test
    fun `addTorrent returns success with hash`() {
        fakeSession.initSession()
        val result =
            fakeSession.addTorrent(
                magnetUri = "magnet:?xt=urn:btih:abc123abc123abc123abc123abc123abc123abc1",
                savePath = "/downloads",
            )
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `addTorrent without init returns failure`() {
        val result =
            fakeSession.addTorrent(
                magnetUri = "magnet:?xt=urn:btih:abc123abc123abc123abc123abc123abc123abc1",
                savePath = "/downloads",
            )
        assertTrue(result.isFailure)
    }

    @Test
    fun `removeTorrent removes from downloads`() {
        fakeSession.initSession()
        val hash =
            fakeSession
                .addTorrent(
                    magnetUri = "magnet:?xt=urn:btih:abc123abc123abc123abc123abc123abc123abc1",
                    savePath = "/downloads",
                ).getOrThrow()

        fakeSession.removeTorrent(hash)
        assertNull(fakeSession.getDownload(hash))
    }

    @Test
    fun `removeTorrent with deleteFiles flag propagates`() {
        fakeSession.initSession()
        val hash =
            fakeSession
                .addTorrent(
                    magnetUri = "magnet:?xt=urn:btih:abc123abc123abc123abc123abc123abc123abc1",
                    savePath = "/downloads",
                ).getOrThrow()

        fakeSession.removeTorrent(hash, deleteFiles = true)
        assertNull(fakeSession.getDownload(hash))
        assertTrue(fakeSession.lastDeleteFilesFlag)
    }

    // --- Pause / Resume ---

    @Test
    fun `pauseTorrent updates download state`() {
        fakeSession.initSession()
        val hash =
            fakeSession
                .addTorrent(
                    magnetUri = "magnet:?xt=urn:btih:abc123abc123abc123abc123abc123abc123abc1",
                    savePath = "/downloads",
                ).getOrThrow()

        fakeSession.pauseTorrent(hash)
        assertEquals(TorrentState.PAUSED, fakeSession.getDownload(hash)?.state)
    }

    @Test
    fun `resumeTorrent updates download state`() {
        fakeSession.initSession()
        val hash =
            fakeSession
                .addTorrent(
                    magnetUri = "magnet:?xt=urn:btih:abc123abc123abc123abc123abc123abc123abc1",
                    savePath = "/downloads",
                ).getOrThrow()

        fakeSession.pauseTorrent(hash)
        fakeSession.resumeTorrent(hash)
        assertEquals(TorrentState.DOWNLOADING, fakeSession.getDownload(hash)?.state)
    }

    @Test
    fun `pauseAll pauses all active torrents`() {
        fakeSession.initSession()
        val h1 = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "a".repeat(40), "/dl").getOrThrow()
        val h2 = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "b".repeat(40), "/dl").getOrThrow()

        fakeSession.pauseAll()
        assertEquals(TorrentState.PAUSED, fakeSession.getDownload(h1)?.state)
        assertEquals(TorrentState.PAUSED, fakeSession.getDownload(h2)?.state)
    }

    @Test
    fun `resumeAll resumes all paused torrents`() {
        fakeSession.initSession()
        val h1 = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "a".repeat(40), "/dl").getOrThrow()
        val h2 = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "b".repeat(40), "/dl").getOrThrow()

        fakeSession.pauseAll()
        fakeSession.resumeAll()
        assertEquals(TorrentState.DOWNLOADING, fakeSession.getDownload(h1)?.state)
        assertEquals(TorrentState.DOWNLOADING, fakeSession.getDownload(h2)?.state)
    }

    // --- Streaming / Priority ---

    @Test
    fun `setSequentialDownload enables streaming mode`() {
        fakeSession.initSession()
        val hash = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "c".repeat(40), "/dl").getOrThrow()

        fakeSession.setSequentialDownload(hash, true)
        assertTrue(fakeSession.isSequentialEnabled(hash))

        fakeSession.setSequentialDownload(hash, false)
        assertFalse(fakeSession.isSequentialEnabled(hash))
    }

    @Test
    fun `prioritizeFile sets file priority`() {
        fakeSession.initSession()
        val hash = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "d".repeat(40), "/dl").getOrThrow()

        fakeSession.prioritizeFile(hash, fileIndex = 0, priority = 7)
        assertEquals(7, fakeSession.getFilePriority(hash, 0))
    }

    @Test
    fun `setFilePriorities sets all file priorities`() {
        fakeSession.initSession()
        val hash = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "e".repeat(40), "/dl").getOrThrow()

        fakeSession.setFilePriorities(hash, listOf(4, 7, 1))
        assertEquals(listOf(4, 7, 1), fakeSession.getAllFilePriorities(hash))
    }

    @Test
    fun `isFileReadyForStreaming returns false before any data`() {
        fakeSession.initSession()
        val hash = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "f".repeat(40), "/dl").getOrThrow()

        assertFalse(fakeSession.isFileReadyForStreaming(hash, 0))
    }

    @Test
    fun `getDownloadedBytes returns zero before download`() {
        fakeSession.initSession()
        val hash = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "a1".repeat(20), "/dl").getOrThrow()

        assertEquals(0L, fakeSession.getDownloadedBytes(hash, 0))
    }

    // --- downloadsFlow ---

    @Test
    fun `downloadsFlow is empty before init`() {
        assertTrue(fakeSession.downloadsFlow.value.isEmpty())
    }

    @Test
    fun `downloadsFlow contains added torrents`() {
        fakeSession.initSession()
        fakeSession.addTorrent("magnet:?xt=urn:btih:" + "aa".repeat(20), "/dl")

        assertEquals(1, fakeSession.downloadsFlow.value.size)
    }

    @Test
    fun `moveTorrentStorage propagates to fake`() {
        fakeSession.initSession()
        val hash = fakeSession.addTorrent("magnet:?xt=urn:btih:" + "bb".repeat(20), "/old").getOrThrow()

        fakeSession.moveTorrentStorage(hash, "/new")
        assertEquals("/new", fakeSession.getSavePath(hash))
    }

    // --- Edge cases ---

    @Test
    fun `getDownload returns null for unknown hash`() {
        assertNull(fakeSession.getDownload("nonexistent"))
    }

    @Test
    fun `operations on unknown hash do not crash`() {
        // These should be no-ops, not throw
        fakeSession.pauseTorrent("unknown")
        fakeSession.resumeTorrent("unknown")
        fakeSession.removeTorrent("unknown")
        fakeSession.setSequentialDownload("unknown", true)
        fakeSession.prioritizeFile("unknown", 0, 4)
        fakeSession.setFilePriorities("unknown", listOf(4))
        assertFalse(fakeSession.isFileReadyForStreaming("unknown", 0))
        assertEquals(0L, fakeSession.getDownloadedBytes("unknown", 0))
    }
}

/**
 * Lightweight fake implementation of [TorrentSession] for unit tests.
 *
 * Maintains in-memory state to verify the interface contract
 * without requiring libtorrent4j native libraries.
 */
public class FakeTorrentSession : TorrentSession {
    private val _downloadsFlow = MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())
    override val downloadsFlow: StateFlow<Map<String, TorrentDownload>> = _downloadsFlow

    public var isInitialized: Boolean = false
        private set

    public var initCount: Int = 0
        private set

    public var lastDeleteFilesFlag: Boolean = false
        private set

    private val sequentialFlags = mutableMapOf<String, Boolean>()
    private val filePriorities = mutableMapOf<String, MutableList<Int>>()
    private val savePaths = mutableMapOf<String, String>()

    override fun initSession() {
        if (!isInitialized) {
            isInitialized = true
            initCount++
        }
    }

    override fun addTorrent(
        magnetUri: String,
        savePath: String,
        selectedFileIndices: List<Int>?,
        topicId: String?,
    ): Result<String> {
        if (!isInitialized) {
            return Result.failure(IllegalStateException("Session not initialized"))
        }
        // Extract a deterministic hash from the magnet URI for testing
        val hash = magnetUri.takeLast(40)
        val download =
            TorrentDownload(
                hash = hash,
                name = "test-$hash",
                savePath = savePath,
                state = TorrentState.DOWNLOADING,
                progress = 0f,
                downloadSpeed = 0,
                uploadSpeed = 0,
                totalSize = 0L,
                downloadedSize = 0L,
                eta = -1L,
                numSeeds = 0,
                numPeers = 0,
                files = emptyList(),
                topicId = topicId,
            )
        _downloadsFlow.value = _downloadsFlow.value + (hash to download)
        savePaths[hash] = savePath
        return Result.success(hash)
    }

    override fun removeTorrent(
        hash: String,
        deleteFiles: Boolean,
    ) {
        lastDeleteFilesFlag = deleteFiles
        _downloadsFlow.value = _downloadsFlow.value - hash
        sequentialFlags.remove(hash)
        filePriorities.remove(hash)
        savePaths.remove(hash)
    }

    override fun pauseTorrent(hash: String) {
        updateState(hash, TorrentState.PAUSED)
    }

    override fun resumeTorrent(hash: String) {
        updateState(hash, TorrentState.DOWNLOADING)
    }

    override fun pauseAll() {
        _downloadsFlow.value.keys.forEach { updateState(it, TorrentState.PAUSED) }
    }

    override fun resumeAll() {
        _downloadsFlow.value.keys.forEach { updateState(it, TorrentState.DOWNLOADING) }
    }

    override fun moveTorrentStorage(
        hash: String,
        newPath: String,
    ) {
        savePaths[hash] = newPath
    }

    override fun setSequentialDownload(
        hash: String,
        enabled: Boolean,
    ) {
        sequentialFlags[hash] = enabled
    }

    override fun prioritizeFile(
        hash: String,
        fileIndex: Int,
        priority: Int,
    ) {
        val priorities = filePriorities.getOrPut(hash) { mutableListOf() }
        while (priorities.size <= fileIndex) {
            priorities.add(4)
        }
        priorities[fileIndex] = priority
    }

    override fun setFilePriorities(
        hash: String,
        priorities: List<Int>,
    ) {
        filePriorities[hash] = priorities.toMutableList()
    }

    override fun isFileReadyForStreaming(
        hash: String,
        fileIndex: Int,
    ): Boolean {
        // Fake: always false unless explicitly set (test can override via subclass)
        return false
    }

    override fun getDownloadedBytes(
        hash: String,
        fileIndex: Int,
    ): Long {
        // Fake: always zero (test can override via subclass)
        return 0L
    }

    override fun getDownload(hash: String): TorrentDownload? = _downloadsFlow.value[hash]

    override fun stopSession() {
        isInitialized = false
        _downloadsFlow.value = emptyMap()
        sequentialFlags.clear()
        filePriorities.clear()
        savePaths.clear()
    }

    // --- Test helpers ---

    public fun isSequentialEnabled(hash: String): Boolean = sequentialFlags[hash] == true

    public fun getFilePriority(
        hash: String,
        fileIndex: Int,
    ): Int = filePriorities[hash]?.getOrNull(fileIndex) ?: 4

    public fun getAllFilePriorities(hash: String): List<Int> = filePriorities[hash]?.toList() ?: emptyList()

    public fun getSavePath(hash: String): String? = savePaths[hash]

    private fun updateState(
        hash: String,
        state: TorrentState,
    ) {
        val current = _downloadsFlow.value[hash] ?: return
        val updated = current.copy(state = state)
        _downloadsFlow.value = _downloadsFlow.value + (hash to updated)
    }
}
