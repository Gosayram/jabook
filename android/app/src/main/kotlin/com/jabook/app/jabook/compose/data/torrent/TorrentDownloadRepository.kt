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
        private val resumeDao: TorrentResumeDao,
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
         * Get all downloads (synchronous)
         */
        public suspend fun getAll(): List<TorrentDownload> = dao.getAll().map { it.toDomain() }

        /**
         * Get download by hash
         */
        public suspend fun getByHash(hash: String): TorrentDownload? = dao.getByHash(hash)?.toDomain()

        /**
         * Sync live download snapshots to the database.
         *
         * Inserts rows for new torrents and updates the synced columns of existing ones
         * without touching resumeData, so libtorrent resume BLOBs survive progress writes.
         */
        public suspend fun saveAll(downloads: List<TorrentDownload>) {
            try {
                dao.upsertSyncFields(downloads.map { TorrentDownloadEntity.fromDomain(it) })
            } catch (e: Exception) {
                logger.e({ "Failed to save torrents" }, e)
            }
        }

        /**
         * Delete download
         */
        public suspend fun delete(hash: String) {
            try {
                // Remove the resume row together with the torrent row (single transaction).
                resumeDao.deleteTorrent(dao, hash)
                logger.d { "Deleted torrent: $hash" }
            } catch (e: Exception) {
                logger.e({ "Failed to delete torrent: $hash" }, e)
            }
        }
    }
