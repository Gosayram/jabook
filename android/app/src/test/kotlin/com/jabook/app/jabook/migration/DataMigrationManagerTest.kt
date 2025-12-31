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

package com.jabook.app.jabook.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.scanner.BookIdentifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DataMigrationManagerTest {
    private lateinit var context: Context
    private lateinit var database: JabookDatabase
    private lateinit var booksDao: BooksDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var bookIdentifier: BookIdentifier
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var migrationManager: DataMigrationManager

    @Before
    fun setup() {
        booksDao = mock()
        database =
            mock {
                on { booksDao() } doReturn booksDao
            }

        editor =
            mock {
                on { putBoolean(any(), any()) } doReturn it
            }

        sharedPreferences =
            mock {
                on { edit() } doReturn editor
            }

        context =
            mock {
                on { getSharedPreferences(any(), any()) } doReturn sharedPreferences
            }

        dataStore = mock()

        bookIdentifier =
            mock {
                on { generateBookId(any(), any(), any()) } doReturn "mock_id"
            }

        migrationManager = DataMigrationManager(context, database, dataStore, bookIdentifier)
    }

    @Test
    fun `needsMigration returns false if already migrated`() =
        runTest {
            whenever(sharedPreferences.getBoolean("migration_completed_v1", false)).thenReturn(true)

            val result = migrationManager.needsMigration()

            assertFalse(result)
        }

    @Test
    fun `needsMigration returns false if no legacy state`() =
        runTest {
            whenever(sharedPreferences.getBoolean("migration_completed_v1", false)).thenReturn(false)
            whenever(sharedPreferences.contains("flutter.player_state")).thenReturn(false)

            val result = migrationManager.needsMigration()

            assertFalse(result)
        }

    @Test
    fun `needsMigration returns true if legacy state exists and not migrated`() =
        runTest {
            whenever(sharedPreferences.getBoolean("migration_completed_v1", false)).thenReturn(false)
            whenever(sharedPreferences.contains("flutter.player_state")).thenReturn(true)

            val result = migrationManager.needsMigration()

            assertTrue(result)
        }

    @Test
    fun `migrateFromFlutter parses JSON and inserts book`() =
        runTest {
            // Arrange
            val legacyJson =
                """
                {
                    "groupPath": "/storage/emulated/0/Audiobooks/MyBook",
                    "currentPosition": 15000,
                    "currentIndex": 2,
                    "metadata": {
                        "title": "Legacy Book",
                        "artist": "Legacy Author",
                        "album": "Legacy Album"
                    }
                }
                """.trimIndent()

            whenever(sharedPreferences.getString("flutter.player_state", null)).thenReturn(legacyJson)

            // Mock specific ID generation for verification
            whenever(
                bookIdentifier.generateBookId(
                    eq("/storage/emulated/0/Audiobooks/MyBook"),
                    eq("Legacy Album"),
                    eq("Legacy Author"),
                ),
            ).thenReturn("generated-id")

            // Act
            val result = migrationManager.migrateFromFlutter()

            // Assert
            assertTrue(result is MigrationResult.Success)
            verify(editor).putBoolean("migration_completed_v1", true)
            verify(editor).apply()

            val captor = argumentCaptor<BookEntity>()
            verify(booksDao).insertBook(captor.capture())

            val capturedBook = captor.firstValue
            assertEquals("generated-id", capturedBook.id)
            assertEquals("Legacy Book", capturedBook.title)
            assertEquals("Legacy Author", capturedBook.author)
            assertEquals(15000L, capturedBook.currentPosition)
            assertEquals(2, capturedBook.currentChapterIndex)
            assertEquals("/storage/emulated/0/Audiobooks/MyBook", capturedBook.localPath)
        }
}
