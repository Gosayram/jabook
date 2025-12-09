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

package com.jabook.app.jabook.audio.data.repository

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.local.dao.SavedPlayerStateDao
import com.jabook.app.jabook.audio.data.local.database.entity.SavedPlayerStateEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing saved player states.
 *
 * Provides offline-first access to full player state with support for
 * playlist, position, speed, repeat mode, and sleep timer.
 */
@Singleton
class SavedPlayerStateRepository
    @Inject
    constructor(
        private val stateDao: SavedPlayerStateDao,
    ) {
        /**
         * Gets the saved player state for a group path.
         */
        suspend fun getSavedState(groupPath: String): Result<SavedPlayerStateEntity?> =
            try {
                val entity = stateDao.getState(groupPath)
                Result.Success(entity)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Gets the most recently updated saved player state.
         */
        suspend fun getLatestState(): Result<SavedPlayerStateEntity?> =
            try {
                val entity = stateDao.getLatestState()
                Result.Success(entity)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Saves a player state.
         */
        suspend fun saveState(
            groupPath: String,
            filePaths: List<String>,
            metadata: Map<String, String>? = null,
            currentIndex: Int = 0,
            currentPosition: Long = 0,
            playbackSpeed: Double = 1.0,
            isPlaying: Boolean = false,
            repeatMode: Int = 0,
            sleepTimerRemainingSeconds: Int? = null,
        ): Result<Unit> =
            try {
                val filePathsJson = JSONArray(filePaths).toString()
                val metadataJson =
                    if (metadata != null && metadata.isNotEmpty()) {
                        JSONObject(metadata).toString()
                    } else {
                        null
                    }

                val entity =
                    SavedPlayerStateEntity(
                        groupPath = groupPath,
                        filePaths = filePathsJson,
                        metadata = metadataJson,
                        currentIndex = currentIndex,
                        currentPosition = currentPosition,
                        playbackSpeed = playbackSpeed,
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                        lastUpdated = System.currentTimeMillis(),
                    )
                stateDao.upsertState(entity)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Updates saved state settings (repeat mode and sleep timer).
         */
        suspend fun updateSettings(
            groupPath: String,
            repeatMode: Int? = null,
            sleepTimerRemainingSeconds: Int? = null,
        ): Result<Unit> =
            try {
                val existingState = stateDao.getState(groupPath)
                if (existingState != null) {
                    val updatedEntity =
                        existingState.copy(
                            repeatMode = repeatMode ?: existingState.repeatMode,
                            sleepTimerRemainingSeconds =
                                sleepTimerRemainingSeconds ?: existingState.sleepTimerRemainingSeconds,
                            lastUpdated = System.currentTimeMillis(),
                        )
                    stateDao.upsertState(updatedEntity)
                    Result.Success(Unit)
                } else {
                    Result.Success(Unit) // No state to update
                }
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Parses file paths from JSON string.
         */
        fun parseFilePaths(filePathsJson: String): List<String> =
            try {
                val jsonArray = JSONArray(filePathsJson)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }

        /**
         * Parses metadata from JSON string.
         */
        fun parseMetadata(metadataJson: String?): Map<String, String>? {
            return try {
                if (metadataJson == null || metadataJson.isEmpty()) {
                    return null
                }
                val jsonObject = JSONObject(metadataJson)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.getString(key)
                }
                map
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Deletes a saved player state.
         */
        suspend fun deleteState(groupPath: String): Result<Unit> =
            try {
                stateDao.deleteState(groupPath)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Deletes all saved player states.
         */
        suspend fun deleteAllStates(): Result<Unit> =
            try {
                stateDao.deleteAllStates()
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
