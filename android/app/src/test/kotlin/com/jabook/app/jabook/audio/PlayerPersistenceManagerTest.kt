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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
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
    fun `persisted snapshot restores clip windows for chapters in one M4B`() =
        runTest {
            val items =
                listOf(
                    PlaylistItem("/book.m4b", "chapter-1", 0L, 10_000L),
                    PlaylistItem("/book.m4b", "chapter-2", 10_000L, null),
                )
            manager.savePersistedPlayerState(
                PlayerPersistenceManager.PersistedPlayerState(
                    groupPath = "book-1",
                    filePaths = items.map(PlaylistItem::path),
                    playlistItems = items,
                    currentIndex = 1,
                    currentPosition = 500L,
                    metadata = null,
                ),
            )

            val restored = manager.retrievePersistedPlayerState()

            assertEquals(items, restored?.playlistItems)
            assertEquals(1, restored?.currentIndex)
        }

    private fun prefs() = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
}
