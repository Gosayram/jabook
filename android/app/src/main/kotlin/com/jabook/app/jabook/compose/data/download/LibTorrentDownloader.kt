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

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of TorrentDownloader using libtorrent4j.
 *
 * **Streaming Support:**
 * - Sequential download range for orderly piece loading
 * - TOP_PRIORITY for first 5% of pieces (instant playback start)
 * - Piece deadlines to ensure critical pieces load ASAP
 * - Allows audio playback to begin immediately during download
 */
@Singleton
class LibTorrentDownloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TorrentDownloader {
        companion object {
            private const val TAG = "LibTorrentDownloader"

            // Streaming configuration
            private const val PRIORITY_PIECES_PERCENT = 0.05f // First 5% = TOP_PRIORITY
            private const val PIECE_DEADLINE_MS = 1000 // 1 second deadline for priority pieces
        }

        private val sessionManager: SessionManager by lazy {
            SessionManager().apply {
                start()
            }
        }

        // Track active torrent handles for proper management
        private val activeTorrents = mutableMapOf<String, TorrentHandle>()

        override suspend fun download(
            torrentUrl: String,
            savePath: String,
            onProgress: (Float) -> Unit,
        ): String =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting STREAMING torrent download: $torrentUrl")

                    val saveDir = File(savePath)
                    if (!saveDir.exists()) {
                        saveDir.mkdirs()
                    }

                    // 1. Parse magnet link manually to get info hash
                    val ec = error_code()
                    val p = libtorrent.parse_magnet_uri(torrentUrl, ec)

                    if (ec.value() != 0) {
                        throw IllegalArgumentException("Invalid magnet link: ${ec.message()}")
                    }

                    // Use explicit getter and step-by-step extraction to avoid inference issues with SWIG
                    val infoHashes = p.getInfo_hashes()
                    val bestHash = infoHashes.get_best()
                    val infoHash = Sha1Hash(bestHash)

                    // 2. Add torrent using SessionManager
                    // SessionManager.download returns void, so we add and then find
                    val flags = torrent_flags_t() // default flags
                    sessionManager.download(torrentUrl, saveDir, flags)

                    // 3. Find the handle (wait briefly if needed)
                    var torrentHandle: TorrentHandle? = null
                    var attempts = 0
                    while (torrentHandle == null && attempts < 10) {
                        torrentHandle = sessionManager.find(infoHash)
                        if (torrentHandle == null) {
                            delay(100)
                            attempts++
                        }
                    }

                    if (torrentHandle == null || !torrentHandle.isValid) {
                        throw IllegalStateException("Failed to get valid TorrentHandle after adding magnet link")
                    }

                    activeTorrents[torrentUrl] = torrentHandle

                    configureStreaming(torrentHandle)
                    torrentHandle.resume()

                    while (!torrentHandle.status().isFinished) {
                        delay(1000)
                        val status = torrentHandle.status()
                        onProgress(status.progress())
                    }

                    val torrentInfo = torrentHandle.torrentFile()
                    val fileName = torrentInfo?.name() ?: "download"
                    val filePath = File(saveDir, fileName).absolutePath

                    activeTorrents.remove(torrentUrl)
                    filePath
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed", e)
                    activeTorrents.remove(torrentUrl)
                    throw e
                }
            }

        override suspend fun pause(downloadId: String) {
            withContext(Dispatchers.IO) {
                activeTorrents[downloadId]?.pause()
            }
        }

        override suspend fun resume(downloadId: String) {
            withContext(Dispatchers.IO) {
                activeTorrents[downloadId]?.resume()
            }
        }

        override suspend fun cancel(downloadId: String) {
            withContext(Dispatchers.IO) {
                activeTorrents[downloadId]?.let { handle ->
                    sessionManager.remove(handle)
                    activeTorrents.remove(downloadId)
                }
            }
        }

        /**
         * Configure torrent for streaming (instant playback).
         */
        private fun configureStreaming(torrentHandle: TorrentHandle) {
            try {
                val torrentInfo = torrentHandle.torrentFile() ?: return

                val numPieces = torrentInfo.numPieces()
                Log.d(TAG, "Configuring streaming for $numPieces pieces")

                // Sequential download
                torrentHandle.setSequentialRange(0, numPieces - 1)

                // Priority pieces for instant start
                val priorityPieceCount = (numPieces * PRIORITY_PIECES_PERCENT).toInt().coerceAtLeast(1)

                for (i in 0 until priorityPieceCount) {
                    torrentHandle.piecePriority(i, Priority.TOP_PRIORITY)
                    if (i < 20) {
                        torrentHandle.setPieceDeadline(i, PIECE_DEADLINE_MS)
                    }
                }

                Log.d(TAG, "Streaming ready - can play immediately!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure streaming", e)
            }
        }
    }
