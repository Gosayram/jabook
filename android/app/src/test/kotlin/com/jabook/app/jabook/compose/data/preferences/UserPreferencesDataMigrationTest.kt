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

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPreferencesDataMigrationTest {
    private val migration = UserPreferencesDataMigration()

    @Test
    fun `shouldMigrate returns true for legacy schema`() =
        runTest {
            val legacy =
                UserPreferences
                    .newBuilder()
                    .setThemeMode(ThemeMode.DARK)
                    .setPlaybackSpeed(1.25f)
                    .setSchemaVersion(0)
                    .build()

            assertTrue(migration.shouldMigrate(legacy))
        }

    @Test
    fun `migrate applies schema version and stable defaults`() =
        runTest {
            val legacy =
                UserPreferences
                    .newBuilder()
                    .setSchemaVersion(0)
                    .setEqualizerPreset("")
                    .setResumeRewindSeconds(0)
                    .setSkipSilenceThresholdDb(0f)
                    .setSkipSilenceMinMs(0)
                    .build()

            val migrated = migration.migrate(legacy)

            assertEquals(UserPreferencesDataMigration.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
            assertEquals("FLAT", migrated.equalizerPreset)
            assertEquals(10, migrated.resumeRewindSeconds)
            assertEquals(-32.0f, migrated.skipSilenceThresholdDb)
            assertEquals(250, migrated.skipSilenceMinMs)
            assertTrue(migrated.autoLoadCoversOnCellular)
        }

    @Test
    fun `serializer readFrom with explicit migration upgrades legacy payload`() =
        runTest {
            val legacy =
                UserPreferences
                    .newBuilder()
                    .setSchemaVersion(0)
                    .setEqualizerPreset("")
                    .build()
            val payload = legacy.toByteArray().inputStream()

            val parsedRaw = UserPreferencesSerializer.readFrom(payload)
            val parsed = migration.migrate(parsedRaw)

            assertEquals(UserPreferencesDataMigration.CURRENT_SCHEMA_VERSION, parsed.schemaVersion)
            assertEquals("FLAT", parsed.equalizerPreset)
            assertTrue(parsed.autoLoadCoversOnCellular)
        }
}
