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

package com.jabook.app.jabook.compose.data.torrent

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for torrent downloads with database persistence
 */
@Singleton
class TorrentDownloadRepository
    @Inject
    constructor(
        private val dao: TorrentDownloadDao,
    ) {
        /**
         * Get all downloads as Flow
         */
        fun getAllFlow(): Flow<List<TorrentDownload>> =
            dao.getAllFlow().map { entities ->
                entities.map { it.toDomain() }
            }

        /**
         * Get active downloads
         */
        fun getActiveFlow(): Flow<List<TorrentDownload>> =
            dao.getActiveFlow().map { entities ->
                entities.map { it.toDomain() }
            }

        /**
         * Get all downloads (synchronous)
         */
        suspend fun getAll(): List<TorrentDownload> = dao.getAll().map { it.toDomain() }

        /**
         * Get download by hash
         */
        suspend fun getByHash(hash: String): TorrentDownload? = dao.getByHash(hash)?.toDomain()

        /**
         * Save download
         */
        suspend fun save(download: TorrentDownload) {
            try {
                val entity = TorrentDownloadEntity.fromDomain(download)
                dao.insert(entity)
                Log.d(TAG, "Saved torrent: ${download.hash}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save torrent: ${download.hash}", e)
            }
        }

        /**
         * Save multiple downloads
         */
        suspend fun saveAll(downloads: List<TorrentDownload>) {
            try {
                val entities = downloads.map { TorrentDownloadEntity.fromDomain(it) }
                dao.insertAll(entities)
                Log.d(TAG, "Saved ${downloads.size} torrents")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save torrents", e)
            }
        }

        /**
         * Delete download
         */
        suspend fun delete(hash: String) {
            try {
                dao.deleteByHash(hash)
                Log.d(TAG, "Deleted torrent: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete torrent: $hash", e)
            }
        }

        /**
         * Update download state
         */
        suspend fun updateState(
            hash: String,
            state: TorrentState,
        ) {
            try {
                dao.updateState(hash, state)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update state for: $hash", e)
            }
        }

        /**
         * Update download progress
         */
        suspend fun updateProgress(
            hash: String,
            progress: Float,
            downloadedSize: Long,
        ) {
            try {
                dao.updateProgress(hash, progress, downloadedSize)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update progress for: $hash", e)
            }
        }

        /**
         * Delete all completed downloads
         */
        suspend fun deleteAllCompleted() {
            try {
                dao.deleteAllCompleted()
                Log.d(TAG, "Deleted all completed torrents")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete completed torrents", e)
            }
        }

        companion object {
            private const val TAG = "TorrentDownloadRepository"
        }
    }
