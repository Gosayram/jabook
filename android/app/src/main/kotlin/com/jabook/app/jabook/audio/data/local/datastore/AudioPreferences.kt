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

package com.jabook.app.jabook.audio.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.jabook.app.jabook.core.datastore.DataStoreCorruptionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun createAudioPreferencesDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = DataStoreCorruptionPolicy.preferencesHandler(storeName = "audio_preferences"),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("audio_preferences") },
    )

/**
 * Keys for audio preferences.
 */
public object AudioPreferencesKeys {
    public val PLAYBACK_SPEED: Preferences.Key<Float> = floatPreferencesKey("playback_speed")
    public val LAST_PLAYED_BOOK_ID: Preferences.Key<Int> = intPreferencesKey("last_played_book_id")
    public val AUTO_PLAY_NEXT: Preferences.Key<Boolean> = booleanPreferencesKey("auto_play_next")
}

/**
 * Repository for audio preferences using DataStore.
 */
public class AudioPreferences(
    private val context: Context,
) {
    private val dataStore: DataStore<Preferences> by lazy { createAudioPreferencesDataStore(context) }

    /**
     * Gets the playback speed preference.
     */
    public val playbackSpeed: Flow<Float> =
        dataStore.data.map { preferences ->
            preferences[AudioPreferencesKeys.PLAYBACK_SPEED] ?: 1.0f
        }

    /**
     * Sets the playback speed preference.
     */
    public suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[AudioPreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    /**
     * Gets the last played book ID.
     */
    public val lastPlayedBookId: Flow<Int?> =
        dataStore.data.map { preferences ->
            preferences[AudioPreferencesKeys.LAST_PLAYED_BOOK_ID]
        }

    /**
     * Sets the last played book ID.
     */
    public suspend fun setLastPlayedBookId(bookId: Int) {
        dataStore.edit { preferences ->
            preferences[AudioPreferencesKeys.LAST_PLAYED_BOOK_ID] = bookId
        }
    }

    /**
     * Gets the auto play next preference.
     */
    public val autoPlayNext: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[AudioPreferencesKeys.AUTO_PLAY_NEXT] ?: true
        }

    /**
     * Sets the auto play next preference.
     */
    public suspend fun setAutoPlayNext(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AudioPreferencesKeys.AUTO_PLAY_NEXT] = enabled
        }
    }
}
