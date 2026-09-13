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

package com.jabook.app.jabook.compose.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class LegacyPreferencesDataMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Seeds a real legacy "jabook_preferences" store, releases its DataStore registration,
     * and returns the backing file so [LegacyPreferencesDataMigration] can open it.
     */
    private suspend fun TestScope.seedLegacyStore(): java.io.File {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val file = context.preferencesDataStoreFile(LegacyPreferencesDataMigration.LEGACY_STORE_NAME)
        Files.createDirectories(file.parentFile.toPath())
        file.delete()
        val store =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
        store.edit { prefs ->
            prefs[stringPreferencesKey("theme")] = "DARK"
            prefs[stringPreferencesKey("language_code")] = "en"
            prefs[stringPreferencesKey("sort_order")] = "TITLE_ASC"
            prefs[floatPreferencesKey("playback_speed")] = 1.75f
            prefs[booleanPreferencesKey("onboarding_completed")] = true
            prefs[booleanPreferencesKey("auto_play_next")] = false
        }
        // Release the active-store registration so the migration may open the same file.
        scope.cancel()
        testScheduler.advanceUntilIdle()
        return file
    }

    @Test
    fun `legacy values are copied into proto and legacy file is removed`() =
        runTest {
            val legacyFile = seedLegacyStore()
            assertTrue(legacyFile.exists())

            val migration = LegacyPreferencesDataMigration(context)
            val migrated = migration.migrate(UserPreferences.getDefaultInstance())

            // Copied values.
            assertEquals(ThemeMode.DARK, migrated.themeMode)
            assertEquals("en", migrated.languageCode)
            assertEquals("TITLE_ASC", migrated.librarySortOrder)
            assertEquals(1.75f, migrated.playbackSpeed)
            assertTrue(migrated.onboardingCompleted)
            // Explicit `false` survives; absent key gets the legacy `true` default.
            assertFalse(migrated.autoPlayNext)
            assertTrue(migrated.pitchCorrectionEnabled)

            migration.cleanUp()
            assertFalse(legacyFile.exists())
        }

    @Test
    fun `missing legacy file means no migration`() =
        runTest {
            context.preferencesDataStoreFile(LegacyPreferencesDataMigration.LEGACY_STORE_NAME).delete()
            val migration = LegacyPreferencesDataMigration(context)
            val current = UserPreferencesSerializer.defaultValue

            assertFalse(migration.shouldMigrate(current))
            assertEquals(current, migration.migrate(current))
        }

    @Test
    fun `shouldMigrate is true only while legacy file exists`() =
        runTest {
            val legacyFile = seedLegacyStore()

            val migration = LegacyPreferencesDataMigration(context)

            assertTrue(migration.shouldMigrate(UserPreferencesSerializer.defaultValue))
            migration.migrate(UserPreferences.getDefaultInstance())
            migration.cleanUp()
            assertFalse(legacyFile.exists())
            assertFalse(migration.shouldMigrate(UserPreferencesSerializer.defaultValue))
        }
}
