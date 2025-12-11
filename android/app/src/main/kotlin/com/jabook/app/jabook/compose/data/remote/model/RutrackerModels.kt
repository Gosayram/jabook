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

package com.jabook.app.jabook.compose.data.remote.model

/**
 * Search result item from Rutracker.
 *
 * Represents a single audiobook found in search results.
 */
data class SearchResult(
    /** Unique topic ID */
    val topicId: String,
    /** Book title */
    val title: String,
    /** Author/narrator */
    val author: String,
    /** Category/genre */
    val category: String,
    /** File size (formatted, e.g., "1.5 GB") */
    val size: String,
    /** Number of seeders */
    val seeders: Int,
    /** Number of leechers */
    val leechers: Int,
    /** Magnet link for direct download */
    val magnetUrl: String?,
    /** Torrent download URL */
    val torrentUrl: String,
)

/**
 * Detailed information about a topic/audiobook.
 */
data class TopicDetails(
    /** Topic ID */
    val topicId: String,
    /** Full title */
    val title: String,
    /** Author or narrator info */
    val author: String?,
    /** Book performer/reader */
    val performer: String?,
    /** Category */
    val category: String,
    /** File size */
    val size: String,
    /** Seeders count */
    val seeders: Int,
    /** Leechers count */
    val leechers: Int,
    /** Magnet link */
    val magnetUrl: String?,
    /** Torrent file URL */
    val torrentUrl: String,
    /** Cover image URL */
    val coverUrl: String?,
    /** List of genres */
    val genres: List<String>,
    /** Date added */
    val addedDate: String?,
    /** Duration (e.g., "08:05:13") */
    val duration: String?,
    /** Bitrate (e.g., "128 kbps") */
    val bitrate: String?,
    /** Audio codec (e.g., "MP3") */
    val audioCodec: String?,
    /** Description/content */
    val description: String?,
    /** Related audiobooks from same series */
    val relatedBooks: List<RelatedBook>,
)

/**
 * Related audiobook from the same series/cycle.
 */
data class RelatedBook(
    /** Topic ID */
    val topicId: String,
    /** Book title */
    val title: String,
)

/**
 * Torrent metadata.
 */
data class TorrentInfo(
    /** Info hash */
    val infoHash: String,
    /** Torrent name */
    val name: String,
    /** Total size in bytes */
    val totalSize: Long,
    /** List of files in torrent */
    val files: List<TorrentFile>,
)

/**
 * File within a torrent.
 */
data class TorrentFile(
    /** File path within torrent */
    val path: String,
    /** File size in bytes */
    val size: Long,
)
