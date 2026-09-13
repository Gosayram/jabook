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

import com.jabook.app.jabook.compose.data.local.dao.FavoriteDao
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.domain.model.FavoriteItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesRepositoryTest {
    private lateinit var fakeDao: FakeFavoriteDao
    private lateinit var repository: FavoritesRepository

    private val testFavorite =
        FavoriteItem(
            topicId = "topic-1",
            title = "Test Book",
            author = "Author",
            category = "Audiobooks",
            size = "500 MB",
            seeders = 10,
            leechers = 2,
            magnetUrl = "magnet:?xt=urn:test",
            coverUrl = "https://example.com/cover.jpg",
            addedDate = "2026-01-01",
            addedToFavorites = "2026-01-02",
        )

    @Before
    fun setUp() {
        fakeDao = FakeFavoriteDao()
        repository = FavoritesRepository(fakeDao)
    }

    @Test
    fun `addToFavorites inserts and returns success`() =
        runTest {
            val result = repository.addToFavorites(testFavorite)

            assertTrue(result.isSuccess)
            assertEquals(1, fakeDao.inserted.size)
            assertEquals("topic-1", fakeDao.inserted[0].topicId)
        }

    @Test
    fun `removeFromFavorites deletes by topicId`() =
        runTest {
            val result = repository.removeFromFavorites("topic-1")

            assertTrue(result.isSuccess)
            assertEquals("topic-1", fakeDao.deletedTopicIds.single())
        }

    @Test
    fun `removeMultipleFavorites deletes all provided topicIds`() =
        runTest {
            val result = repository.removeMultipleFavorites(listOf("t1", "t2", "t3"))

            assertTrue(result.isSuccess)
            assertEquals(listOf("t1", "t2", "t3"), fakeDao.deletedBatch)
        }

    @Test
    fun `clearAllFavorites clears everything`() =
        runTest {
            val result = repository.clearAllFavorites()

            assertTrue(result.isSuccess)
            assertTrue(fakeDao.cleared)
        }

    @Test
    fun `isFavorite returns true when DAO says true`() =
        runTest {
            fakeDao.favorites["topic-1"] = true

            assertTrue(repository.isFavorite("topic-1"))
        }

    @Test
    fun `isFavorite returns false when DAO says false`() =
        runTest {
            assertFalse(repository.isFavorite("topic-999"))
        }

    @Test
    fun `getFavoritesCount returns DAO count`() =
        runTest {
            fakeDao.count = 5

            assertEquals(5, repository.getFavoritesCount())
        }

    @Test
    fun `getFavoriteById returns mapped item when found`() =
        runTest {
            fakeDao.entityMap["topic-1"] =
                FavoriteEntity(
                    topicId = "topic-1",
                    title = "Book",
                    author = "Author",
                    category = "Fiction",
                    size = "100 MB",
                    magnetUrl = "magnet:?xt=urn:test",
                    addedDate = "2026-01-01",
                    addedToFavorites = "2026-01-02",
                )

            val result = repository.getFavoriteById("topic-1")

            assertEquals("topic-1", result?.topicId)
            assertEquals("Book", result?.title)
        }

    @Test
    fun `getFavoriteById returns null when not found`() =
        runTest {
            assertNull(repository.getFavoriteById("nonexistent"))
        }

    @Test
    fun `addToFavorites returns failure when DAO throws`() =
        runTest {
            fakeDao.throwOnInsert = true

            val result = repository.addToFavorites(testFavorite)

            assertTrue(result.isFailure)
        }

    @Test
    fun `removeFromFavorites returns failure when DAO throws`() =
        runTest {
            fakeDao.throwOnDelete = true

            val result = repository.removeFromFavorites("topic-1")

            assertTrue(result.isFailure)
        }

    @Test
    fun `allFavorites maps entities to domain models`() =
        runTest {
            val entity =
                FavoriteEntity(
                    topicId = "t1",
                    title = "Book One",
                    author = "A",
                    category = "Cat",
                    size = "10 MB",
                    magnetUrl = "magnet:?xt=urn:1",
                    addedDate = "2026-01-01",
                    addedToFavorites = "2026-01-02",
                )

            val daoWithFavorites = FakeFavoriteDao()
            daoWithFavorites.allFavoritesFlow = flowOf(listOf(entity))
            val repo = FavoritesRepository(daoWithFavorites)

            val favorites = repo.allFavorites.first()

            assertEquals(1, favorites.size)
            assertEquals("Book One", favorites[0].title)
        }

    @Test
    fun `favoriteIds returns topic IDs from DAO`() =
        runTest {
            val daoWithIds = FakeFavoriteDao()
            daoWithIds.allFavoriteIdsFlow = flowOf(listOf("t1", "t2"))
            val repo = FavoritesRepository(daoWithIds)

            val ids = repo.favoriteIds.first()

            assertEquals(listOf("t1", "t2"), ids)
        }

    private class FakeFavoriteDao : FavoriteDao {
        var inserted: MutableList<FavoriteEntity> = mutableListOf()
        var deletedTopicIds: MutableList<String> = mutableListOf()
        var deletedBatch: List<String> = emptyList()
        var cleared: Boolean = false
        var count: Int = 0
        var favorites: MutableMap<String, Boolean> = mutableMapOf()
        var entityMap: MutableMap<String, FavoriteEntity> = mutableMapOf()
        var throwOnInsert: Boolean = false
        var throwOnDelete: Boolean = false
        var allFavoritesFlow: kotlinx.coroutines.flow.Flow<List<FavoriteEntity>> = flowOf(emptyList())
        var allFavoriteIdsFlow: kotlinx.coroutines.flow.Flow<List<String>> = flowOf(emptyList())

        override fun getAllFavoritesInternal(): kotlinx.coroutines.flow.Flow<List<FavoriteEntity>> = allFavoritesFlow

        override fun getAllFavorites(): kotlinx.coroutines.flow.Flow<List<FavoriteEntity>> = allFavoritesFlow

        override fun getAllFavoriteIdsInternal(): kotlinx.coroutines.flow.Flow<List<String>> = allFavoriteIdsFlow

        override fun getAllFavoriteIds(): kotlinx.coroutines.flow.Flow<List<String>> = allFavoriteIdsFlow

        override suspend fun getFavoriteById(topicId: String): FavoriteEntity? = entityMap[topicId]

        override suspend fun insertFavorite(favorite: FavoriteEntity) {
            if (throwOnInsert) throw RuntimeException("DB error")
            inserted.add(favorite)
        }

        override suspend fun deleteFavorite(topicId: String) {
            if (throwOnDelete) throw RuntimeException("DB error")
            deletedTopicIds.add(topicId)
        }

        override suspend fun deleteFavorites(topicIds: List<String>) {
            if (throwOnDelete) throw RuntimeException("DB error")
            deletedBatch = topicIds
        }

        override suspend fun clearAllFavorites() {
            cleared = true
        }

        override suspend fun getFavoritesCount(): Int = count

        override suspend fun isFavorite(topicId: String): Boolean = favorites[topicId] ?: false
    }
}
