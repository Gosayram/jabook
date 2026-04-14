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

import androidx.datastore.core.DataMigration

/**
 * Explicit versioned migration for Proto DataStore schema changes.
 *
 * DataStore ignores unknown proto fields by design. To avoid silent preference loss and to
 * guarantee deterministic defaults for newly introduced fields, we track and migrate via
 * `schema_version`.
 */
public class UserPreferencesDataMigration : DataMigration<UserPreferences> {
    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 3
    }

    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean = currentData.schemaVersion < CURRENT_SCHEMA_VERSION

    override suspend fun migrate(currentData: UserPreferences): UserPreferences {
        var migrated = currentData

        // v1: initialize deterministic defaults for newer fields that may be absent in old files.
        if (migrated.schemaVersion < 1) {
            val builder = migrated.toBuilder()
            if (builder.equalizerPreset.isBlank()) {
                builder.equalizerPreset = "FLAT"
            }
            if (builder.resumeRewindSeconds <= 0) {
                builder.resumeRewindSeconds = 10
            }
            if (builder.skipSilenceThresholdDb == 0f) {
                builder.skipSilenceThresholdDb = -32.0f
            }
            if (builder.skipSilenceMinMs <= 0) {
                builder.skipSilenceMinMs = 250
            }
            builder.schemaVersion = 1
            migrated = builder.build()
        }

        // v2: keep previous runtime behavior and allow cellular cover preloads by default.
        if (migrated.schemaVersion < 2) {
            val builder = migrated.toBuilder()
            builder.autoLoadCoversOnCellular = true
            builder.schemaVersion = 2
            migrated = builder.build()
        }

        // v3: initialize player snapshot defaults for process-death restore fallback.
        if (migrated.schemaVersion < 3) {
            val builder = migrated.toBuilder()
            if (builder.playerSnapshotPlaybackSpeed <= 0f) {
                builder.playerSnapshotPlaybackSpeed = 1.0f
            }
            if (builder.playerSnapshotSleepMode.isBlank()) {
                builder.playerSnapshotSleepMode = "idle"
            }
            builder.schemaVersion = 3
            migrated = builder.build()
        }

        return migrated
    }

    override suspend fun cleanUp() {
        // No-op.
    }
}
