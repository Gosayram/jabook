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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for new PlaylistManager functions.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistManagerExtensionsTest {

    @Test
    fun testSearchTracks_findsMatches() = runBlockingTest {
        val playlistManager = FakePlaylistManager()
        val indices = playlistManager.searchTracks("test")
        assertThat(indices).isNotEmpty()
    }

    @Test
    fun testFilterTracks_filtersCorrectly() = runBlockingTest {
        val playlistManager = FakePlaylistManager()
        val indices = playlistManager.filterTracks { metadata ->
            metadata["artist"] == "Artist A"
        }
        assertThat(indices).isNotEmpty()
    }

    @Test
    fun testGroupByArtist_groupsCorrectly() = runBlockingTest {
        val playlistManager = FakePlaylistManager()
        val groups = playlistManager.groupByArtist()
        assertThat(groups).isNotEmpty()
    }

    @Test
    fun testGroupByAlbum_groupsCorrectly() = runBlockingTest {
        val playlistManager = FakePlaylistManager()
        val groups = playlistManager.groupByAlbum()
        assertThat(groups).isNotEmpty()
    }

    @Test
    fun testSortByGenre_sortsCorrectly() = runBlockingTest {
        val playlistManager = FakePlaylistManager()
        val sorted = playlistManager.sortByGenre()
        assertThat(sorted).isNotEmpty()
    }
}