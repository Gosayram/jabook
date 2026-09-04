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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [TorrentDownload] snapshots from libtorrent handles. Stateless and
 * side-effect free (reads native handles, never mutates session state) — kept
 * separate from [TorrentSessionManager] so session control and state building
 * follow libtorrent4j's own separation of concerns.
 */
@Singleton
public class TorrentStateBuilder
    @Inject
    constructor(
        loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("TorrentStateBuilder")

        public fun buildTorrentDownload(
            hash: String,
            handle: TorrentHandle,
            topicId: String?,
        ): TorrentDownload {
            try {
                val status = handle.status()
                val torrentInfo = handle.torrentFile()

                // Get name with fallback: try status.name(), then torrentInfo.name(), then hash
                val name =
                    try {
                        val statusName = status.name()
                        if (statusName.isNotBlank()) {
                            statusName
                        } else {
                            torrentInfo?.name()?.takeIf { it.isNotBlank() } ?: hash
                        }
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get name for torrent $hash, using hash as fallback" }
                        hash
                    }

                // Get save path with error handling
                val savePath =
                    try {
                        handle.savePath()
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get save path for torrent $hash" }
                        ""
                    }

                // Get progress with bounds checking
                val progress = status.progress().coerceIn(0f, 1f)

                // Get speeds with error handling
                val downloadSpeed =
                    try {
                        status.downloadRate().toLong().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get download speed for torrent $hash" }
                        0L
                    }

                val uploadSpeed =
                    try {
                        status.uploadRate().toLong().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get upload speed for torrent $hash" }
                        0L
                    }

                // Get sizes with error handling
                val totalSize =
                    try {
                        status.totalWanted().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get total size for torrent $hash" }
                        0L
                    }

                val downloadedSize =
                    try {
                        status.totalWantedDone().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get downloaded size for torrent $hash" }
                        0L
                    }

                val uploadedSize =
                    try {
                        status.allTimeUpload().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get uploaded size for torrent $hash" }
                        0L
                    }

                // Get peer counts with error handling
                val numPeers =
                    try {
                        status.numPeers()
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get num peers for torrent $hash" }
                        0
                    }

                val numSeeds =
                    try {
                        status.numSeeds()
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get num seeds for torrent $hash" }
                        0
                    }

                // Calculate ETA with error handling
                val eta =
                    try {
                        calculateEta(status)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to calculate ETA for torrent $hash" }
                        -1L
                    }

                // Get files with error handling
                val files =
                    try {
                        if (torrentInfo != null) {
                            mapFiles(torrentInfo, handle)
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to map files for torrent $hash" }
                        emptyList()
                    }

                // State mapping per libtorrent4j: an ERROR is surfaced via
                // TorrentStatus.errorCode(), and a user/tracker pause is a flag
                // (TorrentFlags.PAUSED), not a TorrentStatus.State.
                val errorCode = runCatching { status.errorCode() }.getOrNull()
                val errorMessage = errorCode?.takeIf { it.isError() }?.getMessage()
                val isError = errorMessage != null
                val isPaused =
                    runCatching {
                        handle
                            .getFlags()
                            .and_(org.libtorrent4j.TorrentFlags.PAUSED)
                            .non_zero()
                    }.getOrDefault(false)
                val resolvedState =
                    when {
                        isError -> TorrentState.ERROR
                        isPaused -> TorrentState.PAUSED
                        else -> mapState(status.state())
                    }

                return TorrentDownload(
                    hash = hash,
                    name = name,
                    state = resolvedState,
                    progress = progress,
                    downloadSpeed = downloadSpeed.toInt(),
                    uploadSpeed = uploadSpeed.toInt(),
                    totalSize = totalSize,
                    downloadedSize = downloadedSize,
                    uploadedSize = uploadedSize,
                    numPeers = numPeers,
                    numSeeds = numSeeds,
                    eta = eta,
                    savePath = savePath,
                    files = files,
                    errorMessage = errorMessage,
                    topicId = topicId,
                )
            } catch (e: Exception) {
                // If anything goes wrong, return a minimal valid TorrentDownload
                logger.e({ "Critical error creating TorrentDownload for $hash" }, e)
                return TorrentDownload(
                    hash = hash,
                    name = hash, // Fallback to hash
                    state = TorrentState.ERROR,
                    progress = 0f,
                    downloadSpeed = 0,
                    uploadSpeed = 0,
                    totalSize = 0,
                    downloadedSize = 0,
                    uploadedSize = 0,
                    numPeers = 0,
                    numSeeds = 0,
                    eta = -1L,
                    savePath = "",
                    files = emptyList(),
                    errorMessage = "Error creating download info: ${e.message}",
                    topicId = topicId,
                )
            }
        }

        public fun mapState(state: TorrentStatus.State): TorrentState =
            when (state) {
                TorrentStatus.State.CHECKING_FILES,
                TorrentStatus.State.CHECKING_RESUME_DATA,
                -> TorrentState.CHECKING
                TorrentStatus.State.DOWNLOADING_METADATA -> TorrentState.DOWNLOADING_METADATA
                TorrentStatus.State.DOWNLOADING -> TorrentState.DOWNLOADING
                TorrentStatus.State.SEEDING -> TorrentState.SEEDING
                TorrentStatus.State.FINISHED -> TorrentState.COMPLETED
                TorrentStatus.State.UNKNOWN -> TorrentState.QUEUED
                else -> TorrentState.QUEUED
            }

        public fun calculateEta(status: TorrentStatus): Long {
            val remaining = status.totalWanted() - status.totalWantedDone()
            val speed = status.downloadRate()

            return if (speed > 0 && remaining > 0) {
                remaining / speed
            } else {
                -1
            }
        }

        public fun mapFiles(
            torrentInfo: TorrentInfo,
            handle: TorrentHandle,
        ): List<TorrentFile> {
            return try {
                val fileStorage = torrentInfo.files() ?: return emptyList()
                val numFiles = fileStorage.numFiles()

                if (numFiles <= 0) {
                    logger.w { "Torrent has no files" }
                    return emptyList()
                }

                // Get priorities with error handling
                val priorities =
                    try {
                        handle.filePriorities() // Returns Priority[]
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get file priorities" }
                        emptyArray()
                    }

                // Get progress with error handling
                val progress =
                    try {
                        // Use empty flags to get progress in bytes, not pieces
                        handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to get file progress" }
                        longArrayOf()
                    }

                (0 until numFiles).mapNotNull { index ->
                    try {
                        val priority =
                            if (index < priorities.size) {
                                try {
                                    priorities[index].swig().toInt().coerceIn(0, 7)
                                } catch (e: Exception) {
                                    logger.w(e) { "Failed to get priority for file $index" }
                                    4 // Default priority
                                }
                            } else {
                                4 // Default priority
                            }

                        val size =
                            try {
                                fileStorage.fileSize(index).coerceAtLeast(0L)
                            } catch (e: Exception) {
                                logger.w(e) { "Failed to get size for file $index" }
                                0L
                            }

                        val downloaded =
                            if (index < progress.size) {
                                try {
                                    progress[index].coerceAtLeast(0L)
                                } catch (e: Exception) {
                                    logger.w(e) { "Failed to get downloaded bytes for file $index" }
                                    0L
                                }
                            } else {
                                0L
                            }

                        val fileProgress = if (size > 0) (downloaded.toFloat() / size).coerceIn(0f, 1f) else 0f

                        val path =
                            try {
                                fileStorage.filePath(index) ?: "file_$index"
                            } catch (e: Exception) {
                                logger.w(e) { "Failed to get path for file $index" }
                                "file_$index"
                            }

                        TorrentFile(
                            index = index,
                            path = path,
                            size = size,
                            priority = priority,
                            progress = fileProgress,
                            isSelected = priority != 0,
                        )
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to map file $index" }
                        null // Skip this file
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Critical error mapping files" }
                emptyList()
            }
        }
    }
