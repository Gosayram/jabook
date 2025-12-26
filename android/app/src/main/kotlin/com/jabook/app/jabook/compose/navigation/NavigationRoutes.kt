// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for Jabook Compose app.
 *
 * Uses kotlinx.serialization for type-safe argument passing.
 * Based on Navigation Compose 2.8.0+ type-safe APIs.
 */

/**
 * Player screen route - plays the audiobook.
 *
 * @param bookId Unique identifier of the book to play
 */
@Serializable
data class PlayerRoute(
    val bookId: String,
)

/**
 * WebView screen route - shows web content.
 *
 * @param url URL to load in the WebView
 */
@Serializable
data class WebViewRoute(
    val url: String,
)

/**
 * Settings screen route - shows app settings and preferences.
 */
@Serializable
object SettingsRoute

/**
 * Search screen route - search for books by title or author.
 */
@Serializable
object SearchRoute

/**
 * Downloads screen route - shows active downloads.
 */
@Serializable
data class DownloadsRoute(
    val magnetLink: String? = null,
)

/**
 * Torrent Details screen route - shows files and details.
 *
 * @param hash Info hash of the torrent
 */
@Serializable
data class TorrentDetailsRoute(
    val hash: String,
)

/**
 * Debug screen route - shows debug tools and logs.
 */
@Serializable
object DebugRoute

/**
 * Library screen route - displays user's books.
 */
@Serializable
object LibraryRoute

/**
 * Favorites screen route - displays favorite books.
 */
@Serializable
object FavoritesRoute

/**
 * Topic screen route - displays books for a specific topic/category.
 *
 * @param topicId The unique identifier of the topic
 */
@Serializable
data class TopicRoute(
    val topicId: String,
)

/**
 * Download History screen route - displays download history.
 */
@Serializable
object DownloadHistoryRoute

/**
 * RuTracker Search screen route - search audiobooks on RuTracker.
 */
@Serializable
object RutrackerSearchRoute

@Serializable
object ScanSettingsRoute

/**
 * Migration screen route - shows data migration progress.
 */
@Serializable
object MigrationRoute
