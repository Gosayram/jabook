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
        }

    @Test
    fun `serializer readFrom migrates legacy payload`() =
        runTest {
            val legacy =
                UserPreferences
                    .newBuilder()
                    .setSchemaVersion(0)
                    .setEqualizerPreset("")
                    .build()
            val payload = legacy.toByteArray().inputStream()

            val parsed = UserPreferencesSerializer.readFrom(payload)

            assertEquals(UserPreferencesDataMigration.CURRENT_SCHEMA_VERSION, parsed.schemaVersion)
            assertEquals("FLAT", parsed.equalizerPreset)
        }
}
