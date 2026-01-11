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

package com.jabook.app.jabook.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class PlayerPersistenceManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        public companion object {
            private const val PREFS_NAME = "FlutterSharedPreferences"
            private const val KEY_RESUMPTION_FILE_PATH = "playback_resumption_file_path"
            private const val KEY_RESUMPTION_POSITION_MS = "playback_resumption_position_ms"
            private const val KEY_RESUMPTION_DURATION_MS = "playback_resumption_duration_ms"
            private const val KEY_RESUMPTION_ARTWORK_PATH = "playback_resumption_artwork_path"
            private const val KEY_RESUMPTION_TITLE = "playback_resumption_title"
            private const val KEY_RESUMPTION_ARTIST = "playback_resumption_artist"
            private const val KEY_RESUMPTION_GROUP_PATH = "playback_resumption_group_path"
            private const val KEY_LAST_PLAYED_BOOK_ID = "last_played_book_id" // NEW for mini player
        }

        // NEW: StateFlow for last played book ID (for mini player)
        private val _lastPlayedBookId = MutableStateFlow<String?>(null)
        public val lastPlayedBookId: StateFlow<String?> = _lastPlayedBookId.asStateFlow()

        init {
            // Load last played book ID on init
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _lastPlayedBookId.value = prefs.getString(KEY_LAST_PLAYED_BOOK_ID, null)
        }

        public data class PersistedPlayerState(
            public val groupPath: String,
            public val filePaths: List<String>,
            public val currentIndex: Int,
            public val currentPosition: Long,
            public val metadata: Map<String, String>?,
        )

        suspend fun saveCurrentMediaItem(
            mediaId: String,
            positionMs: Long,
            durationMs: Long,
            artworkPath: String,
            title: String,
            artist: String,
            groupPath: String,
        ) = withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs
                    .edit()
                    .putString(KEY_RESUMPTION_FILE_PATH, mediaId)
                    .putLong(KEY_RESUMPTION_POSITION_MS, positionMs)
                    .putLong(KEY_RESUMPTION_DURATION_MS, durationMs)
                    .putString(KEY_RESUMPTION_ARTWORK_PATH, artworkPath)
                    .putString(KEY_RESUMPTION_TITLE, title)
                    .putString(KEY_RESUMPTION_ARTIST, artist)
                    .putString(KEY_RESUMPTION_GROUP_PATH, groupPath)
                    .apply()
                android.util.Log.v(
                    "PlayerPersistence",
                    "Stored current media item for resumption: $mediaId, position=${positionMs}ms",
                )
            } catch (e: Exception) {
                android.util.Log.w("PlayerPersistence", "Failed to store current media item for resumption", e)
            }
        }

        suspend fun retrieveLastStoredMediaItem(): Map<String, Any?>? =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val filePath = prefs.getString(KEY_RESUMPTION_FILE_PATH, null) ?: return@withContext null
                    val positionMs = prefs.getLong(KEY_RESUMPTION_POSITION_MS, 0L)
                    val durationMs = prefs.getLong(KEY_RESUMPTION_DURATION_MS, 0L)
                    val artworkPath = prefs.getString(KEY_RESUMPTION_ARTWORK_PATH, "")
                    val title = prefs.getString(KEY_RESUMPTION_TITLE, "")
                    val artist = prefs.getString(KEY_RESUMPTION_ARTIST, "")
                    val groupPath = prefs.getString(KEY_RESUMPTION_GROUP_PATH, "")

                    mapOf(
                        "filePath" to filePath,
                        "positionMs" to positionMs,
                        "durationMs" to durationMs,
                        "artworkPath" to artworkPath,
                        "title" to title,
                        "artist" to artist,
                        "groupPath" to groupPath,
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PlayerPersistence", "Failed to retrieve last stored media item", e)
                    null
                }
            }

        suspend fun retrievePersistedPlayerState(): PersistedPlayerState? =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString = prefs.getString("flutter.player_state", null) ?: return@withContext null

                    val json = JSONObject(jsonString)
                    val groupPath = json.getString("groupPath")
                    val currentIndex = json.optInt("currentIndex", 0)
                    val currentPosition = json.optLong("currentPosition", 0L)

                    val filePathsJson = json.getJSONArray("filePaths")
                    val filePaths = mutableListOf<String>()
                    for (i in 0 until filePathsJson.length()) {
                        filePaths.add(filePathsJson.getString(i))
                    }

                    val metadataJson = json.optJSONObject("metadata")
                    val metadata =
                        if (metadataJson != null) {
                            val map = mutableMapOf<String, String>()
                            val keys = metadataJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                map[key] = metadataJson.getString(key)
                            }
                            map
                        } else {
                            null
                        }

                    PersistedPlayerState(groupPath, filePaths, currentIndex, currentPosition, metadata)
                } catch (e: Exception) {
                    android.util.Log.e("PlayerPersistence", "Failed to parse player_state JSON", e)
                    null
                }
            }

        public fun saveGroupPathToSharedPreferences() {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val sanitizedPath = sanitizeGroupPath(groupPath)
                prefs.edit().putString("current_group_path", sanitizedPath).apply()
                android.util.Log.d("PlayerPersistence", "Saved groupPath to SharedPreferences: $sanitizedPath")
            } catch (e: Exception) {
                android.util.Log.w("PlayerPersistence", "Failed to save groupPath to SharedPreferences", e)
            }
        }

        // NEW: Per-book state for Backup/Restore & Activity Sorting
        suspend fun savePlayerState(state: PlayerState) =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val json =
                        JSONObject().apply {
                            put("bookId", state.bookId)
                            put("positionMs", state.positionMs)
                            put("durationMs", state.durationMs)
                            put("lastPlayedTimestamp", state.lastPlayedTimestamp)
                            put("completedTimestamp", state.completedTimestamp)
                            put("playCount", state.playCount)
                            // We don't necessarily need filePaths in this light state
                        }
                    prefs.edit().putString("book_state_${state.bookId}", json.toString()).apply()
                } catch (e: Exception) {
                    android.util.Log.e("PlayerPersistence", "Failed to save player state for ${state.bookId}", e)
                }
            }

        suspend fun getPlayerState(bookId: String): PlayerState? =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString = prefs.getString("book_state_$bookId", null) ?: return@withContext null
                    val json = JSONObject(jsonString)

                    PlayerState(
                        bookId = json.getString("bookId"),
                        positionMs = json.optLong("positionMs", 0),
                        durationMs = json.optLong("durationMs", 0),
                        filePaths = emptyList(), // Not stored here
                        lastPlayedTimestamp = json.optLong("lastPlayedTimestamp", 0),
                        completedTimestamp = json.optLong("completedTimestamp", 0),
                        playCount = json.optInt("playCount", 0),
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PlayerPersistence", "Failed to get player state for $bookId", e)
                    null
                }
            }

        suspend fun updateLastPlayed(bookId: String) {
            val currentState = getPlayerState(bookId)
            val newState =
                if (currentState != null) {
                    currentState.copy(lastPlayedTimestamp = System.currentTimeMillis())
                } else {
                    PlayerState(
                        bookId = bookId,
                        positionMs = 0,
                        durationMs = 0,
                        filePaths = emptyList(),
                        lastPlayedTimestamp = System.currentTimeMillis(),
                    )
                }
            savePlayerState(newState)

            // NEW: Update last played book ID for mini player
            _lastPlayedBookId.value = bookId
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_PLAYED_BOOK_ID, bookId).apply()
        }

        suspend fun markCompleted(bookId: String) {
            val currentState = getPlayerState(bookId)
            if (currentState != null && currentState.completedTimestamp == 0L) {
                savePlayerState(currentState.copy(completedTimestamp = System.currentTimeMillis()))
            } else if (currentState == null) {
                // edge case: completed but never played? unlikely but possible
                savePlayerState(
                    PlayerState(
                        bookId = bookId,
                        positionMs = 0,
                        durationMs = 0,
                        filePaths = emptyList(),
                        completedTimestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }

        /**
         * Increments the play count for a book.
         * Should be called when a book/chapter is completed.
         */
        suspend fun incrementPlayCount(bookId: String) {
            val currentState = getPlayerState(bookId)
            val newState =
                if (currentState != null) {
                    currentState.copy(playCount = currentState.playCount + 1)
                } else {
                    PlayerState(
                        bookId = bookId,
                        positionMs = 0,
                        durationMs = 0,
                        filePaths = emptyList(),
                        playCount = 1,
                    )
                }
            savePlayerState(newState)
            android.util.Log.d("PlayerPersistence", "Play count for $bookId incremented to ${newState.playCount}")
        }

        /**
         * Gets the play count for a book.
         */
        suspend fun getPlayCount(bookId: String): Int {
            val state = getPlayerState(bookId)
            return state?.playCount ?: 0
        }

        private fun sanitizeGroupPath(path: String): String = path.replace(Regex("[^\\w\\-.]"), "_")
    }

/**
 * Lightweight state for backup and sorting
 */
public data class PlayerState(
    public val bookId: String,
    public val positionMs: Long,
    public val durationMs: Long,
    public val filePaths: List<String>,
    public val lastPlayedTimestamp: Int = L,
    public val completedTimestamp: Int = L,
    public val playCount: Int = 0,
)
