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

package com.jabook.app.jabook.audio.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore for audio player preferences.
 */
private val Context.audioPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "audio_preferences",
)

/**
 * Keys for audio preferences.
 */
object AudioPreferencesKeys {
    val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    val LAST_PLAYED_BOOK_ID = intPreferencesKey("last_played_book_id")
    val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
}

/**
 * Repository for audio preferences using DataStore.
 */
class AudioPreferences(
    private val context: Context,
) {
    private val dataStore = context.audioPreferencesDataStore

    /**
     * Gets the playback speed preference.
     */
    val playbackSpeed: Flow<Float> =
        dataStore.data.map { preferences ->
            preferences[AudioPreferencesKeys.PLAYBACK_SPEED] ?: 1.0f
        }

    /**
     * Sets the playback speed preference.
     */
    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[AudioPreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    /**
     * Gets the last played book ID.
     */
    val lastPlayedBookId: Flow<Int?> =
        dataStore.data.map { preferences ->
            preferences[AudioPreferencesKeys.LAST_PLAYED_BOOK_ID]
        }

    /**
     * Sets the last played book ID.
     */
    suspend fun setLastPlayedBookId(bookId: Int) {
        dataStore.edit { preferences ->
            preferences[AudioPreferencesKeys.LAST_PLAYED_BOOK_ID] = bookId
        }
    }

    /**
     * Gets the auto play next preference.
     */
    val autoPlayNext: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[AudioPreferencesKeys.AUTO_PLAY_NEXT] ?: true
        }

    /**
     * Sets the auto play next preference.
     */
    suspend fun setAutoPlayNext(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AudioPreferencesKeys.AUTO_PLAY_NEXT] = enabled
        }
    }
}
