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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.ForumsDao
import com.jabook.app.jabook.compose.data.local.entity.ForumEntity
import com.jabook.app.jabook.compose.data.remote.model.AudiobookCategory
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cache of RuTracker forum display names, sourced from the index.php
 * category tree ([RutrackerRepository.getCategories]). Lets the UI show real
 * names ("Радиоспектакли") instead of "Forum 574".
 *
 * A failed refresh keeps the last good table — names are static enough that
 * stale data is strictly better than no data.
 */
@Singleton
public class ForumCatalog
    @Inject
    constructor(
        private val forumsDao: ForumsDao,
        private val rutrackerRepository: RutrackerRepository,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("ForumCatalog")

        /**
         * Fetch the category tree and atomically rewrite the cache.
         * Errors are logged and returned as [Result.failure]; the previous
         * table contents survive.
         */
        public suspend fun refresh(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val categories = rutrackerRepository.getCategories().getOrThrow()
                    val rows = flatten(categories)
                    if (rows.isEmpty()) {
                        // A login-wall or block page can parse as "success" with
                        // zero categories — treat as failure so callers see it in
                        // logs and the table keeps its last good contents.
                        throw IllegalStateException("Category fetch returned 0 categories (login wall?)")
                    }
                    forumsDao.replaceAll(rows)
                    logger.i { "Forum catalog refreshed: ${categories.size} categories, ${rows.size} forums" }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    logger.e({ "Forum catalog refresh failed — keeping last good data" }, e)
                }
            }

        public fun observeAll(): Flow<List<ForumEntity>> = forumsDao.getAll()

        public suspend fun namesById(): Map<String, String> = forumsDao.getAllList().associate { it.forumId to it.name }

        /** Categories + subcategories in site order; subcategories carry their parent's name. */
        internal fun flatten(categories: List<AudiobookCategory>): List<ForumEntity> {
            val rows = mutableListOf<ForumEntity>()
            var order = 0
            categories.forEach { category ->
                rows +=
                    ForumEntity(
                        forumId = category.id,
                        name = category.name,
                        categoryName = "",
                        sortOrder = order++,
                    )
                category.subcategories.forEach { sub ->
                    rows +=
                        ForumEntity(
                            forumId = sub.id,
                            name = sub.name,
                            categoryName = category.name,
                            sortOrder = order++,
                        )
                }
            }
            return rows
        }
    }
