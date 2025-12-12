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

package com.jabook.app.jabook.compose.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for UserPreferences Proto DataStore.
 *
 * Handles serialization and deserialization of UserPreferences protobuf messages.
 */
object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences =
        UserPreferences
            .newBuilder()
            .setThemeMode(ThemeMode.SYSTEM)
            .setUseDynamicColors(true)
            .setPlaybackSpeed(1.0f)
            .setRewindDurationSeconds(15)
            .setForwardDurationSeconds(30)
            .setVolumeBoostLevel("Off")
            .setDrcLevel("Off")
            .setSpeechEnhancer(false)
            .setAutoVolumeLeveling(false)
            .setNormalizeVolume(true)
            .setLanguageCode("en")
            .setNotificationsEnabled(true)
            .setDownloadNotifications(true)
            .setPlayerNotifications(true)
            .build()

    override suspend fun readFrom(input: InputStream): UserPreferences =
        try {
            UserPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }

    override suspend fun writeTo(
        t: UserPreferences,
        output: OutputStream,
    ) {
        t.writeTo(output)
    }
}
