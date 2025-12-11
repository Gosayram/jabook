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
 * Library screen route - shows list of audiobooks.
 * This is typically the start destination of the app.
 */
@Serializable
object LibraryRoute

/**
 * Player screen route - shows audio player with playback controls.
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
