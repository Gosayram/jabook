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

package com.jabook.app.jabook.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Unit tests for Play Count functionality in PlayerPersistenceManager.
 */
@RunWith(RobolectricTestRunner::class)
class PlayCountTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var persistenceManager: PlayerPersistenceManager

    @Before
    fun setup() {
        context = mock()
        sharedPreferences = mock()
        editor = mock()

        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)

        persistenceManager = PlayerPersistenceManager(context)
    }

    @Test
    fun `incrementPlayCount creates new state if none exists`() =
        runTest {
            // Given: No existing state
            whenever(sharedPreferences.getString(eq("book_state_book123"), any())).thenReturn(null)

            // When: incrementPlayCount is called
            persistenceManager.incrementPlayCount("book123")

            // Then: State is saved with playCount = 1
            verify(editor).putString(
                eq("book_state_book123"),
                org.mockito.kotlin.argThat { json ->
                    json.contains("\"playCount\":1")
                },
            )
        }

    @Test
    fun `incrementPlayCount increments existing count`() =
        runTest {
            // Given: Existing state with playCount = 5
            val existingJson =
                """{"bookId":"book123","positionMs":0,"durationMs":0,"lastPlayedTimestamp":0,"completedTimestamp":0,"playCount":5}"""
            whenever(sharedPreferences.getString(eq("book_state_book123"), any()))
                .thenReturn(existingJson)

            // When: incrementPlayCount is called
            persistenceManager.incrementPlayCount("book123")

            // Then: State is saved with playCount = 6
            verify(editor).putString(
                eq("book_state_book123"),
                org.mockito.kotlin.argThat { json ->
                    json.contains("\"playCount\":6")
                },
            )
        }

    @Test
    fun `getPlayCount returns 0 for non-existent book`() =
        runTest {
            // Given: No existing state
            whenever(sharedPreferences.getString(eq("book_state_unknown"), any())).thenReturn(null)

            // When: getPlayCount is called
            val count = persistenceManager.getPlayCount("unknown")

            // Then: Returns 0
            assertEquals(0, count)
        }

    @Test
    fun `getPlayCount returns stored count for existing book`() =
        runTest {
            // Given: Existing state with playCount = 42
            val existingJson =
                """{"bookId":"book456","positionMs":0,"durationMs":0,"lastPlayedTimestamp":0,"completedTimestamp":0,"playCount":42}"""
            whenever(sharedPreferences.getString(eq("book_state_book456"), any()))
                .thenReturn(existingJson)

            // When: getPlayCount is called
            val count = persistenceManager.getPlayCount("book456")

            // Then: Returns 42
            assertEquals(42, count)
        }

    @Test
    fun `PlayerState playCount defaults to 0`() {
        // Given: PlayerState with default values
        val state =
            PlayerState(
                bookId = "test",
                positionMs = 0,
                durationMs = 0,
                filePaths = emptyList(),
            )

        // Then: playCount is 0
        assertEquals(0, state.playCount)
    }

    @Test
    fun `PlayerState playCount can be set`() {
        // Given: PlayerState with playCount = 10
        val state =
            PlayerState(
                bookId = "test",
                positionMs = 0,
                durationMs = 0,
                filePaths = emptyList(),
                playCount = 10,
            )

        // Then: playCount is 10
        assertEquals(10, state.playCount)
    }
}
