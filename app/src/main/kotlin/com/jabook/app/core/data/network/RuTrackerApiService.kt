package com.jabook.app.core.data.network

import com.jabook.app.core.data.network.model.AudiobookSearchResult
import com.jabook.app.core.data.network.model.RuTrackerTorrent
import kotlinx.coroutines.delay

interface RuTrackerApiService {
    suspend fun searchAudiobooks(query: String, page: Int = 1): Result<List<AudiobookSearchResult>>

    suspend fun getTorrentDetails(torrentId: String): Result<RuTrackerTorrent>

    suspend fun getTopAudiobooks(limit: Int = 20): Result<List<AudiobookSearchResult>>

    suspend fun getNewAudiobooks(limit: Int = 20): Result<List<AudiobookSearchResult>>

    suspend fun getAudiobooksByCategory(category: String, limit: Int = 20): Result<List<AudiobookSearchResult>>

    suspend fun getAudiobooksByAuthor(author: String, limit: Int = 20): Result<List<AudiobookSearchResult>>

    suspend fun checkAvailability(): Result<Boolean>
}

class MockRuTrackerApiService : RuTrackerApiService {

    override suspend fun searchAudiobooks(query: String, page: Int): Result<List<AudiobookSearchResult>> {
        delay(1000) // Simulate network delay

        val mockResults =
            (1..10).map { index ->
                AudiobookSearchResult(
                    id = "search_${query.hashCode()}_$index",
                    title = "Mock Search Result #$index for '$query'",
                    author = "Mock Author #$index",
                    narrator = "Mock Narrator #$index",
                    duration = "${(6..15).random()} hours",
                    size = "${(200..800).random()} MB",
                    seeds = (5..100).random(),
                    leeches = (1..25).random(),
                    category = "Audiobooks",
                    subcategory = listOf("Fiction", "Non-Fiction", "Biography", "Science", "History").random(),
                    uploadDate = System.currentTimeMillis() - (1..365).random() * 24 * 60 * 60 * 1000,
                    magnetLink = "magnet:?xt=urn:btih:mock_search_${query.hashCode()}_$index",
                )
            }

        return Result.success(mockResults)
    }

    override suspend fun getTorrentDetails(torrentId: String): Result<RuTrackerTorrent> {
        delay(500) // Simulate network delay

        val mockTorrent =
            RuTrackerTorrent(
                id = torrentId,
                title = "Mock Torrent Details",
                author = "Mock Author",
                narrator = "Mock Narrator",
                description = "Mock torrent description for testing",
                duration = "12 hours 30 minutes",
                size = "500 MB",
                seeds = 25,
                leeches = 5,
                category = "Audiobooks",
                subcategory = "Fiction",
                uploadDate = System.currentTimeMillis() - (24 * 60 * 60 * 1000), // 1 day ago
                magnetLink = "magnet:?xt=urn:btih:mock_hash_$torrentId",
                language = "Russian",
                year = 2023,
                coverImageUrl = null,
            )

        return Result.success(mockTorrent)
    }

    override suspend fun getTopAudiobooks(limit: Int): Result<List<AudiobookSearchResult>> {
        delay(800) // Simulate network delay

        val mockResults =
            (1..limit).map { index ->
                AudiobookSearchResult(
                    id = "top_$index",
                    title = "Top Audiobook #$index",
                    author = "Popular Author #$index",
                    narrator = "Popular Narrator #$index",
                    duration = "${(8..20).random()} hours",
                    size = "${(300..800).random()} MB",
                    seeds = (50..200).random(),
                    leeches = (5..30).random(),
                    category = "Audiobooks",
                    subcategory = if (index % 2 == 0) "Fiction" else "Non-Fiction",
                    uploadDate = System.currentTimeMillis() - (1..30).random() * 24 * 60 * 60 * 1000,
                    magnetLink = "magnet:?xt=urn:btih:mock_top_$index",
                )
            }

        return Result.success(mockResults)
    }

    override suspend fun getNewAudiobooks(limit: Int): Result<List<AudiobookSearchResult>> {
        delay(600) // Simulate network delay

        val mockResults =
            (1..limit).map { index ->
                AudiobookSearchResult(
                    id = "new_$index",
                    title = "New Audiobook #$index",
                    author = "New Author #$index",
                    narrator = "New Narrator #$index",
                    duration = "${(5..12).random()} hours",
                    size = "${(200..600).random()} MB",
                    seeds = (10..50).random(),
                    leeches = (1..15).random(),
                    category = "Audiobooks",
                    subcategory = listOf("Fiction", "Non-Fiction", "Biography", "Science").random(),
                    uploadDate = System.currentTimeMillis() - (1..7).random() * 24 * 60 * 60 * 1000, // Last 7 days
                    magnetLink = "magnet:?xt=urn:btih:mock_new_$index",
                )
            }

        return Result.success(mockResults)
    }

    override suspend fun getAudiobooksByCategory(category: String, limit: Int): Result<List<AudiobookSearchResult>> {
        delay(700) // Simulate network delay

        val mockResults =
            (1..limit).map { index ->
                AudiobookSearchResult(
                    id = "${category.lowercase()}_$index",
                    title = "$category Audiobook #$index",
                    author = "$category Author #$index",
                    narrator = "$category Narrator #$index",
                    duration = "${(7..16).random()} hours",
                    size = "${(250..700).random()} MB",
                    seeds = (15..80).random(),
                    leeches = (2..20).random(),
                    category = "Audiobooks",
                    subcategory = category,
                    uploadDate = System.currentTimeMillis() - (1..60).random() * 24 * 60 * 60 * 1000,
                    magnetLink = "magnet:?xt=urn:btih:mock_${category.lowercase()}_$index",
                )
            }

        return Result.success(mockResults)
    }

    override suspend fun getAudiobooksByAuthor(author: String, limit: Int): Result<List<AudiobookSearchResult>> {
        delay(650) // Simulate network delay

        val mockResults =
            (1..limit).map { index ->
                AudiobookSearchResult(
                    id = "${author.hashCode()}_$index",
                    title = "Book #$index by $author",
                    author = author,
                    narrator = "Narrator for $author #$index",
                    duration = "${(6..14).random()} hours",
                    size = "${(300..600).random()} MB",
                    seeds = (20..60).random(),
                    leeches = (3..18).random(),
                    category = "Audiobooks",
                    subcategory = listOf("Fiction", "Non-Fiction", "Biography").random(),
                    uploadDate = System.currentTimeMillis() - (1..180).random() * 24 * 60 * 60 * 1000,
                    magnetLink = "magnet:?xt=urn:btih:mock_author_${author.hashCode()}_$index",
                )
            }

        return Result.success(mockResults)
    }

    override suspend fun checkAvailability(): Result<Boolean> {
        delay(200) // Simulate network delay
        return Result.success(true)
    }
}
