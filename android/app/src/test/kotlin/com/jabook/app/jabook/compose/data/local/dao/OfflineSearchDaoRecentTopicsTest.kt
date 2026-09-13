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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineSearchDaoRecentTopicsTest {
    private lateinit var database: JabookDatabase
    private lateinit var dao: OfflineSearchDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    JabookDatabase::class.java,
                ).build()
        dao = database.offlineSearchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getRecentTopics orders by index date desc regardless of topic id`() =
        runBlocking {
            // Insert oldest-first on purpose so a topicId-ordered query would fail this.
            dao.upsertTopicsInternal(
                listOf(
                    topic(id = "101", timestamp = 1_000L),
                    topic(id = "303", timestamp = 3_000L),
                    topic(id = "202", timestamp = 2_000L),
                ),
            )

            assertEquals(
                listOf("303", "202", "101"),
                dao.getRecentTopics(limit = 20, offset = 0).map { it.topicId },
            )
        }

    @Test
    fun `getRecentTopics respects limit and offset for shelf paging`() =
        runBlocking {
            dao.upsertTopicsInternal(
                listOf(
                    topic(id = "a", timestamp = 1_000L),
                    topic(id = "b", timestamp = 2_000L),
                    topic(id = "c", timestamp = 3_000L),
                ),
            )

            assertEquals(listOf("c", "b"), dao.getRecentTopics(limit = 2, offset = 0).map { it.topicId })
            assertEquals(listOf("a"), dao.getRecentTopics(limit = 2, offset = 2).map { it.topicId })
        }

    @Test
    fun `getRecentTopics orders by topic_date when present and timestamp when null`() =
        runBlocking {
            // Insert order is deliberately shuffled vs the expected result.
            // "legacy" has NO topic_date (pre-v38 row) → falls back to its
            // timestamp; the others are ordered by their own topic_date, even
            // though "old" was indexed last (newest timestamp).
            dao.upsertTopicsInternal(
                listOf(
                    topic(id = "old", timestamp = 9_000L, topicDate = 1_000L),
                    topic(id = "new", timestamp = 2_000L, topicDate = 8_000L),
                    topic(id = "legacy", timestamp = 5_000L, topicDate = null),
                    topic(id = "mid", timestamp = 3_000L, topicDate = 4_000L),
                ),
            )

            // topic_date DESC first: new (8k) > mid (4k) > old (1k);
            // legacy (null) falls back to timestamp 5k, landing between them.
            assertEquals(
                listOf("new", "legacy", "mid", "old"),
                dao.getRecentTopics(limit = 20, offset = 0).map { it.topicId },
            )
        }

    private fun topic(
        id: String,
        timestamp: Long,
        topicDate: Long? = null,
    ): CachedTopicEntity =
        CachedTopicEntity(
            topicId = id,
            title = "title $id",
            author = "author",
            category = "Аудиокниги",
            size = "1 MB",
            seeders = 1,
            leechers = 0,
            timestamp = timestamp,
            topicDate = topicDate,
        )
}
