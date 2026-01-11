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
public data class PlayerRoute(
    val bookId: String,
)

/**
 * WebView screen route - shows web content.
 *
 * @param url URL to load in the WebView
 */
@Serializable
public data class WebViewRoute(
    val url: String,
)

/**
 * Settings screen route - shows app settings and preferences.
 */
@Serializable
public object SettingsRoute

/**
 * Search screen route - search for books by title or author.
 */
@Serializable
public object SearchRoute

/**
 * Downloads screen route - shows active downloads.
 */
@Serializable
public data class DownloadsRoute(
    val magnetLink: String? = null,
)

/**
 * Torrent Details screen route - shows files and details.
 *
 * @param hash Info hash of the torrent
 */
@Serializable
public data class TorrentDetailsRoute(
    val hash: String,
)

/**
 * Debug screen route - shows debug tools and logs.
 */
@Serializable
public object DebugRoute

/**
 * Library screen route - displays user's books.
 */
@Serializable
public object LibraryRoute

/**
 * Favorites screen route - displays favorite books.
 */
@Serializable
public object FavoritesRoute

/**
 * Topic screen route - displays books for a specific topic/category.
 *
 * @param topicId The unique identifier of the topic
 */
@Serializable
public data class TopicRoute(
    val topicId: String,
)

/**
 * Download History screen route - displays download history.
 */
@Serializable
public object DownloadHistoryRoute

/**
 * RuTracker Search screen route - search audiobooks on RuTracker.
 */
@Serializable
public object RutrackerSearchRoute

@Serializable
public object ScanSettingsRoute

/**
 * Audio Settings screen route - configure audio playback settings.
 */
@Serializable
public object AudioSettingsRoute

/**
 * Migration screen route - shows data migration progress.
 */
@Serializable
public object MigrationRoute

/**
 * Onboarding screen route - introduces the app to new users.
 */
@Serializable
public object OnboardingRoute
