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

package com.jabook.app.jabook.compose.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class UserPreferencesRepositoryTest {
    private fun kotlinx.coroutines.test.TestScope.createRepository(): Pair<DataStoreUserPreferencesRepository, java.io.File> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val file = Files.createTempFile("jabook-prefs-test", ".preferences_pb").toFile()
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
        return DataStoreUserPreferencesRepository(dataStore) to file
    }

    @Test
    fun `theme preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setTheme(com.jabook.app.jabook.compose.data.model.AppTheme.DARK)
            val data = repo.userData.first()
            assertEquals(com.jabook.app.jabook.compose.data.model.AppTheme.DARK, data.theme)
            file.delete()
        }

    @Test
    fun `sort order preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setSortOrder(com.jabook.app.jabook.compose.data.model.BookSortOrder.TITLE_ASC)
            val data = repo.userData.first()
            assertEquals(com.jabook.app.jabook.compose.data.model.BookSortOrder.TITLE_ASC, data.sortOrder)
            file.delete()
        }

    @Test
    fun `auto play next preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            val initial = repo.userData.first()
            assertTrue(initial.autoPlayNext)

            repo.setAutoPlayNext(false)
            val updated = repo.userData.first()
            assertFalse(updated.autoPlayNext)
            file.delete()
        }

    @Test
    fun `playback speed preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setPlaybackSpeed(1.75f)
            val data = repo.userData.first()
            assertEquals(1.75f, data.playbackSpeed, 0.001f)
            file.delete()
        }

    @Test
    fun `font preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setFont(com.jabook.app.jabook.compose.data.model.AppFont.ROBOTO)
            val data = repo.userData.first()
            assertEquals(com.jabook.app.jabook.compose.data.model.AppFont.ROBOTO, data.font)
            file.delete()
        }

    @Test
    fun `onboarding completed persists`() =
        runTest {
            val (repo, file) = createRepository()
            val initial = repo.userData.first()
            assertFalse(initial.onboardingCompleted)

            repo.setOnboardingCompleted(true)
            val updated = repo.userData.first()
            assertTrue(updated.onboardingCompleted)
            file.delete()
        }

    @Test
    fun `haptics enabled persists`() =
        runTest {
            val (repo, file) = createRepository()
            val initial = repo.userData.first()
            assertTrue(initial.hapticsEnabled)

            repo.setHapticsEnabled(false)
            val updated = repo.userData.first()
            assertFalse(updated.hapticsEnabled)
            file.delete()
        }

    @Test
    fun `language preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setLanguage("ru")
            val data = repo.userData.first()
            assertEquals("ru", data.languageCode)
            file.delete()
        }

    @Test
    fun `view mode preference persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setViewMode(com.jabook.app.jabook.compose.data.model.LibraryViewMode.GRID_COMPACT)
            val data = repo.userData.first()
            assertEquals(com.jabook.app.jabook.compose.data.model.LibraryViewMode.GRID_COMPACT, data.viewMode)
            file.delete()
        }

    @Test
    fun `normalize chapter titles persists`() =
        runTest {
            val (repo, file) = createRepository()
            repo.setNormalizeChapterTitles(true)
            val data = repo.userData.first()
            assertTrue(data.normalizeChapterTitles)
            file.delete()
        }
}
