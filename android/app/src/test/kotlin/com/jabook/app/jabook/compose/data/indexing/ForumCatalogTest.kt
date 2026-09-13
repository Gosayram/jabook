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

package com.jabook.app.jabook.compose.data.indexing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.remote.model.AudiobookCategory
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForumCatalogTest {
    private lateinit var database: JabookDatabase
    private lateinit var repository: RutrackerRepository
    private lateinit var catalog: ForumCatalog
    private val loggerFactory: LoggerFactory = mock()

    @Before
    fun setUp() {
        whenever(loggerFactory.get(any<String>())).thenReturn(
            com.jabook.app.jabook.compose.core.logger.NoOpLogger,
        )
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    JabookDatabase::class.java,
                ).build()
        repository = mock()
        catalog = ForumCatalog(database.forumsDao(), repository, loggerFactory)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `refresh flattens categories and subcategories into ordered rows`() =
        runBlocking {
            whenever(repository.getCategories()).thenReturn(
                Result.success(
                    listOf(
                        AudiobookCategory(
                            id = "2324",
                            name = "Художественная литература",
                            url = "",
                            subcategories =
                                listOf(
                                    AudiobookCategory(id = "574", name = "Радиоспектакли", url = ""),
                                    AudiobookCategory(id = "1036", name = "Аудиосериалы", url = ""),
                                ),
                        ),
                        AudiobookCategory(id = "2326", name = "Нехудожественная литература", url = ""),
                    ),
                ),
            )

            val result = catalog.refresh()

            assertEquals(Result.success(Unit), result)
            val rows = catalog.observeAll().first()
            assertEquals(
                listOf("2324", "574", "1036", "2326"),
                rows.map { it.forumId },
            )
            assertEquals("", rows[0].categoryName)
            assertEquals("Художественная литература", rows[1].categoryName)
            assertEquals(
                listOf(0, 1, 2, 3),
                rows.map { it.sortOrder },
            )
            assertEquals("Радиоспектакли", catalog.namesById()["574"])
        }

    @Test
    fun `failed refresh keeps last good data`() =
        runBlocking {
            whenever(repository.getCategories()).thenReturn(
                Result.success(listOf(AudiobookCategory(id = "574", name = "Радиоспектакли", url = ""))),
            )
            catalog.refresh()

            whenever(repository.getCategories()).thenReturn(Result.failure(IllegalStateException("network down")))
            val second = catalog.refresh()

            assertTrue(second.isFailure)
            val rows = catalog.observeAll().first()
            assertEquals(listOf("574"), rows.map { it.forumId })
        }

    @Test
    fun `empty category parse is treated as failure and keeps last good data`() =
        runBlocking {
            whenever(repository.getCategories()).thenReturn(
                Result.success(listOf(AudiobookCategory(id = "574", name = "Радиоспектакли", url = ""))),
            )
            catalog.refresh()

            // Login-wall/block page parsing as "success" with zero categories
            // must not wipe the table.
            whenever(repository.getCategories()).thenReturn(Result.success(emptyList()))
            val result = catalog.refresh()

            assertTrue(result.isFailure)
            val rows = catalog.observeAll().first()
            assertEquals(listOf("574"), rows.map { it.forumId })
        }

    @Test
    fun `flatten preserves site order with parent name on subcategories`() {
        val rows =
            catalog.flatten(
                listOf(
                    AudiobookCategory(
                        id = "1",
                        name = "Parent",
                        url = "",
                        subcategories = listOf(AudiobookCategory(id = "2", name = "Child", url = "")),
                    ),
                ),
            )

        assertEquals(listOf("1", "2"), rows.map { it.forumId })
        assertEquals(listOf("", "Parent"), rows.map { it.categoryName })
    }
}
