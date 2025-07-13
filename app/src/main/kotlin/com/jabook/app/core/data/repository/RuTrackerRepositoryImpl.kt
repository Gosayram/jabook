package com.jabook.app.core.data.repository

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.domain.model.RuTrackerStats
import com.jabook.app.core.domain.repository.RuTrackerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerRepositoryImpl @Inject constructor() : RuTrackerRepository {

    override fun searchAudiobooks(query: String, categoryId: String?, page: Int): Flow<RuTrackerSearchResult> {
        return flowOf(RuTrackerSearchResult(query = query, totalResults = 0, currentPage = page, totalPages = 1, results = emptyList()))
    }

    override fun getAudiobookDetails(audiobookId: String): Flow<RuTrackerAudiobook> {
        return flowOf(
            RuTrackerAudiobook(
                id = audiobookId,
                title = "Mock Audiobook",
                author = "Mock Author",
                description = "Mock description",
                category = "Audiobooks",
                categoryId = "audiobooks",
                year = 2023,
                quality = "128 kbps",
                duration = "5 hours",
                size = "100 MB",
                sizeBytes = 104857600L,
                magnetUri = "magnet:?xt=urn:btih:mock",
                torrentUrl = "https://mock.torrent",
                seeders = 10,
                leechers = 2,
                completed = 100,
                addedDate = "2023-01-01",
                rating = 4.5f,
            )
        )
    }

    override fun getCategories(): Flow<List<RuTrackerCategory>> {
        return flowOf(emptyList())
    }

    override fun getAudiobooks(categoryId: String, page: Int, sortBy: String): Flow<List<RuTrackerAudiobook>> {
        return flowOf(emptyList())
    }

    override fun getStats(): Flow<RuTrackerStats> {
        return flowOf(
            RuTrackerStats(totalAudiobooks = 1000, totalCategories = 50, activeUsers = 1000, totalSize = "10 TB", lastUpdate = "2023-01-01")
        )
    }

    override fun checkAvailability(): Flow<Boolean> {
        return flowOf(true)
    }

    override fun getTrendingAudiobooks(limit: Int): Flow<List<RuTrackerAudiobook>> {
        return flowOf(emptyList())
    }

    override fun getRecentlyAdded(limit: Int): Flow<List<RuTrackerAudiobook>> {
        return flowOf(emptyList())
    }

    override fun getPopularByCategory(categoryId: String, limit: Int): Flow<List<RuTrackerAudiobook>> {
        return flowOf(emptyList())
    }
}
