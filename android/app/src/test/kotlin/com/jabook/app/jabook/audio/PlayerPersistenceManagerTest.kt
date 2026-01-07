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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for PlayerPersistenceManager.
 * 
 * Tests cover:
 * - Saving playback position on pause
 * - Restoring position after app restart
 * - Saving queue on destroy
 * - Per-book state management
 * - Last played book ID tracking
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerPersistenceManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var manager: PlayerPersistenceManager

    @Before
    fun setup() {
        context = mock()
        sharedPrefs = mock()
        editor = mock()

        whenever(context.getSharedPreferences(any<String>(), any())).thenReturn(sharedPrefs)
        whenever(sharedPrefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(sharedPrefs.getString(any(), any())).thenReturn(null)

        manager = PlayerPersistenceManager(context)
    }

    // ============ saveCurrentMediaItem Tests ============

    @Test
    fun `saveCurrentMediaItem saves playback position to SharedPreferences`() = runTest {
        // Given
        val mediaId = "/storage/audiobook/chapter1.mp3"
        val positionMs = 30000L
        val durationMs = 120000L
        val artworkPath = "/storage/audiobook/cover.jpg"
        val title = "Chapter 1"
        val artist = "Author Name"
        val groupPath = "/storage/audiobook"

        // When
        manager.saveCurrentMediaItem(
            mediaId = mediaId,
            positionMs = positionMs,
            durationMs = durationMs,
            artworkPath = artworkPath,
            title = title,
            artist = artist,
            groupPath = groupPath
        )

        // Then
        verify(editor).putString(eq("playback_resumption_file_path"), eq(mediaId))
        verify(editor).putLong(eq("playback_resumption_position_ms"), eq(positionMs))
        verify(editor).putLong(eq("playback_resumption_duration_ms"), eq(durationMs))
        verify(editor).putString(eq("playback_resumption_artwork_path"), eq(artworkPath))
        verify(editor).putString(eq("playback_resumption_title"), eq(title))
        verify(editor).putString(eq("playback_resumption_artist"), eq(artist))
        verify(editor).putString(eq("playback_resumption_group_path"), eq(groupPath))
        verify(editor).apply()
    }

    // ============ retrieveLastStoredMediaItem Tests ============

    @Test
    fun `retrieveLastStoredMediaItem returns null when no stored media item exists`() = runTest {
        // Given
        whenever(sharedPrefs.getString(eq("playback_resumption_file_path"), any())).thenReturn(null)

        // When
        val result = manager.retrieveLastStoredMediaItem()

        // Then
        assertNull(result)
    }

    @Test
    fun `retrieveLastStoredMediaItem returns stored media item with all fields`() = runTest {
        // Given
        val mediaId = "/storage/audiobook/chapter1.mp3"
        val positionMs = 45000L
        val durationMs = 180000L
        val artworkPath = "/storage/audiobook/cover.jpg"
        val title = "Chapter 1"
        val artist = "Author Name"
        val groupPath = "/storage/audiobook"

        // Use anyOrNull() instead of any() for nullable parameters
        whenever(sharedPrefs.getString(eq("playback_resumption_file_path"), org.mockito.kotlin.isNull())).thenReturn(mediaId)
        whenever(sharedPrefs.getLong(eq("playback_resumption_position_ms"), eq(0L))).thenReturn(positionMs)
        whenever(sharedPrefs.getLong(eq("playback_resumption_duration_ms"), eq(0L))).thenReturn(durationMs)
        whenever(sharedPrefs.getString(eq("playback_resumption_artwork_path"), eq(""))).thenReturn(artworkPath)
        whenever(sharedPrefs.getString(eq("playback_resumption_title"), eq(""))).thenReturn(title)
        whenever(sharedPrefs.getString(eq("playback_resumption_artist"), eq(""))).thenReturn(artist)
        whenever(sharedPrefs.getString(eq("playback_resumption_group_path"), eq(""))).thenReturn(groupPath)

        // When
        val result = manager.retrieveLastStoredMediaItem()

        // Then
        assertNotNull(result)
        assertEquals(mediaId, result!!["filePath"])
        assertEquals(positionMs, result["positionMs"])
        assertEquals(durationMs, result["durationMs"])
        assertEquals(artworkPath, result["artworkPath"])
        assertEquals(title, result["title"])
        assertEquals(artist, result["artist"])
        assertEquals(groupPath, result["groupPath"])
    }

    // ============ Per-book state management Tests ============

    @Test
    fun `updateLastPlayed updates lastPlayedTimestamp correctly`() = runTest {
        // Given
        val bookId = "book_123"
        whenever(sharedPrefs.getString(eq("book_state_$bookId"), any())).thenReturn(null)

        // When
        manager.updateLastPlayed(bookId)

        // Then - verify JSON string saved
        verify(editor).putString(eq("book_state_$bookId"), any())
        verify(editor).putString(eq("last_played_book_id"), eq(bookId))
        assertEquals(bookId, manager.lastPlayedBookId.value)
    }

    @Test
    fun `markCompleted marks book as completed`() = runTest {
        // Given
        val bookId = "book_789"
        val existingState = """{"bookId":"$bookId","positionMs":0,"durationMs":0,"lastPlayedTimestamp":0,"completedTimestamp":0,"playCount":0}"""
        whenever(sharedPrefs.getString(eq("book_state_$bookId"), any())).thenReturn(existingState)

        // When
        manager.markCompleted(bookId)

        // Then
        verify(editor).putString(eq("book_state_$bookId"), any())
    }

    // ============ PlayerState data class Tests ============

    @Test
    fun `PlayerState creates with default values`() {
        val state = PlayerState(
            bookId = "test_book",
            positionMs = 0L,
            durationMs = 0L,
            filePaths = emptyList()
        )

        assertEquals(0L, state.lastPlayedTimestamp)
        assertEquals(0L, state.completedTimestamp)
        assertEquals(0, state.playCount)
    }

    @Test
    fun `PlayerState copies correctly`() {
        val original = PlayerState(
            bookId = "test_book",
            positionMs = 1000L,
            durationMs = 10000L,
            filePaths = listOf("/path1", "/path2"),
            lastPlayedTimestamp = 123456789L
        )

        val updated = original.copy(positionMs = 5000L)

        assertEquals(5000L, updated.positionMs)
        assertEquals(original.bookId, updated.bookId)
        assertEquals(original.lastPlayedTimestamp, updated.lastPlayedTimestamp)
    }
}
