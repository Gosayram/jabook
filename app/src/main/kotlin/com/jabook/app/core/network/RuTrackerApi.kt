package com.jabook.app.core.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * RuTracker API interface for audiobook discovery
 * Based on IDEA.md architecture specification
 */
interface RuTrackerApi {
    
    @GET("forum/tracker.php")
    suspend fun searchAudiobooks(
        @Query("nm") query: String,
        @Query("f") categories: String? = null,
        @Query("start") offset: Int = 0
    ): Response<String>
    
    @GET("forum/viewtopic.php")
    suspend fun getAudiobookDetails(
        @Query("t") topicId: String
    ): Response<String>
    
    @GET("forum/index.php")
    suspend fun getCategories(
        @Query("c") categoryId: String = "33" // Audiobooks category
    ): Response<String>
}

/**
 * Data classes for RuTracker audiobook information
 */
data class AudiobookInfo(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String? = null,
    val description: String,
    val category: String,
    val size: Long,
    val duration: String? = null,
    val quality: String,
    val torrentUrl: String,
    val coverUrl: String? = null,
    val seeders: Int,
    val leechers: Int,
    val magnetLink: String? = null
)

data class Category(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val subcategories: List<Category> = emptyList()
)

/**
 * Parser interface for HTML content from RuTracker
 */
interface RuTrackerParser {
    suspend fun parseSearchResults(html: String): List<AudiobookInfo>
    suspend fun parseAudiobookDetails(html: String): AudiobookInfo
    suspend fun parseCategories(html: String): List<Category>
    suspend fun extractMagnetLink(html: String): String?
} 