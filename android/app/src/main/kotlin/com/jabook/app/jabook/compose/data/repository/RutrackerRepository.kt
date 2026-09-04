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

import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.toSearchResult
import com.jabook.app.jabook.compose.data.network.ConnectivityAwareRequestScheduler
import com.jabook.app.jabook.compose.data.network.ParserVersionPolicy
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
import com.jabook.app.jabook.compose.data.remote.mapper.toDomainFromIndex
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.AppError
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails
import com.jabook.app.jabook.compose.domain.model.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Rutracker data.
 *
 * Handles network requests and HTML parsing for Rutracker search and details.
 */
public interface RutrackerRepository {
    /**
     * Search for audiobooks on Rutracker.
     *
     * @param query Search query
     * @param query Search query
     * @return Flow of Result with list of search results
     */
    public fun search(query: String): Flow<Result<List<RutrackerSearchResult>, AppError>>

    /**
     * Fetch topic details and save cover URL to database.
     *
     * @param topicId Topic ID
     * @return Result with the resolved cover URL when found, otherwise null
     */
    public suspend fun fetchAndSaveCover(topicId: String): Result<String?, AppError>

    /**
     * Get topic details.
     *
     * @param topicId Topic ID
     * @return Result with topic details
     */
    public suspend fun getTopicDetails(topicId: String): Result<RutrackerTopicDetails, AppError>

    /**
     * Get topic details at a specific page.
     *
     * @param topicId Topic ID
     * @param page Page number (1-indexed)
     * @return Result with topic details for that page
     */
    public suspend fun getTopicDetailsPage(
        topicId: String,
        page: Int,
    ): Result<RutrackerTopicDetails, AppError>

    /**
     * Search for audiobooks using indexed topics with optional network refresh.
     *
     * @param query Search query
     * @param forumIds Optional forum IDs filter
     * @return Flow of search results
     */
    public fun searchAudiobooksFlow(
        query: String,
        forumIds: String? = null,
    ): Flow<Result<List<RutrackerSearchResult>, AppError>>
}

/**
 * Implementation of RutrackerRepository.
 */
@Singleton
public class RutrackerRepositoryImpl
    @Inject
    constructor(
        private val api: RutrackerApi,
        private val parser: RutrackerParser,
        private val offlineSearchDao: OfflineSearchDao,
        private val connectivityScheduler: ConnectivityAwareRequestScheduler,
        loggerFactory: LoggerFactory,
    ) : RutrackerRepository {
        private val logger = loggerFactory.get("RutrackerRepositoryImpl")

        override fun search(query: String): Flow<Result<List<RutrackerSearchResult>, AppError>> =
            flow {
                // Use ONLY indexed search (no network)
                try {
                    val indexSize = offlineSearchDao.getTopicCount()

                    if (indexSize > 0) {
                        // Check for debug command (dev builds only)
                        if (BuildConfig.DEBUG && (query.trim() == "!index" || query.trim() == ":debug")) {
                            val sampleTopics = offlineSearchDao.getSampleTopics(10)
                            val domainResults =
                                sampleTopics
                                    .map {
                                        it.toSearchResult().copy(
                                            title = "[DEBUG] ${it.title}",
                                            author = "[${it.author}] (ID: ${it.topicId}, Ver: ${it.indexVersion})",
                                        )
                                    }.toDomainFromIndex()
                            emit(Result.Success(domainResults))
                            return@flow
                        }

                        // Tokenize query for fuzzy search
                        // ponytail: cap at 8 tokens — adversarial input could build unbounded SQL
                        val tokens =
                            query
                                .trim()
                                .split(Regex("\\s+"))
                                .filter { it.isNotEmpty() }
                                .take(8)

                        if (tokens.isEmpty()) {
                            emit(Result.Success(emptyList()))
                        } else {
                            // Build dynamic SQL query for token-based search
                            // Each token must be present in either title OR author
                            val sqlBuilder = StringBuilder("SELECT * FROM cached_topics WHERE ")
                            val args = ArrayList<Any>()

                            tokens.forEachIndexed { index, token ->
                                if (index > 0) sqlBuilder.append(" AND ")
                                sqlBuilder.append("(title LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\')")

                                // ponytail: escape wildcards so "100%" doesn't match everything
                                val likePattern: String = "%${escapeLike(token)}%"
                                args.add(likePattern)
                                args.add(likePattern)
                            }

                            // Add ordering and limit
                            sqlBuilder.append(" ORDER BY seeders DESC, timestamp DESC LIMIT 200")

                            val simpleQuery =
                                androidx.sqlite.db.SimpleSQLiteQuery(
                                    sqlBuilder.toString(),
                                    args.toArray(),
                                )

                            // Emit Flow from Room
                            emitAll(
                                offlineSearchDao
                                    .searchIndexedTopicsRaw(simpleQuery)
                                    .map { entities ->
                                        val dtoResults = entities.map { it.toSearchResult() }
                                        val domainResults = dtoResults.toDomainFromIndex()
                                        Result.Success(domainResults)
                                    },
                            )
                        }
                    } else {
                        // Index is empty - return empty results
                        emit(Result.Success(emptyList()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Search failed - return error
                    emit(Result.Error(e.toAppError()))
                }
            }

        override suspend fun fetchAndSaveCover(topicId: String): Result<String?, AppError> =
            try {
                val result = getTopicDetails(topicId)

                when (result) {
                    is Result.Success -> {
                        val coverUrl = result.data.coverUrl
                        if (!coverUrl.isNullOrBlank()) {
                            offlineSearchDao.updateCoverUrl(topicId, coverUrl)
                            Result.Success(coverUrl)
                        } else {
                            Result.Success(null)
                        }
                    }
                    is Result.Error -> Result.Error(result.error)
                    is Result.Loading -> Result.Loading()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.toAppError())
            }

        override suspend fun getTopicDetails(topicId: String): Result<RutrackerTopicDetails, AppError> {
            return try {
                if (!connectivityScheduler.awaitOnline("getTopicDetails")) {
                    return Result.Error(AppError.NetworkError.NoConnection)
                }
                val response = api.getTopicDetails(topicId)

                if (!response.isSuccessful) {
                    return Result.Error(
                        AppError.NetworkError.HttpError(
                            code = response.code(),
                            response = response.message(),
                        ),
                    )
                }

                // Get raw bytes and decode as Windows-1251
                val rawBytes =
                    response.body()?.bytes() ?: return Result.Error(
                        AppError.NetworkError.Generic("Empty response body"),
                    )
                val html = String(rawBytes, charset("windows-1251"))

                val dtoDetails =
                    parser.parseTopicDetails(html, topicId)
                        ?: run {
                            val check =
                                ParserVersionPolicy.checkBreakage(
                                    parserName = "topic_details",
                                    parserVersion = ParserVersionPolicy.TOPIC_PARSER_VERSION,
                                    resultCount = 0,
                                    query = topicId,
                                    responseHtmlLength = html.length,
                                )
                            logger.w { ParserVersionPolicy.formatBreakageLog(check) }
                            return Result.Error(
                                AppError.ParsingError.Generic("Failed to parse topic details"),
                            )
                        }

                // Map DTO to domain model
                val domainDetails = dtoDetails.toDomain()
                if (domainDetails.isValid()) {
                    Result.Success(domainDetails)
                } else {
                    Result.Error(
                        AppError.ParsingError.Generic("Topic details failed validation"),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.toAppError())
            }
        }

        override suspend fun getTopicDetailsPage(
            topicId: String,
            page: Int,
        ): Result<RutrackerTopicDetails, AppError> {
            return try {
                if (!connectivityScheduler.awaitOnline("getTopicDetailsPage")) {
                    return Result.Error(AppError.NetworkError.NoConnection)
                }
                // Calculate offset: each page has 30 comments, offset = (page - 1) * 30
                val offset = (page - 1) * 30
                val response = api.getTopicDetailsAtPage(topicId, offset)

                if (!response.isSuccessful) {
                    return Result.Error(
                        AppError.NetworkError.HttpError(
                            code = response.code(),
                            response = response.message(),
                        ),
                    )
                }

                val rawBytes =
                    response.body()?.bytes() ?: return Result.Error(
                        AppError.NetworkError.Generic("Empty response body"),
                    )
                val html = String(rawBytes, charset("windows-1251"))

                val dtoDetails =
                    parser.parseTopicDetails(html, topicId)
                        ?: run {
                            val check =
                                ParserVersionPolicy.checkBreakage(
                                    parserName = "topic_details_page",
                                    parserVersion = ParserVersionPolicy.TOPIC_PARSER_VERSION,
                                    resultCount = 0,
                                    query = "$topicId:$page",
                                    responseHtmlLength = html.length,
                                )
                            logger.w { ParserVersionPolicy.formatBreakageLog(check) }
                            return Result.Error(
                                AppError.ParsingError.Generic("Failed to parse topic details"),
                            )
                        }

                val domainDetails = dtoDetails.toDomain()

                if (domainDetails.isValid() || (page > 1 && domainDetails.isValidForPagination())) {
                    Result.Success(domainDetails)
                } else {
                    Result.Error(
                        AppError.ParsingError.Generic("Topic details failed validation"),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.toAppError())
            }
        }

        override fun searchAudiobooksFlow(
            query: String,
            forumIds: String?,
        ): Flow<Result<List<RutrackerSearchResult>, AppError>> =
            flow {
                try {
                    val indexSize = offlineSearchDao.getTopicCount()

                    if (indexSize > 0) {
                        val sqlBuilder = StringBuilder("SELECT * FROM cached_topics WHERE ")
                        val args = ArrayList<Any>()
                        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

                        if (tokens.isEmpty()) {
                            emit(Result.Success(emptyList()))
                            return@flow
                        }

                        tokens.forEachIndexed { index, token ->
                            if (index > 0) sqlBuilder.append(" AND ")
                            sqlBuilder.append("(title LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\')")
                            val likePattern: String = "%${escapeLike(token)}%"
                            args.add(likePattern)
                            args.add(likePattern)
                        }

                        sqlBuilder.append(" ORDER BY seeders DESC, timestamp DESC LIMIT 200")

                        val simpleQuery =
                            androidx.sqlite.db.SimpleSQLiteQuery(
                                sqlBuilder.toString(),
                                args.toArray(),
                            )

                        emitAll(
                            offlineSearchDao
                                .searchIndexedTopicsRaw(simpleQuery)
                                .map { entities ->
                                    val dtoResults = entities.map { it.toSearchResult() }
                                    val domainResults = dtoResults.toDomainFromIndex()
                                    Result.Success(domainResults)
                                },
                        )
                    } else {
                        emit(Result.Success(emptyList()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(Result.Error(e.toAppError()))
                }
            }

        /** Escapes LIKE wildcards so a token like "100%" doesn't match everything. */
        private fun escapeLike(token: String): String = token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
