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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for AutoSaveManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AutoSaveManagerTest {
    private lateinit var persistenceManager: PlayerPersistenceManager
    private lateinit var autoSaveManager: AutoSaveManager

    private val testSnapshot =
        PlaybackSnapshot(
            mediaId = "/storage/book/chapter1.mp3",
            positionMs = 30000L,
            durationMs = 120000L,
            artworkPath = "/storage/book/cover.jpg",
            title = "Chapter 1",
            artist = "Author",
            groupPath = "/storage/book",
        )

    @Before
    fun setup() {
        persistenceManager = mock()
        autoSaveManager = AutoSaveManager(persistenceManager)
    }

    // ============ saveNow Tests ============

    @Test
    fun `saveNow saves state immediately when called with force`() =
        runTest {
            // When
            autoSaveManager.saveNow(testSnapshot, force = true)

            // Then
            verify(persistenceManager).saveCurrentMediaItem(
                mediaId = eq(testSnapshot.mediaId),
                positionMs = eq(testSnapshot.positionMs),
                durationMs = eq(testSnapshot.durationMs),
                artworkPath = eq(testSnapshot.artworkPath),
                title = eq(testSnapshot.title),
                artist = eq(testSnapshot.artist),
                groupPath = eq(testSnapshot.groupPath),
            )
        }

    @Test
    fun `saveNow debounces rapid saves`() =
        runTest {
            // Given - two rapid saves
            autoSaveManager.saveNow(testSnapshot, force = false)
            autoSaveManager.saveNow(testSnapshot, force = false) // Should be debounced

            // Then - only one save should occur
            verify(persistenceManager, times(1)).saveCurrentMediaItem(
                mediaId = any(),
                positionMs = any(),
                durationMs = any(),
                artworkPath = any(),
                title = any(),
                artist = any(),
                groupPath = any(),
            )
        }

    @Test
    fun `saveNow with force ignores debounce`() =
        runTest {
            // Given
            autoSaveManager.saveNow(testSnapshot, force = false)
            autoSaveManager.saveNow(testSnapshot, force = true) // Force overrides debounce

            // Then - both saves should occur
            verify(persistenceManager, times(2)).saveCurrentMediaItem(
                mediaId = any(),
                positionMs = any(),
                durationMs = any(),
                artworkPath = any(),
                title = any(),
                artist = any(),
                groupPath = any(),
            )
        }

    // ============ Auto-save lifecycle Tests ============

    @Test
    fun `startAutoSave marks manager as running`() {
        // When
        autoSaveManager.startAutoSave { testSnapshot }

        // Then
        assertTrue(autoSaveManager.isRunning())

        // Cleanup
        autoSaveManager.stopAutoSave()
    }

    @Test
    fun `stopAutoSave marks manager as not running`() {
        // Given
        autoSaveManager.startAutoSave { testSnapshot }

        // When
        autoSaveManager.stopAutoSave()

        // Then
        assertFalse(autoSaveManager.isRunning())
    }

    @Test
    fun `startAutoSave cancels previous job`() {
        // Given
        autoSaveManager.startAutoSave { testSnapshot }
        val firstRunning = autoSaveManager.isRunning()

        // When - start again
        autoSaveManager.startAutoSave { testSnapshot }

        // Then - should still be running (new job)
        assertTrue(firstRunning)
        assertTrue(autoSaveManager.isRunning())

        // Cleanup
        autoSaveManager.stopAutoSave()
    }

    // ============ PlaybackSnapshot Tests ============

    @Test
    fun `PlaybackSnapshot creates with all fields`() {
        val snapshot =
            PlaybackSnapshot(
                mediaId = "test_id",
                positionMs = 1000L,
                durationMs = 10000L,
                artworkPath = "/path/to/art.jpg",
                title = "Test Title",
                artist = "Test Artist",
                groupPath = "/path/to/group",
            )

        assertEquals("test_id", snapshot.mediaId)
        assertEquals(1000L, snapshot.positionMs)
        assertEquals(10000L, snapshot.durationMs)
        assertEquals("/path/to/art.jpg", snapshot.artworkPath)
        assertEquals("Test Title", snapshot.title)
        assertEquals("Test Artist", snapshot.artist)
        assertEquals("/path/to/group", snapshot.groupPath)
    }

    @Test
    fun `PlaybackSnapshot equality works correctly`() {
        val snapshot1 =
            PlaybackSnapshot(
                mediaId = "same_id",
                positionMs = 1000L,
                durationMs = 10000L,
                artworkPath = "",
                title = "",
                artist = "",
                groupPath = "",
            )

        val snapshot2 = snapshot1.copy()

        assertEquals(snapshot1, snapshot2)
    }
}
