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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for torrent downloads with database persistence
 */
@Singleton
public class TorrentDownloadRepository
    @Inject
    constructor(
        private val dao: TorrentDownloadDao,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("TorrentDownloadRepository")

        /**
         * Get all downloads as Flow
         */
        public fun getAllFlow(): Flow<List<TorrentDownload>> =
            dao.getAllFlow().map { entities ->
                entities.map { it.toDomain() }
            }

        /**
         * Get active downloads
         */
        public fun getActiveFlow(): Flow<List<TorrentDownload>> =
            dao.getActiveFlow().map { entities ->
                entities.map { it.toDomain() }
            }

        /**
         * Get all downloads (synchronous)
         */
        public suspend fun getAll(): List<TorrentDownload> = dao.getAll().map { it.toDomain() }

        /**
         * Get download by hash
         */
        public suspend fun getByHash(hash: String): TorrentDownload? = dao.getByHash(hash)?.toDomain()

        /**
         * Save download
         */
        public suspend fun save(download: TorrentDownload) {
            try {
                val entity = TorrentDownloadEntity.fromDomain(download)
                dao.insert(entity)
                logger.d { "Saved torrent: ${download.hash}" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to save torrent: ${download.hash}" }
            }
        }

        /**
         * Save multiple downloads
         */
        public suspend fun saveAll(downloads: List<TorrentDownload>) {
            try {
                val entities = downloads.map { TorrentDownloadEntity.fromDomain(it) }
                dao.insertAll(entities)
                logger.d { "Saved ${downloads.size} torrents" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to save torrents" }
            }
        }

        /**
         * Delete download
         */
        public suspend fun delete(hash: String) {
            try {
                dao.deleteByHash(hash)
                logger.d { "Deleted torrent: $hash" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to delete torrent: $hash" }
            }
        }

        /**
         * Update download state
         */
        public suspend fun updateState(
            hash: String,
            state: TorrentState,
        ) {
            try {
                dao.updateState(hash, state)
            } catch (e: Exception) {
                logger.e(e) { "Failed to update state for: $hash" }
            }
        }

        /**
         * Update download progress
         */
        public suspend fun updateProgress(
            hash: String,
            progress: Float,
            downloadedSize: Long,
        ) {
            try {
                dao.updateProgress(hash, progress, downloadedSize)
            } catch (e: Exception) {
                logger.e(e) { "Failed to update progress for: $hash" }
            }
        }

        /**
         * Delete all completed downloads
         */
        public suspend fun deleteAllCompleted() {
            try {
                dao.deleteAllCompleted()
                logger.d { "Deleted all completed torrents" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to delete completed torrents" }
            }
        }
    }
