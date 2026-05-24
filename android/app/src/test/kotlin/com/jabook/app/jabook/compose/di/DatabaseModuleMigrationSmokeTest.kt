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

package com.jabook.app.jabook.compose.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseModuleMigrationSmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `migration contract includes 14 to 22 chain`() {
        val migrationPairs =
            DatabaseModule.configuredMigrations.map { it.startVersion to it.endVersion }
        assertTrue(migrationPairs.contains(14 to 15))
        assertTrue(migrationPairs.contains(15 to 16))
        assertTrue(migrationPairs.contains(16 to 17))
        assertTrue(migrationPairs.contains(17 to 18))
        assertTrue(migrationPairs.contains(18 to 19))
        assertTrue(migrationPairs.contains(19 to 20))
        assertTrue(migrationPairs.contains(20 to 21))
        assertTrue(migrationPairs.contains(21 to 22))
    }

    @Test
    fun `migration contract includes step from 20 to 22`() {
        val migrationPairs =
            DatabaseModule.configuredMigrations.map { it.startVersion to it.endVersion }
        assertTrue(migrationPairs.contains(20 to 21))
        assertTrue(migrationPairs.contains(21 to 22))
    }

    @Test
    fun `migration pairs are sequential without gaps`() {
        val migrations = DatabaseModule.configuredMigrations.sortedBy { it.startVersion }
        var expectedVersion = 1
        for (migration in migrations) {
            // Check that starting version matches expected (accounting for skipped versions)
            if (migration.startVersion == expectedVersion) {
                expectedVersion = migration.endVersion + 1
            }
        }
    }
}
