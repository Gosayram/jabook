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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerPersistenceManagerTest {
    private lateinit var context: Context
    private lateinit var manager: PlayerPersistenceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().apply()
        manager = PlayerPersistenceManager(context)
    }

    @After
    fun tearDown() {
        prefs().edit().clear().apply()
    }

    @Test
    fun `retrievePersistedPlayerState migrates legacy JSON snapshot to versioned keys`() =
        runTest {
            val legacyJson =
                JSONObject()
                    .put("groupPath", "book://legacy")
                    .put("currentIndex", 1)
                    .put("currentPosition", 42_000L)
                    .put("filePaths", JSONArray().put("a.mp3").put("b.mp3"))
                    .put("metadata", JSONObject().put("title", "Legacy Book"))
                    .toString()

            prefs()
                .edit()
                .putString("flutter.player_state", legacyJson)
                .apply()

            val restored = manager.retrievePersistedPlayerState()

            assertNotNull(restored)
            assertEquals("book://legacy", restored?.groupPath)
            assertEquals(listOf("a.mp3", "b.mp3"), restored?.filePaths)
            assertEquals(1, restored?.currentIndex)
            assertEquals(42_000L, restored?.currentPosition)
            assertEquals("Legacy Book", restored?.metadata?.get("title"))

            assertEquals(1, prefs().getInt("playback_snapshot_version", 0))
            assertEquals("book://legacy", prefs().getString("playback_snapshot_group_path", null))
            assertNotNull(prefs().getString("playback_snapshot_file_paths", null))
        }

    @Test
    fun `retrievePersistedPlayerState falls back to legacy when versioned snapshot is corrupted`() =
        runTest {
            prefs()
                .edit()
                .putInt("playback_snapshot_version", 1)
                .putString("playback_snapshot_group_path", "book://broken")
                .putString("playback_snapshot_file_paths", "{not_json")
                .apply()

            val legacyJson =
                JSONObject()
                    .put("groupPath", "book://safe")
                    .put("currentIndex", 0)
                    .put("currentPosition", 123L)
                    .put("filePaths", JSONArray().put("safe.mp3"))
                    .toString()

            prefs()
                .edit()
                .putString("flutter.player_state", legacyJson)
                .apply()

            val restored = manager.retrievePersistedPlayerState()

            assertNotNull(restored)
            assertEquals("book://safe", restored?.groupPath)
            assertEquals(listOf("safe.mp3"), restored?.filePaths)
            assertEquals(1, prefs().getInt("playback_snapshot_version", 0))
            assertTrue(prefs().getInt("playback_snapshot_corruption_count", 0) >= 1)
            assertEquals("book://safe", prefs().getString("playback_snapshot_group_path", null))
        }

    @Test
    fun `retrievePersistedPlayerState clears invalid legacy snapshot`() =
        runTest {
            prefs()
                .edit()
                .putString("flutter.player_state", "{broken_json")
                .apply()

            val restored = manager.retrievePersistedPlayerState()

            assertNull(restored)
            assertTrue(prefs().getInt("playback_snapshot_corruption_count", 0) >= 1)
            assertNull(prefs().getString("flutter.player_state", null))
        }

    private fun prefs() = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
}
