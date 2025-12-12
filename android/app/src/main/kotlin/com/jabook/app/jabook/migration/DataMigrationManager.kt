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

package com.jabook.app.jabook.migration

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for data migration from Flutter to Kotlin.
 * TODO: Implement full migration logic.
 */
@Singleton
class DataMigrationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val jabookDatabase: JabookDatabase,
        private val userPreferencesDataStore: DataStore<UserPreferences>,
    ) {
        companion object {
            private const val TAG = "DataMigrationManager"
        }

        suspend fun needsMigration(): Boolean =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "needsMigration (stub): false")
                false
            }

        suspend fun migrateFromFlutter(): MigrationResult =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "migrateFromFlutter (stub)")
                MigrationResult.Success(0, 0)
            }
    }

/**
 * Result of migration operation.
 */
sealed class MigrationResult {
    data class Success(
        val booksCount: Int,
        val chaptersCount: Int,
    ) : MigrationResult()

    data class Failure(
        val error: Exception,
    ) : MigrationResult()
}
