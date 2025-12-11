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

package com.jabook.app.jabook.compose.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from Rutracker search API.
 *
 * Note: Rutracker returns HTML, so this is a simplified model.
 * Real implementation would need HTML parsing.
 */
@Serializable
data class TopicSearchResponse(
    @SerialName("topics")
    val topics: List<TopicItem> = emptyList(),
)

/**
 * Search result topic item.
 */
@Serializable
data class TopicItem(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("author")
    val author: String? = null,
    @SerialName("size")
    val sizeBytes: Long? = null,
    @SerialName("seeders")
    val seeders: Int = 0,
    @SerialName("leechers")
    val leechers: Int = 0,
    @SerialName("torrent_link")
    val torrentLink: String? = null,
)
